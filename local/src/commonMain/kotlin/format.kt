package libra.myPath

import kotlinx.serialization.json.Json

val json by lazy {
    Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
}