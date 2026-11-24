// 工程根目录的构建脚本：只声明插件版本，具体配置放在 app/build.gradle.kts 中。
plugins {
    alias(libs.plugins.android.application) apply false
}
