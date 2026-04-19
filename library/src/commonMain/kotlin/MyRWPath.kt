package libra.myPath

interface MyRWPath: MyPath {
    override val isWritable: Boolean
        get() = true

    suspend fun mk()
    suspend fun cp(path: MyRWPath)
    suspend fun mv(path: MyRWPath)
    suspend fun rm()
}