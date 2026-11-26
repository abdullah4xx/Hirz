import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun prop(name: String, env: String): String? =
    keystoreProperties.getProperty(name) ?: System.getenv(env)

val releaseStoreFile = prop("storeFile", "HIRZ_STORE_FILE")
val releaseStorePassword = prop("storePassword", "HIRZ_STORE_PASSWORD")
val releaseKeyAlias = prop("keyAlias", "HIRZ_KEY_ALIAS")
val releaseKeyPassword = prop("keyPassword", "HIRZ_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "hirz.codx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "hirz.codx.app"
        minSdk = 30
        targetSdk = 35
        val versionProps = Properties().apply {
            rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val localCode = versionProps.getProperty("versionCode")?.toIntOrNull() ?: 1
        val localName = versionProps.getProperty("versionName") ?: "0.1.0"
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = maxOf(localCode, runNumber)
        versionName = System.getenv("HIRZ_VERSION_NAME") ?: localName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("prod") {
            dimension = "channel"
            applicationId = "hirz.codx.app"
        }
        create("dev") {
            dimension = "channel"
            applicationId = "hirz.codx.app.dev"
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.argon2kt)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
