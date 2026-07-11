plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)

    alias(libs.plugins.dokka)
    alias(libs.plugins.serialization)
    alias(libs.plugins.uniffi)
}


group = "libra.myPath.samba"
version = libs.versions.project.get()

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    jvm()

    android {
        namespace = "libra.myPath.samba"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()
//    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // put your multiplatform dependencies here
            kotlin("reflect")
            include(":core")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

uniffi {
    projectName.set("samba_cargo")
    cargoConfig {
        cargoTomlPath.set(file("../samba_cargo/Cargo.toml").absolutePath)
    }
}