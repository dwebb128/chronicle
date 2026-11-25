import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "local.oss.chronicle.core"
    compileSdk = 37

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        checkAllWarnings = true
    }

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        // Share test utilities between unit tests and instrumented tests, here and in :app
        getByName("test") {
            java.srcDir("src/testShared/java")
        }
        getByName("androidTest") {
            java.srcDir("src/testShared/java")
        }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

// KSP configuration for Room
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // `api` for libraries whose types appear in this module's public signatures, which :app
    // uses directly.
    api(libs.timber)
    api(libs.fetch)
    api(libs.work)
    api(libs.result)
    api(libs.coroutines)
    api(libs.lifecycle.livedata.ktx)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.localbroadcastmanager)

    api(libs.retrofit)
    api(libs.retrofit.converter)

    api(libs.okhttp3)
    api(libs.okhttp3.logging)

    api(libs.moshi)

    api(libs.room.runtime)
    ksp(libs.room.compiler)
    api(libs.room.ktx)

    api(libs.dagger)
    ksp(libs.dagger.compiler)

    api(libs.media3.exoplayer)
    api(libs.media3.session)
    api(libs.media3.datasource)
    // MediaBrowserServiceCompat and the android.support.v4.media.* compat types
    api(libs.androidx.media)

    implementation(libs.seismic)
    implementation(libs.security.crypto)
    implementation(libs.annotation)
    compileOnly(libs.facebook.infer.annotation)

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
