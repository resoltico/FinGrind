package dev.erst.fingrind.buildlogic

/** Exact source inventory for the Windows protected-output native interop boundary. */
internal object WindowsPrivateOutputFileNativeInteropSources {
    private const val CORE_MAIN_SOURCE_PREFIX =
        "/core/src/main/java/dev/erst/fingrind/core/"
    private const val CORE_TEST_SOURCE_PREFIX =
        "/core/src/test/java/dev/erst/fingrind/core/"

    private val bindingAndInvocationClasses =
        setOf(
            "WindowsPrivateOutputFileBindings",
            "WindowsPrivateOutputFileBindingSupport",
            "WindowsPrivateOutputFileCalls",
            "WindowsPrivateOutputFileFfmCalls",
            "WindowsPrivateOutputFileFfmInvocation",
            "WindowsPrivateOutputFileHandleBindings",
            "WindowsPrivateOutputFileHandleCalls",
            "WindowsPrivateOutputFileHandleFfmCalls",
        )
    private val ownerAndProofClasses =
        setOf(
            "WindowsPrivateOutputFileOwner",
            "WindowsPrivateOutputFileOwnerBindings",
            "WindowsPrivateOutputFileOwnerCalls",
            "WindowsPrivateOutputFileOwnerFfmCalls",
            "WindowsPrivateOutputFileSecurityBindings",
            "WindowsPrivateOutputFileSecurityCalls",
            "WindowsPrivateOutputFileSecurityFfmCalls",
            "WindowsPrivateOutputFileSecurityProof",
        )
    private val transportNativeAndHandleLifecycleClasses =
        setOf(
            "WindowsPrivateOutputFileFfmTransport",
            "WindowsPrivateOutputFileHandle",
            "WindowsPrivateOutputFileHandleLocks",
            "WindowsPrivateOutputFileNative",
            "WindowsPrivateOutputFileOperationArena",
            "WindowsPrivateOutputDirectoryFfmTransport",
        )
    private val testSupportAndVerificationClasses =
        setOf(
            "WindowsPrivateOutputFileBindingContractTest",
            "WindowsPrivateOutputFileCallTestSupport",
            "WindowsPrivateOutputFileFfmCallsTest",
            "WindowsPrivateOutputFileFfmTransportTest",
            "WindowsPrivateOutputFileFfmTransportResourceLifecycleTest",
            "WindowsPrivateOutputFileHandleTest",
            "WindowsPrivateOutputFileNativeTest",
        )
    private val nativeInteropSourceSuffixes =
        buildSet {
            addAll(bindingAndInvocationClasses.map(::mainSource))
            addAll(ownerAndProofClasses.map(::mainSource))
            addAll(transportNativeAndHandleLifecycleClasses.map(::mainSource))
            addAll(testSupportAndVerificationClasses.map(::testSource))
        }
    private val throwableInvocationSourceSuffixes =
        setOf("${CORE_MAIN_SOURCE_PREFIX}WindowsPrivateOutputFileFfmInvocation.java")

    fun isNativeInteropSource(path: String): Boolean =
        nativeInteropSourceSuffixes.any(path::endsWith)

    fun isThrowableInvocationSource(path: String): Boolean =
        throwableInvocationSourceSuffixes.any(path::endsWith)

    private fun mainSource(className: String): String =
        "${CORE_MAIN_SOURCE_PREFIX}${className}.java"

    private fun testSource(className: String): String =
        "${CORE_TEST_SOURCE_PREFIX}${className}.java"
}
