package libra.myPath

interface MyRWPath: MyPath {
    override suspend fun isWritable(): Boolean = true

    suspend fun mk()
    suspend fun cp(path: MyRWPath)
    suspend fun mv(path: MyRWPath)
    suspend fun rm()
}
