package dev.erst.fingrind.contract.runtime;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Coverage tests for generated book-key metadata and its retained publication fact. */
class GeneratedBookKeyFileTest {
  @TempDir Path tempDir;

  @Test
  void generatedBookKeyMetadataCarriesTheCanonicalPublicationFact() throws Exception {
    Path existingFile = Files.createFile(tempDir.resolve("book.key"));
    ArtifactPublicationResult existingPublication = publication(existingFile, ".book.key.stage");
    ArtifactPublicationResult futurePublication =
        publication(tempDir.resolve("future.key"), ".future.key.stage");

    GeneratedBookKeyFile existing =
        new GeneratedBookKeyFile(existingPublication, "base64", 256, "rw-------");
    GeneratedBookKeyFile future =
        new GeneratedBookKeyFile(futurePublication, "base64", 256, "rw-------");

    assertEquals(existingPublication, existing.publication());
    assertEquals("rw-------", future.permissions());
  }

  @Test
  void generatedBookKeyMetadataRejectsMissingPublicationAndInvalidFields() {
    ArtifactPublicationResult publication =
        publication(tempDir.resolve("book.key"), ".book.key.stage");

    assertThrows(
        NullPointerException.class,
        () -> new GeneratedBookKeyFile(nullOf(), "base64", 256, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(publication, " ", 256, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(publication, "base64", 0, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(publication, "base64", 256, " "));
  }

  private ArtifactPublicationResult publication(Path artifactPath, String retainedStageName) {
    return new ArtifactPublicationResult(
        artifactPath, new ArtifactPublicationRetention(tempDir.resolve(retainedStageName)));
  }
}
