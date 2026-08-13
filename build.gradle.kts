// 根构建脚本：仅声明插件版本（apply false），模块按需 alias 引用（版本见 gradle/libs.versions.toml）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // 静态代码分析：baseline 抑制存量告警，只对新增代码生效（见代码审查规范 §11 阶段一）
    alias(libs.plugins.detekt) apply false
}
