
#[unsafe(no_mangle)]
#[uniffi::export]
pub extern "C" fn add(a: u32, b: u32) -> u32 {
    a + b
}

uniffi::setup_scaffolding!();
