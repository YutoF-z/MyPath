package libra.myPath

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlinx.serialization.cbor.Cbor

interface MyFile: MyPath {
    fun toWritableOrNull(): MyRWFile?
    suspend fun source(): RawSource

    suspend fun readString(): String =
        source().buffered().use { it.readString() }

    suspend fun readByteArray(): ByteArray =
        source().buffered().use { it.readByteArray() }

    suspend fun <T> readValueFromBinaly(serializer: KSerializer<T>, format: BinaryFormat = Cbor): T = format.decodeFromByteArray(serializer, readByteArray())
        
    suspend fun <T> readValue(serializer: KSerializer<T>, format: StringFormat = json): T = when(format) {
        is Json -> source().buffered().use { 
            format.decodeFromSource(serializer, it) 
        }
        else -> format.decodeFromString(serializer, readString())
    }
}

suspend inline fun <reified T> MyFile.readValueFromBinaly(formatformat: BinaryFormat = Cbor): T =
    readValueFromBinaly(serializer(), format)
    
suspend inline fun <reified T> MyFile.readValue(format: StringFormat = json): T =
    readValue(serializer(), format)
