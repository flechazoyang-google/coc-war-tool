package com.cocwar.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 AndroidKeyStore 的 AES/GCM 加密存储（替换已停维护的 security-crypto）。
 *
 * 密文格式：Base64(IV) + ":" + Base64(ciphertext)，IV 随机生成并随密文保存（GCM 标准做法）。
 * 密钥保存在 AndroidKeyStore 中不可导出；应用卸载/清除数据后密钥随之销毁，密文无法解密（视为丢失）。
 *
 * 注意：依赖设备端 AndroidKeyStore，本地 JVM 单元测试不可用；加解密往返需真机/模拟器
 * instrumented 测试验证（见 docs/UPGRADE.md 回归清单）。
 */
object SecurePrefs {

    private const val KEY_ALIAS = "coc_webdav_master_key_v2"
    private const val GCM_TAG_BITS = 128

    /** AndroidKeyStore 是否可用（能创建/访问密钥）。不可用时调用方应拒绝明文落盘并提示用户。 */
    fun isKeystoreAvailable(): Boolean = try {
        getOrCreateKey()
        true
    } catch (e: Exception) {
        false
    }

    /** 加密明文，返回 "iv:data"（均为 Base64.NO_WRAP）；加密失败返回 null（调用方不得落盘明文）。 */
    fun encrypt(plaintext: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val data = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** 解密 "iv:data" 密文；失败（格式错误/密钥丢失/密文被篡改）返回 null。 */
    fun decrypt(ciphertext: String): String? {
        return try {
            val sep = ciphertext.indexOf(':')
            if (sep <= 0) return null
            val iv = Base64.decode(ciphertext.substring(0, sep), Base64.NO_WRAP)
            val data = Base64.decode(ciphertext.substring(sep + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** 获取或创建 AndroidKeyStore 中的 AES 密钥；@Synchronized 防止并发首调重复生成覆盖密钥。 */
    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
