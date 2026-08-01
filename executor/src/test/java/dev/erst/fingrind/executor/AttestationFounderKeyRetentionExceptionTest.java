package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the irreversible open-book preparation retention boundary. */
class AttestationFounderKeyRetentionExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void retainsTheCompleteArtifactFactAlongsideThePrimaryFailure() {
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact artifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
            temporaryDirectory.resolve("founder.fgatk"),
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-stage.tmp")));
    IOException primary = new IOException("signing failed");

    AttestationFounderKeyRetentionException exception =
        new AttestationFounderKeyRetentionException(List.of(artifact), primary);

    assertEquals(List.of(artifact), exception.retainedFounderKeyArtifacts());
    assertSame(primary, exception.getCause());
  }

  @Test
  void rejectsAnEmptyArtifactSet() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AttestationFounderKeyRetentionException(List.of(), new IOException("failed")));

    assertEquals(
        "A founder-key retention exception requires at least one retained artifact.",
        exception.getMessage());
  }
}
