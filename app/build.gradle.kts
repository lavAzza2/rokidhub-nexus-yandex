import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseSigningProperties = Properties()
val releaseSigningFile = providers.gradleProperty("signingProperties")
    .map { file(it) }
    .orNull
if (releaseSigningFile?.isFile == true) {
    releaseSigningFile.inputStream().use(releaseSigningProperties::load)
}

android {
    namespace = "com.rokidhub.nexus.plugin.yandex"
    compileSdk = 36

    defaultConfig {
        val yandexClientId = providers.gradleProperty("yandexClientId")
            .orElse("not-configured")
            .get()
        val rokidHubBaseUrl = providers.gradleProperty("rokidHubBaseUrl")
            .orElse("https://rokidhub.com/api/v1/nexus")
            .get()
        applicationId = "com.rokidhub.nexus.plugin.yandex"
        minSdk = 30
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
        manifestPlaceholders["YANDEX_CLIENT_ID"] = yandexClientId
        buildConfigField("String", "YANDEX_CLIENT_ID", "\"$yandexClientId\"")
        buildConfigField("String", "ROKIDHUB_BASE_URL", "\"$rokidHubBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningProperties.getProperty("storeFile")))
                storePassword = requireNotNull(releaseSigningProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseSigningProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseSigningProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.15.0")
    implementation("com.yandex.android:authsdk:3.1.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
