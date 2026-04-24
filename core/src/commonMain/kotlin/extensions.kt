package libra.myPath

private val SCHEME_REGEX by lazy { Regex("""^\w{2,}://""") }
fun String.stripPrefix(): String = replace(SCHEME_REGEX, "")
