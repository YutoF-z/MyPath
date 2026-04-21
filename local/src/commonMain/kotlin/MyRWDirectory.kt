package libra.myPath

interface MyRWDirectory: MyDirectory, MyRWPath {
    override fun toWritableOrNull(): MyRWDirectory? = this

    suspend fun mkdirs(): MyRWDirectory
}