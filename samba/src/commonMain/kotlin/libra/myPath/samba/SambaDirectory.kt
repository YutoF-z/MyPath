package libra.myPath.samba

import libra.myPath.MyDirectory


expect class SambaDirectory(
    rawPath: String
) : MyDirectory, SambaPath