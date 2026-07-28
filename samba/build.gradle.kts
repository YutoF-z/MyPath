import org.gradle.platform.BuildPlatform

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)

    alias(libs.plugins.dokka)
    alias(libs.plugins.serialization)
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
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // put your multiplatform dependencies here
            kotlin("reflect")
            implementation(project(":core"))
            implementation(libs.bundles.impl.samba)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    description = "cargoBuild"

    val bin = "uniffi-bindgen"
    val library = "target/release/samba_cargo.dll"
    val jniLibs = "src/androidMain/jniLibs"
    val outDir = "src/commonMain/kotlin"

    val cmd = "cmd /c"

    commandLine("$cmd cd samba")

    // https://mozilla.github.io/uniffi-rs/latest/Getting_started.html
    commandLine("$cmd cargo build --release")

    // https://docs.rs/crate/cargo-ndk/4.1.2
    // rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
    commandLine("$cmd cargo ndk -t x86_64 -t arm64-v8a -o $jniLibs build --release")

    // cargo build --target aarch64-apple-ios --release
    // cargo build --target aarch64-apple-ios-sim --release
    commandLine("$cmd cargo run --release --bin $bin generate --library $library --language kotlin --out-dir $outDir --no-format")
}

val uniFFIBindInstall = tasks.register<Copy>("uniFFIBindInstall") {
    description = "uniFFIBindInstall"

    from("target/aarch64-linux-android/release") {
        include("*.so")
    }
    into("samba/src/jvmMain/resources/jna/linux-aarch64")


    from("target/x86_64-linux-android/release") {
        include("*.so")
    }
    into("samba/src/jvmMain/resources/jna/linux-x86-64")

    from("target/release") {
        include("*.dll")
    }
    into("samba/src/jvmMain/resources/jna/win32-x86-64")
}

tasks.build {
    dependsOn(cargoBuild)
    dependsOn(uniFFIBindInstall)
}