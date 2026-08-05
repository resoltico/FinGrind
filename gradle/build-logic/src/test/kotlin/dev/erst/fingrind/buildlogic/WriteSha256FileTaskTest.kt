package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder

class WriteSha256FileTaskTest {
    @Test
    fun writesAnUtf8LfTerminatedPortableChecksumRecord() {
        val temporaryRoot = Files.createTempDirectory("write-sha256-file-task")
        try {
            val input = temporaryRoot.resolve("fingrind-1.2.3-windows-x86_64.zip")
            val output = temporaryRoot.resolve("fingrind-1.2.3-windows-x86_64.zip.sha256")
            Files.writeString(input, "published archive bytes", UTF_8)
            val project = ProjectBuilder.builder().withProjectDir(temporaryRoot.toFile()).build()
            val task = project.tasks.register<WriteSha256FileTask>("writeChecksum").get()
            task.inputFile.set(input.toFile())
            task.outputFile.set(output.toFile())

            task.writeSha256File()

            val expectedDigest =
                HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)),
                )
            val checksumRecord = Files.readString(output, UTF_8)
            assertEquals("$expectedDigest  ${input.fileName}\n", checksumRecord)
            assertFalse(checksumRecord.contains('\r'))
        } finally {
            DistributionContractReaderTestSupport.deleteTree(temporaryRoot)
        }
    }
}
