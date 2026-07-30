package dev.erst.fingrind.buildlogic

/** Fixed public archive permissions, including the executable-file override. */
internal object CliBundleArchivePermissions {
    const val DIRECTORY_UNIX_MODE = 493
    const val REGULAR_FILE_UNIX_MODE = 420
    const val EXECUTABLE_FILE_UNIX_MODE = 493

    fun fileUnixMode(sourceFileIsExecutable: Boolean): Int =
        if (sourceFileIsExecutable) {
            EXECUTABLE_FILE_UNIX_MODE
        } else {
            REGULAR_FILE_UNIX_MODE
        }
}
