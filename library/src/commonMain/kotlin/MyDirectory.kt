package libra.myPath

interface MyDirectory: MyPath {
    fun toWritableOrNull(): MyRWDirectory?

    fun list(pattern: Regex? = null): Sequence<MyPath>
}