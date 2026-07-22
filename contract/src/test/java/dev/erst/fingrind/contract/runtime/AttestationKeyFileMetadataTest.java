package dev.erst.fingrind.contract.runtime;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Value-boundary coverage for the non-secret attestation credential response contract. */
class AttestationKeyFileMetadataTest {
  @TempDir Path temporaryDirectory;

  @Test
  void acceptsAnAbsentOrRegularCredentialPathWithCanonicalPublicValues() throws Exception {
    Path absentPath = temporaryDirectory.resolve("absent.fgatk");
    AttestationKeyFileMetadata absent =
        new AttestationKeyFileMetadata(absentPath, "MCowBQYDK2VwAyEA", "a1b2");
    Path regularPath = temporaryDirectory.resolve("regular.fgatk");
    Files.writeString(regularPath, "public header only for value validation");
    AttestationKeyFileMetadata regular =
        new AttestationKeyFileMetadata(regularPath, "MCowBQYDK2VwAyEA", "a1b2");

    assertEquals(absentPath, absent.attestationKeyFilePath());
    assertEquals(regularPath, regular.attestationKeyFilePath());
  }

  @Test
  void rejectsDirectoriesAndMissingOrBlankPublicFields() throws Exception {
    Path directoryPath = temporaryDirectory.resolve("not-a-file");
    Files.createDirectory(directoryPath);

    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationKeyFileMetadata(directoryPath, "MCowBQYDK2VwAyEA", "a1b2"));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationKeyFileMetadata(nullOf(), "MCowBQYDK2VwAyEA", "a1b2"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationKeyFileMetadata(temporaryDirectory.resolve("valid.fgatk"), " ", "a1b2"));
    assertThrows(
        NullPointerException.class,
        () ->
            new AttestationKeyFileMetadata(
                temporaryDirectory.resolve("valid.fgatk"), nullOf(), "a1b2"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationKeyFileMetadata(temporaryDirectory.resolve("valid.fgatk"), "spki", " "));
  }
}
