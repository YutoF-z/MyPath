uniffi::setup_scaffolding!();

use smb2::{ClientConfig, FileReader, FileWriter, SmbClient, Tree};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;

#[derive(uniffi::Record)]
pub struct SmbServerConfig {
    pub host: Vec<String>,
    pub port: Option<u16>,
    pub share_name: String,
    pub username: String,
    pub password: String,
    pub domain: Option<String>,
}

#[derive(uniffi::Record)]
pub struct SmbMetadata {
    pub is_directory: bool,
    pub is_file: bool,
    pub size: u64,
}

#[derive(uniffi::Record)]
pub struct SmbDirEntry {
    pub name: String,
    pub is_directory: bool,
    pub size: u64,
}

#[derive(uniffi::Object)]
pub struct SmbFileSystem {
    client: Mutex<SmbClient>,
    tree: Mutex<Tree>,
}

#[derive(uniffi::Object)]
pub struct SmbFileReader {
    reader: Mutex<Option<FileReader>>,
}

#[derive(uniffi::Object)]
pub struct SmbFileWriter {
    writer: Mutex<Option<FileWriter>>,
}

impl SmbServerConfig {
    pub fn to_client_config(&self, timeout: Duration) -> Vec<ClientConfig> {
        self.host
            .iter()
            .map(|s| ClientConfig {
                addr: format!("{}:{}", s, self.port.unwrap_or(445)),
                timeout,
                username: self.username.clone(),
                password: self.password.clone(),
                domain: self.domain.clone().unwrap_or("".to_string()),
                auto_reconnect: true,
                compression: false,
                dfs_enabled: false,
                dfs_target_overrides: Default::default(),
            })
            .collect()
    }
}

#[uniffi::export]
pub async fn connect_samba(config: SmbServerConfig) -> Option<Arc<SmbFileSystem>> {
    let timeout = Duration::from_secs(3);

    for cnf in config.to_client_config(timeout) {
        let client = SmbClient::connect(cnf).await;

        if client.is_err() {
            continue;
        }
        let mut client = client.unwrap();
        if let Ok(tree) = client.connect_share(&config.share_name).await {
            return Some(Arc::new(SmbFileSystem {
                client: Mutex::new(client),
                tree: Mutex::new(tree),
            }));
        }
    }
    None
}

#[uniffi::export]
impl SmbFileSystem {
    pub async fn metadata(&self, path: &str) -> Option<SmbMetadata> {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;
        let stat = client.stat(&mut *tree, path).await.ok()?;
        Some(SmbMetadata {
            is_directory: stat.is_directory,
            is_file: !stat.is_directory,
            size: stat.size,
        })
    }

    pub async fn list_directory(&self, path: &str) -> Option<Vec<SmbDirEntry>> {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;
        let list = client.list_directory(&mut *tree, path).await.ok()?;
        Some(
            list.into_iter()
                .map(|item| SmbDirEntry {
                    name: item.name,
                    is_directory: item.is_directory,
                    size: item.size,
                })
                .collect(),
        )
    }

    pub async fn open_read(&self, path: &str) -> Option<Arc<SmbFileReader>> {
        let mut tree = self.tree.lock().await;
        let client = self.client.lock().await;
        let file = client.open_file_reader(&mut *tree, path).await.ok()?;
        Some(Arc::new(SmbFileReader {
            reader: Mutex::new(Some(file)),
        }))
    }

    pub async fn open_write(&self, path: &str, append: bool) -> Option<Arc<SmbFileWriter>> {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;

        let file = if append {
            client.create_file_writer(&mut *tree, path).await.ok()?
        } else {
            let size = tree.stat(client.connection_mut(), path).await.ok()?.size;
            client
                .create_file_writer_at(&mut *tree, path, size)
                .await
                .ok()?
        };

        Some(Arc::new(SmbFileWriter {
            writer: Mutex::new(Some(file)),
        }))
    }

    pub async fn create_directory(&self, path: &str) -> bool {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;
        client.create_directory(&mut *tree, path).await.is_ok()
    }

    pub async fn delete_file(&self, path: &str) -> bool {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;
        client.delete_file(&mut *tree, path).await.is_ok()
    }

    pub async fn delete_directory(&self, path: &str) -> bool {
        let mut tree = self.tree.lock().await;
        let mut client = self.client.lock().await;
        client.delete_directory(&mut *tree, path).await.is_ok()
    }
}

#[uniffi::export]
impl SmbFileReader {
    pub async fn read_at(&self, offset: u64, len: u64) -> Option<Vec<u8>> {
        let reader = self.reader.lock().await;
        if let Some(reader) = reader.as_ref() {
            reader.read_at(offset, len).await.ok()
        } else {
            None
        }
    }

    pub async fn finish(&self) -> bool {
        let mut guard = self.reader.lock().await;
        if let Some(reader) = guard.take() {
            reader.close().await.is_ok()
        } else {
            false
        }
    }
}

#[uniffi::export]
impl SmbFileWriter {
    pub async fn write_at(&self, data: Vec<u8>) -> bool {
        let mut writer = self.writer.lock().await;
        if let Some(writer) = writer.as_mut() {
            writer
                .write_chunk(&data)
                .await
                .is_ok()
        } else {
            false
        }
    }
    

    pub async fn finish(&self) -> bool {
        let mut guard = self.writer.lock().await;
        if let Some(writer) = guard.take() {
            writer.finish().await.is_ok()
        } else {
            false
        }
    }
}
