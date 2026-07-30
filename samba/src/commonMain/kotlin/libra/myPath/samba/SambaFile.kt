package libra.myPath.samba

import libra.myPath.MyFile

expect class SambaFile(
    rawPath: String
) : MyFile, SambaPath