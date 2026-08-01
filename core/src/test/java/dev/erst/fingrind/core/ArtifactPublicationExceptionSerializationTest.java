package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationKeyFilePublicationDurabilityException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves publication failures retain their actionable path facts across Java serialization. */
class ArtifactPublicationExceptionSerializationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void outcomeUncertainExceptionRoundTripsCandidateAndRetainedStage() throws Exception {
    Path candidatePath = temporaryDirectory.resolve("candidate.receipt");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve("candidate-stage.tmp"));

    ArtifactPublicationOutcomeUncertainException restored =
        roundTrip(
            new ArtifactPublicationOutcomeUncertainException(
                candidatePath, retention, new IOException("link outcome unknown")),
            ArtifactPublicationOutcomeUncertainException.class);

    assertEquals(candidatePath.toAbsolutePath().normalize(), restored.candidateArtifactPath());
    assertEquals(retention, restored.retainedStage());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void outcomeUncertainExceptionExposesItsLiveCandidateAndRetainedStage() {
    Path candidatePath = temporaryDirectory.resolve("live-candidate.receipt");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve("live-candidate-stage.tmp"));
    ArtifactPublicationOutcomeUncertainException exception =
        new ArtifactPublicationOutcomeUncertainException(
            candidatePath, retention, new IOException("link outcome unknown"));

    assertEquals(candidatePath.toAbsolutePath().normalize(), exception.candidateArtifactPath());
    assertEquals(retention, exception.retainedStage());
  }

  @Test
  void outcomeUncertainExceptionRoundTripsAnOutcomeWithoutAStage() throws Exception {
    Path candidatePath = temporaryDirectory.resolve("clean-candidate.receipt");

    ArtifactPublicationOutcomeUncertainException restored =
        roundTrip(
            new ArtifactPublicationOutcomeUncertainException(
                candidatePath, null, new IOException("link outcome unknown")),
            ArtifactPublicationOutcomeUncertainException.class);

    assertEquals(candidatePath.toAbsolutePath().normalize(), restored.candidateArtifactPath());
    assertNull(restored.retainedStage());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void outcomeUncertainExceptionRejectsASelfReferentialRetainedStage() {
    Path candidatePath = temporaryDirectory.resolve("candidate.receipt");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ArtifactPublicationOutcomeUncertainException(
                    candidatePath,
                    new ArtifactPublicationRetention(candidatePath),
                    new IOException("link outcome unknown")));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("distinct canonical paths"));
  }

  @Test
  void retainedStageExceptionRoundTripsTheStageAndPrimaryFailure() throws Exception {
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve("unpublished-stage.tmp"));

    ArtifactPublicationRetainedStageException restored =
        roundTrip(
            new ArtifactPublicationRetainedStageException(
                retention, new IOException("stage write failed")),
            ArtifactPublicationRetainedStageException.class);

    assertEquals(retention, restored.retainedStage());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void durabilityExceptionRoundTripsPublicationAndRetentionFacts() throws Exception {
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve("published-stage.tmp"));
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(temporaryDirectory.resolve("published.pk8"), retention);

    AttestationKeyFilePublicationDurabilityException restored =
        roundTrip(
            new AttestationKeyFilePublicationDurabilityException(
                publication, new IOException("directory force failed")),
            AttestationKeyFilePublicationDurabilityException.class);

    assertEquals(
        publication.publishedArtifactPath(), restored.publication().publishedArtifactPath());
    assertEquals(publication.retention(), restored.publication().retention());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  private static <T extends Throwable> T roundTrip(T exception, Class<T> expectedType)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return expectedType.cast(input.readObject());
    }
  }
}
