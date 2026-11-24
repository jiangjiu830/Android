plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.android.notepad"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.android.notepad"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 期中实验不涉及代码混淆，保持可读性便于检查
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // 个别旧版 API 的提示不影响实验演示
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
}
