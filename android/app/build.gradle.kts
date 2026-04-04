import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.vtbvita.widget"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vtbvita.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Mock API base URL.
        //
        // По умолчанию используется production-сервер (https://vtb.vibefounder.ru).
        // Для локальной разработки создай файл android/local.properties и добавь строку:
        //
        //   Эмулятор (AVD):
        //     MOCK_API_BASE_URL=http://10.0.2.2:8000
        //
        //   Реальное устройство по USB:
        //     1. Запусти сервер: cd ml/mock_api && uvicorn main:app --host 127.0.0.1 --port 8000
        //     2. Пробрось порт:  adb reverse tcp:8000 tcp:8000
        //     3. MOCK_API_BASE_URL=http://localhost:8000
        //
        //   Реальное устройство по Wi-Fi (без USB):
        //     MOCK_API_BASE_URL=http://<IP_ноутбука_в_сети>:8000
        //     (сервер запускать с --host 0.0.0.0)
        //
        // local.properties добавлен в .gitignore — ключи и URL не попадут в репо.
        buildConfigField(
            "String",
            "MOCK_API_BASE_URL",
            "\"${localProps.getProperty("MOCK_API_BASE_URL", "https://vtb.vibefounder.ru")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
