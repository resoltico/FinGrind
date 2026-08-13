package dev.erst.fingrind.contract.runtime;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Coverage tests for generated book-key metadata and its completed publication fact. */
class GeneratedBookKeyFileTest {
  @TempDir Path tempDir;

  @Test
  void generatedBookKeyMetadataCarriesTheCanonicalPublicationFact() throws Exception {
    PublicationTransactionArtifact existingPublication = publication(tempDir.resolve("book.key"));
    PublicationTransactionArtifact futurePublication = publication(tempDir.resolve("future.key"));

    GeneratedBookKeyFile existing =
        new GeneratedBookKeyFile(existingPublication, "base64", 256, "rw-------");
    GeneratedBookKeyFile future =
        new GeneratedBookKeyFile(futurePublication, "base64", 256, "rw-------");

    assertEquals(existingPublication, existing.publication());
    assertEquals("rw-------", future.permissions());
  }

  @Test
  void generatedBookKeyMetadataRejectsMissingPublicationAndInvalidFields() {
    PublicationTransactionArtifact publication = publication(tempDir.resolve("book.key"));

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

  private PublicationTransactionArtifact publication(Path artifactPath) {
    return new PublicationTransactionArtifact(
        artifactPath,
        new PublicationTransactionResult(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE)));
  }
}
