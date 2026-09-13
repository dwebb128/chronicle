import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    id("kotlin-kapt") // Data Binding's annotation processor has no KSP equivalent.
}

android {
    namespace = "local.oss.chronicle"
    compileSdk = 36

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    defaultConfig {
        // Same applicationId as the watch app on purpose: Play treats them as one listing and
        // ships the right APK to each form factor. They are separate APKs, so a single device
        // only ever holds one of them.
        applicationId = "local.oss.chronicle"
        minSdk = 30
        targetSdk = 36
        versionCode = 67
        versionName = "0.62.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")

            if (System.getenv("KEYSTORE_FILE") != null) {
                storeFile = file(System.getenv("KEYSTORE_FILE"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())

                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"

        freeCompilerArgs +=
            listOf(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                )
        }
    }

    sourceSets {
        getByName("test") {
            java.srcDir("../core/src/testShared/java")
        }
        getByName("androidTest") {
            java.srcDir("../core/src/testShared/java")
        }
    }
}

dependencies {

    implementation(project(":core"))

    // Phone-only UI stack. The watch app uses Compose for Wear instead.
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.swiperefresh)

    implementation(libs.timber)
    implementation(libs.fetch)
    implementation(libs.work)
    implementation(libs.result)
    implementation(libs.security.crypto)
    implementation(libs.annotation)
    implementation(libs.coroutines)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    compileOnly(libs.facebook.infer.annotation)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter)
    implementation(libs.okhttp3)
    implementation(libs.okhttp3.logging)
    implementation(libs.moshi)

    // Coil replaces the Fresco/Glide pair the phone app used to carry.
    implementation(libs.coil.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(libs.media.compat)
    implementation(libs.localbroadcastmanager)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)

    /*
     * Local Tests
     */
    testImplementation(libs.dagger)
    kspTest(libs.dagger.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.hamcrest)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
