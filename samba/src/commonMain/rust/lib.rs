uniffi::setup_scaffolding!();

use smb2::{SmbClient, Tree};
use std::option::Option;
use std::sync::Arc;
use tokio::sync::Mutex;

#[derive(uniffi::Object)]
pub struct UniSmbClient {
    client: Mutex<SmbClient>,
}

#[derive(uniffi::Object)]
pub struct UniSmbTree {
    tree: Mutex<Tree>,
}

#[derive(uniffi::Record)]
pub struct FileInfo {
    pub name: String,
    pub size: u64,
    pub is_directory: bool,
    pub created: u64,
    pub modified: u64,
    pub accessed: Option<u64>,
}

#[uniffi::export]
pub async fn connect(addr: &str, username: &str, password: &str) -> Option<Arc<UniSmbClient>> {
    if let Ok(client) = smb2::connect(addr, username, password).await {
        Some(Arc::new(UniSmbClient {
            client: Mutex::new(client),
        }))
    } else {
        None
    }
}

#[uniffi::export]
impl UniSmbClient {
    pub async fn reconnect(&self) -> bool {
        self.client.lock().await.reconnect().await.is_ok()
    }

    pub async fn connect_share(&self, share_name: &str) -> Option<Arc<UniSmbTree>> {
        if let Ok(tree) = self.client.lock().await.connect_share(share_name).await {
            Some(Arc::new(UniSmbTree {
                tree: Mutex::new(tree),
            }))
        } else {
            None
        }
    }

    pub async fn list_directory(&self, tree: &UniSmbTree, path: &str) -> Option<Vec<FileInfo>> {
        if let Ok(list) = self
            .client
            .lock()
            .await
            .list_directory(&mut *tree.tree.lock().await, path)
            .await
        {
            Some(
                list.into_iter()
                    .map(|it| FileInfo {
                        name: it.name,
                        size: it.size,
                        is_directory: it.is_directory,
                        created: it.created.0,
                        modified: it.modified.0,
                        accessed: None,
                    })
                    .collect(),
            )
        } else {
            None
        }
    }

    pub async fn read_file(&self, tree: &UniSmbTree, path: &str) -> Option<Vec<u8>> {
        self.client
            .lock()
            .await
            .read_file(&mut *tree.tree.lock().await, path)
            .await
            .ok()
    }

    pub async fn read_file_pipelined(&self, tree: &UniSmbTree, path: &str) -> Option<Vec<u8>> {
        self.client
            .lock()
            .await
            .read_file_pipelined(&mut *tree.tree.lock().await, path)
            .await
            .ok()
    }

    pub async fn write_file(&self, tree: &UniSmbTree, path: &str, data: Vec<u8>) {
        self.client
            .lock()
            .await
            .write_file(&mut *tree.tree.lock().await, path, &*data)
            .await
            .ok();
    }

    pub async fn write_file_pipelined(&self, tree: &UniSmbTree, path: &str, data: Vec<u8>) {
        self.client
            .lock()
            .await
            .write_file_pipelined(&mut *tree.tree.lock().await, path, &*data)
            .await
            .ok();
    }

    pub async fn delete_file(&self, tree: &UniSmbTree, path: &str) {
        self.client
            .lock()
            .await
            .delete_file(&mut *tree.tree.lock().await, path)
            .await
            .ok();
    }

    pub async fn stat(&self, tree: &UniSmbTree, path: &str) -> Option<FileInfo> {
        if let Ok(stat) = self
            .client
            .lock()
            .await
            .stat(&mut *tree.tree.lock().await, path)
            .await
        {
            Some(FileInfo {
                name: path
                    .trim_end_matches(['/', '\\'])
                    .split(['/', '\\'])
                    .last()
                    .unwrap_or("")
                    .to_string(),
                size: stat.size,
                is_directory: stat.is_directory,
                created: stat.created.0,
                modified: stat.modified.0,
                accessed: Some(stat.accessed.0),
            })
        } else {
            None
        }
    }

    pub async fn create_directory(&self, tree: &UniSmbTree, path: &str) -> bool {
        self.client
            .lock()
            .await
            .create_directory(&mut *tree.tree.lock().await, path)
            .await
            .is_ok()
    }

    pub async fn delete_directory(&self, tree: &UniSmbTree, path: &str) -> bool {
        self.client
            .lock()
            .await
            .delete_directory(&mut *tree.tree.lock().await, path)
            .await
            .is_ok()
    }
}
