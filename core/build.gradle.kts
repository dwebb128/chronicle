plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "local.oss.chronicle.core"
    compileSdk = 36

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    defaultConfig {
        // The phone app supports older devices than the watch app, so the shared layer has to
        // build against the lower of the two.
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")

        // Shared code reports the app version to Plex; both app modules ship the same one.
        buildConfigField("String", "VERSION_NAME", "\"0.62.4\"")
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
        buildConfig = true
    }

    sourceSets {
        // Fixtures shared by this module's tests and both app modules' tests.
        getByName("test") {
            java.srcDir("src/testShared/java")
        }
        getByName("androidTest") {
            java.srcDir("src/testShared/java")
        }
    }
}

// KSP configuration for Room — the schemas live with the entities, which are in this module.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // `api` rather than `implementation` for anything that appears in this module's public
    // signatures: both app modules consume these types directly.
    api(libs.timber)
    api(libs.fetch)
    api(libs.work)
    api(libs.result)
    api(libs.coroutines)
    api(libs.lifecycle.livedata.ktx)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.moshi)
    api(libs.retrofit)
    api(libs.retrofit.converter)
    api(libs.okhttp3)
    api(libs.okhttp3.logging)
    api(libs.coil.compose)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.dagger)

    // MediaPlayerService extends androidx.media.MediaBrowserServiceCompat and the playback layer
    // is written against the android.support.v4.media.* compat types.
    api(libs.media.compat)
    api(libs.localbroadcastmanager)
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    api(libs.media3.datasource)

    // The media notification promotes itself to an OngoingActivity on Wear. The API is a no-op
    // on a phone, so the shared player can call it unconditionally.
    api(libs.wear.ongoing)

    implementation(libs.security.crypto)
    implementation(libs.annotation)
    compileOnly(libs.facebook.infer.annotation)

    ksp(libs.room.compiler)
    ksp(libs.dagger.compiler)

    /*
     * Local Tests — the data layer, player and account tests moved here with the code they cover,
     * which they reach through `internal` visibility.
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
