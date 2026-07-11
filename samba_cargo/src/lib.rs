
uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn add(a: u32, b: u32) -> u32 { a + b }