package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves the durability exception retains one mandatory publication-stage fact. */
class AttestationKeyFilePublicationDurabilityExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedDurabilityFailureRestoresThePublishedArtifactAndRetainedStage() throws Exception {
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            temporaryDirectory.resolve("published.fgatk"),
            new ArtifactPublicationRetention(temporaryDirectory.resolve("published-stage.tmp")));

    AttestationKeyFilePublicationDurabilityException restored =
        roundTrip(
            new AttestationKeyFilePublicationDurabilityException(
                publication, new IOException("simulated directory-force failure")));

    assertEquals(
        publication.publishedArtifactPath(), restored.publication().publishedArtifactPath());
    assertEquals(publication.retention(), restored.publication().retention());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void liveDurabilityFailureExposesTheOriginalPublicationFact() {
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            temporaryDirectory.resolve("published.fgatk"),
            new ArtifactPublicationRetention(temporaryDirectory.resolve("published-stage.tmp")));
    AttestationKeyFilePublicationDurabilityException failure =
        new AttestationKeyFilePublicationDurabilityException(
            publication, new IOException("simulated directory-force failure"));

    assertSame(publication, failure.publication());
  }

  @Test
  void serializedPublicationRequiresTheRetainedStagePath() {
    String publishedPath = temporaryDirectory.resolve("published.fgatk").toString();

    assertThrows(
        NullPointerException.class,
        () ->
            new AttestationKeyFilePublicationDurabilityException.SerializedPublication(
                publishedPath, nullOf()));
  }

  @Test
  void serializedDurabilityFailureDoesNotResolveItsCapturedParentAgain() throws Exception {
    Path capturedParent = Files.createDirectories(temporaryDirectory.resolve("captured"));
    Path replacementParent = Files.createDirectories(temporaryDirectory.resolve("replacement"));
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            capturedParent.resolve("published.fgatk"),
            new ArtifactPublicationRetention(capturedParent.resolve("published-stage.tmp")));
    byte[] serialized =
        serialize(
            new AttestationKeyFilePublicationDurabilityException(
                publication, new IOException("simulated directory-force failure")));
    try {
      Files.move(capturedParent, temporaryDirectory.resolve("captured-before-replacement"));
      Files.createSymbolicLink(capturedParent, replacementParent);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }

    AttestationKeyFilePublicationDurabilityException restored = deserialize(serialized);

    assertEquals(
        publication.publishedArtifactPath(), restored.publication().publishedArtifactPath());
    assertEquals(publication.retention(), restored.publication().retention());
  }

  private static AttestationKeyFilePublicationDurabilityException roundTrip(
      AttestationKeyFilePublicationDurabilityException exception)
      throws IOException, ClassNotFoundException {
    return deserialize(serialize(exception));
  }

  private static byte[] serialize(AttestationKeyFilePublicationDurabilityException exception)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    return bytes.toByteArray();
  }

  private static AttestationKeyFilePublicationDurabilityException deserialize(byte[] serialized)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return AttestationKeyFilePublicationDurabilityException.class.cast(input.readObject());
    }
  }
}
