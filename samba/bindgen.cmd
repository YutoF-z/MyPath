cd /d %~dp0

set NAME=samba_cargo
set EXT=dll

rem https://mozilla.github.io/uniffi-rs/latest/Getting_started.html
cargo build --release

rem https://docs.rs/crate/cargo-ndk/4.1.2
rem rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
cargo ndk -t armeabi-v7a -t arm64-v8a -o ./src/androidMain/jniLibs build --release

rem cargo build --target aarch64-apple-ios --release
rem cargo build --target aarch64-apple-ios-sim --release

cargo run --release --bin uniffi-bindgen generate --library target/release/%NAME%.%EXT% --language kotlin --out-dir ./src/commonMain/kotlin --no-format