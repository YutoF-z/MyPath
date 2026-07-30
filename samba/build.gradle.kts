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
//    linuxX64 {
//        compilations.getByName("main") {
//            cinterops.create("samba_cargo") {
//                defFile("src/nativeInterop/cinterop/samba_cargo.def")
//                includeDirs("src/nativeInterop/cinterop")
//            }
//        }
//
//        binaries.all {
//            linkerOpts(
//                "-L${projectDir}/../lib/target/x86_64-unknown-linux-gnu/release",
//                "-lsamba_cargo"
//            )
//        }
//    }

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

    val isWindows = true

    val library = "samba_cargo"
    val outDir = "target/release"

    // https://mozilla.github.io/uniffi-rs/latest/Getting_started.html
    // https://docs.rs/crate/cargo-ndk/4.1.2
    // rustup target add aarch64-linux-android x86_64-linux-android armv7-linux-androideabi i686-linux-android

    val head = if (isWindows) arrayOf("cmd", "/c") else arrayOf()
    val ext = if (isWindows) "dll" else "dll"

    commandLine(
        *head,
        listOf(
            "cargo build --release",
//             cargo build --release --target aarch64-apple-ios,
//             cargo build --release --target aarch64-apple-ios-sim,
            "cargo ndk -t x86_64 -t arm64-v8a -o src/androidMain/jniLibs build --release",
//            "cbindgen --crate $library --output $outDir/$library.h"
            "cargo run --bin uniffi-bindgen generate --library $outDir/$library.$ext --language kotlin --out-dir $outDir --no-format"
        ).joinToString(" && ")
    )
}

tasks.register<Copy>("uniFFIBindInstall") {
    dependsOn(cargoBuild)

    description = "uniFFIBindInstall"
    destinationDir = projectDir

    from("target/aarch64-linux-android/release") {
        include("*.so")
        into("src/jvmMain/resources/jna/linux-aarch64")
    }

    from("target/x86_64-linux-android/release") {
        include("*.so")
        into("src/jvmMain/resources/jna/linux-x86-64")
    }

    from("target/release") {
        include("*.dll")
        into("src/jvmMain/resources/jna/win32-x86-64")
    }

    from("target/release/uniffi") {
        include("**/*.kt")
        into("src/androidMain/kotlin")
    }

    from("target/release/uniffi") {
        include("**/*.kt")
        into("src/jvmMain/kotlin")
    }

//    from("target/release/samba_cargo.h") {
//        into("src/nativeInterop/cinterop")
//        samba_cargo.def
//        headers = samba_cargo.h
//        package = uniffi.samba_cargo
//    }
}
