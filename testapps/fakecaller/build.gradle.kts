// Emulator-only fixture: pretends to be WhatsApp and posts call notifications so the
// unified call log can be tested end to end. Never install on a real phone with WhatsApp.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.opencell.fakecaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whatsapp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
