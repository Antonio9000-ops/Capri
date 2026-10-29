// Archivo: app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.capri.pet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.capri.pet"
        minSdk = 24 // Ajusta según tus necesidades
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Clave de debug fija: sin esto, cada build de CI se firma con una clave
    // distinta y Android rechaza la actualización (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
    signingConfigs {
        getByName("debug") {
            storeFile = file("capri-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Dependencias mínimas que solicitaste
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Para SecurityHelper.kt (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}
