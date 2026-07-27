package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises CLI creation of an encrypted founder credential and its persisted signed genesis. */
class CliAttestedOpenBookTest extends CliWorkflowFixtureSupport {
  @Test
  void createsAndPersistsSignedGenesisWithTheNewBook() {
    Path bookPath = tempDirectory.resolve("attested-open-book.sqlite");
    Path bookKeyPath = writeBookKey(bookPath);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    String[] arguments = openBookKeyFileArguments(bookPath, bookKeyPath);
    try (AttestationSigningCredential credential =
        AttestationKeyFiles.openOrCreateCredential(
                java.util.UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
                Path.of(arguments[arguments.length - 3]),
                Path.of(arguments[arguments.length - 1]))
            .credential()) {
      AttestationGenesis.requireMatchingBookIdentity(
          AttestationGenesis.create(
              java.util.UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
              bookIdentity(),
              fixedClock().instant(),
              java.util.List.of(credential)),
          bookIdentity());
    } catch (Exception exception) {
      throw new AssertionError(
          "Founder credential and genesis construction must succeed.", exception);
    }

    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(arguments);

    assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));

    ByteArrayOutputStream verificationOutput = new ByteArrayOutputStream();
    int verificationExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(verificationOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "verify-book",
                    "--book-file",
                    bookPath.toString(),
                    "--book-key-file",
                    bookKeyPath.toString()));

    assertEquals(0, verificationExitCode, verificationOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(verificationOutput, "\"previousHead\":\"" + "0".repeat(64) + "\"");
  }
}
