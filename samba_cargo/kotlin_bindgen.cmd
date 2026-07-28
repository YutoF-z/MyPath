cd /d %~dp0

set NAME=samba_cargo
set EXT=dll

cargo build --release
cargo ndk -t armeabi-v7a -t arm64-v8a -o ./jniLibs build --release

ren cargo build --target aarch64-apple-ios --release
ren cargo build --target aarch64-apple-ios-sim --release

cargo run --release --bin uniffi-bindgen generate --library target/release/%NAME%.%EXT% --language kotlin --out-dir target/release/uniffi_bindgen