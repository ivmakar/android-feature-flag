import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Coordinates for composite-build consumption: an includeBuild() consumer can substitute
// implementation("io.github.ivmakar:feature-flags:<version>") with this project.
group = "io.github.ivmakar"
version = "0.1.0"

android {
    namespace = "io.github.ivmakar.featureflags"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // FakeFeatureFlags lives in the testFixtures source set so consumers can reuse it via
    // testImplementation(testFixtures(project(":feature-flags"))).
    testFixtures { enable = true }
    kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }
}

dependencies {
    api("androidx.datastore:datastore-preferences:1.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Hilt powers the ready-made DI module (io.github.ivmakar.featureflags.di). Consumers who do not
    // use Hilt can ignore that package and wire FeatureFlagsConfigurator manually — see README.
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")

    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.google.truth:truth:1.4.4")
}
