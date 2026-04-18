
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)

    alias(libs.plugins.dokka)
    alias(libs.plugins.serialization)
}


group = "libra"
version = libs.versions.project.get()

kotlin {
    jvmToolchain(25)

    jvm()
    android {
        namespace = "libra.MyPath"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
            kotlin("reflect")
            implementation(libs.bundles.impl)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
