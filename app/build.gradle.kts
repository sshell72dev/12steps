import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.na.step4.obidy"
    compileSdk = 35

    val localProps = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { localProps.load(it) }
    }
    val versionProps = Properties()
    val versionFile = rootProject.file("version.properties")
    if (versionFile.exists()) {
        versionFile.inputStream().use { versionProps.load(it) }
    }
    val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
    val appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0")
    fun localProp(name: String, default: String = ""): String =
        (localProps.getProperty(name) ?: default)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

    defaultConfig {
        applicationId = "ru.na.steps12"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("int", "APP_VERSION_CODE", "$appVersionCode")
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("String", "VAPI_PUBLIC_KEY", "\"${localProp("VAPI_PUBLIC_KEY")}\"")
        buildConfigField("String", "VAPI_ASSISTANT_ID", "\"${localProp("VAPI_ASSISTANT_ID")}\"")
        buildConfigField(
            "String",
            "ANALYSIS_API_URL",
            "\"${localProp("ANALYSIS_API_URL", "https://12stepsapp.luch-rehab.ru")}\""
        )
        buildConfigField("String", "ANALYSIS_API_TOKEN", "\"${localProp("ANALYSIS_API_TOKEN")}\"")
        // 64-bit only: Play 16 KB requirement applies to 64-bit ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Keep Daily that ships with Vapi — forcing 0.37 broke call join / hung on connect.
    implementation("ai.vapi.android:vapi:1.0.7")
    implementation(project(":voice"))
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
