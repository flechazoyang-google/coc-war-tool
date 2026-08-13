package com.cocwar.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SecurePrefs（AndroidKeyStore + AES/GCM）真机加解密往返测试（androidTest，需设备/模拟器）。
 *
 * AndroidKeyStore 依赖设备密钥库，本地 JVM 单元测试无法覆盖，故放 androidTest。
 * 覆盖：密钥可用、加解密往返、同明文不同密文（随机 IV）、非法/损坏密文安全返回 null。
 */
@RunWith(AndroidJUnit4::class)
class SecurePrefsTest {

    @Test
    fun keystoreAvailable() {
        assertTrue("AndroidKeyStore 应可用", SecurePrefs.isKeystoreAvailable())
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val plain = "webdav-密码-123!@#"
        val cipher = SecurePrefs.encrypt(plain)
        assertNotNull("加密不应失败", cipher)
        assertEquals(plain, SecurePrefs.decrypt(cipher!!))
    }

    @Test
    fun samePlaintextYieldsDifferentCiphertext() {
        val c1 = SecurePrefs.encrypt("same")
        val c2 = SecurePrefs.encrypt("same")
        assertNotNull(c1)
        assertNotNull(c2)
        // GCM 随机 IV：同明文两次加密的密文应不同
        assertNotEquals(c1, c2)
    }

    @Test
    fun decryptInvalidInputReturnsNull() {
        assertNull(SecurePrefs.decrypt(""))
        assertNull(SecurePrefs.decrypt("no-colon-separator"))
        assertNull(SecurePrefs.decrypt("bad:base64"))
    }
}
