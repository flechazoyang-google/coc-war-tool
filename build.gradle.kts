// 根构建脚本：仅声明插件版本（apply false），模块按需 alias 引用（版本见 gradle/libs.versions.toml）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
