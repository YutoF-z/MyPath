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

struct SmbSession {
    client: SmbClient,
    tree: Tree,
}

#[derive(uniffi::Object)]
pub struct SmbFileSystem {
    session: Mutex<SmbSession>,
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
    pub fn iter_client_configs(&self, timeout: Duration) -> impl Iterator<Item = ClientConfig> + '_ {
        let port = self.port.unwrap_or(445);
        self.host.iter().map(move |s| ClientConfig {
            addr: format!("{s}:{port}"),
            timeout,
            username: self.username.clone(),
            password: self.password.clone(),
            domain: self.domain.clone().unwrap_or_default(),
            auto_reconnect: true,
            compression: false,
            dfs_enabled: false,
            dfs_target_overrides: Default::default(),
            dfs_target_policy: Default::default(),
            connect_options: None,
            response_timeout: None,
            previous_session_id: None,
        })
    }
}

#[uniffi::export(async_runtime = "tokio")]
pub async fn connect_samba(config: SmbServerConfig) -> Option<Arc<SmbFileSystem>> {
    let timeout = Duration::from_secs(3);

    for cnf in config.iter_client_configs(timeout) {
        let Ok(mut client) = SmbClient::connect(cnf).await else {
            continue;
        };
        if let Ok(tree) = client.connect_share(&config.share_name).await {
            return Some(Arc::new(SmbFileSystem {
                session: Mutex::new(SmbSession { client, tree }),
            }));
        }
    }
    None
}

#[uniffi::export(async_runtime = "tokio")]
impl SmbFileSystem {
    pub async fn metadata(&self, path: &str) -> Option<SmbMetadata> {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        let stat = client.stat(tree, path).await.ok()?;
        Some(SmbMetadata {
            is_directory: stat.is_directory,
            is_file: !stat.is_directory,
            size: stat.size,
        })
    }

    pub async fn list_directory(&self, path: &str) -> Option<Vec<SmbDirEntry>> {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        let list = client.list_directory(tree, path).await.ok()?;
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
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        let file = client.open_file_reader(tree, path).await.ok()?;
        Some(Arc::new(SmbFileReader {
            reader: Mutex::new(Some(file)),
        }))
    }

    pub async fn open_write(&self, path: &str, append: bool) -> Option<Arc<SmbFileWriter>> {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;

        let file = if append {
            let size = tree
                .stat(client.connection_mut(), path)
                .await
                .map(|s| s.size)
                .unwrap_or(0);
            client.create_file_writer_at(tree, path, size).await.ok()?
        } else {
            client.create_file_writer(tree, path).await.ok()?
        };

        Some(Arc::new(SmbFileWriter {
            writer: Mutex::new(Some(file)),
        }))
    }

    pub async fn create_directory(&self, path: &str) -> bool {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        client.create_directory(tree, path).await.is_ok()
    }

    pub async fn delete_file(&self, path: &str) -> bool {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        client.delete_file(tree, path).await.is_ok()
    }

    pub async fn delete_directory(&self, path: &str) -> bool {
        let mut session = self.session.lock().await;
        let SmbSession { client, tree } = &mut *session;
        client.delete_directory(tree, path).await.is_ok()
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl SmbFileReader {
    pub async fn read_at(&self, offset: u64, len: u64) -> Option<Vec<u8>> {
        let guard = self.reader.lock().await;
        guard.as_ref()?.read_at(offset, len).await.ok()
    }

    pub async fn finish(&self) -> bool {
        let mut guard = self.reader.lock().await;
        match guard.take() {
            Some(reader) => reader.close().await.is_ok(),
            None => false,
        }
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl SmbFileWriter {
    pub async fn write_at(&self, data: Vec<u8>) -> bool {
        let mut guard = self.writer.lock().await;
        match guard.as_mut() {
            Some(writer) => writer.write_chunk(&data).await.is_ok(),
            None => false,
        }
    }

    pub async fn finish(&self) -> bool {
        let mut guard = self.writer.lock().await;
        match guard.take() {
            Some(writer) => writer.finish().await.is_ok(),
            None => false,
        }
    }
}
