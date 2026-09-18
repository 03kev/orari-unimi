import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseCredentialsFile = rootProject.file("signing/release.properties")
val releaseKeystoreFile = rootProject.file("signing/release.keystore")
val releaseCredentials = Properties().apply {
    if (releaseCredentialsFile.isFile) releaseCredentialsFile.inputStream().use(::load)
}
val hasReleaseSigning = releaseCredentialsFile.isFile && releaseKeystoreFile.isFile

android {
    namespace = "app.orariunimi"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.orariunimi"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.5.3"
    }

    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = releaseKeystoreFile
            storePassword = requireNotNull(releaseCredentials.getProperty("storePassword"))
            keyAlias = requireNotNull(releaseCredentials.getProperty("keyAlias"))
            keyPassword = requireNotNull(releaseCredentials.getProperty("keyPassword"))
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

val checkReleaseSigning by tasks.registering {
    doLast {
        check(hasReleaseSigning) {
            "Manca la chiave release: configura signing/release.keystore e signing/release.properties."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseSigning)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.work:work-runtime:2.11.2")
    testImplementation("junit:junit:4.13.2")
}
