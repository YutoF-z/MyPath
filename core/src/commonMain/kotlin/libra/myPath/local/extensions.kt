package libra.myPath.local

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import libra.myPath.MyDirectory
import libra.myPath.MyFile
import libra.myPath.MyPath

@OptIn(ExperimentalSerializationApi::class)
val LocalModule by lazy {
    SerializersModule {
        polymorphic(MyPath::class) {
            subclassesOfSealed<LocalMyPath>()
        }
        polymorphic(MyDirectory::class) {
            subclass(LocalDirectory::class)
        }
        polymorphic(MyFile::class) {
            subclass(LocalFile::class)
        }
    }
}