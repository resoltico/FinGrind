package dev.erst.fingrind.buildlogic

/**
 * Produces the argument vector for the host-native MSVC managed SQLite build.
 *
 * MSVC parses the quote-bearing `/Fo`, `/OUT`, and `/IMPLIB` option values itself, while the
 * source path remains a separate process argument.
 */
internal object WindowsManagedSqliteCompilePlan {
    fun commandLine(
        compiler: String,
        sourceFilePath: String,
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
        compilerHardeningFlags: List<String>,
        linkerHardeningFlags: List<String>,
        outputLibraryFilePath: String,
        importLibraryFilePath: String,
        objectFilePath: String,
    ): List<String> =
        buildList {
            add(compiler)
            add("/nologo")
            add("/O2")
            addAll(compilerHardeningFlags)
            add("/LD")
            addAll(
                ManagedSqliteArtifactSupport.windowsCompilerDefines(
                    requiredCompileOptions,
                    requiresSecureMemorySupport,
                ),
            )
            // The managed runtime selects SQLite's locking-preserving long-path VFS on Windows.
            // Make its wide-character implementation an explicit binary-build contract.
            add("/DSQLITE_WIN32_HAS_WIDE=1")
            add("/DSQLITE_API=__declspec(dllexport)")
            add("/Fo\"$objectFilePath\"")
            add(sourceFilePath)
            add("/link")
            add("/NOLOGO")
            add("/INCREMENTAL:NO")
            addAll(linkerHardeningFlags)
            add("/OUT:\"$outputLibraryFilePath\"")
            add("/IMPLIB:\"$importLibraryFilePath\"")
        }
}
