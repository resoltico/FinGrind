package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves PDF durability failures preserve complete serializable publication evidence. */
class CliPdfPublicationDurabilityExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedDurabilityFailureRestoresTheFinalArtifactAndItsRetainedStage() throws Exception {
    ArtifactPublicationResult publication = publication("published.pdf", ".published-stage.tmp");

    CliPdfPublicationDurabilityException restored =
        roundTrip(
            new CliPdfPublicationDurabilityException(
                publication, new IOException("simulated directory-force failure")));

    assertEquals(
        publication.publishedArtifactPath(), restored.publication().publishedArtifactPath());
    assertEquals(
        publication.retention().retainedStagePath(),
        restored.publication().retention().retainedStagePath());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void serializedDurabilityFailureRestoresCapturedPathsAfterItsParentIsReplaced() throws Exception {
    Path capturedParent = temporaryDirectory.resolve("captured-parent");
    Path replacementParent = temporaryDirectory.resolve("replacement-parent");
    Files.createDirectory(capturedParent);
    Files.createDirectory(replacementParent);
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(
            capturedParent.resolve("published.pdf"),
            new ArtifactPublicationRetention(capturedParent.resolve(".published-stage.tmp")));
    byte[] serialized =
        serialize(
            new CliPdfPublicationDurabilityException(
                publication, new IOException("simulated directory-force failure")));

    Files.delete(capturedParent);
    Files.createSymbolicLink(capturedParent, replacementParent);

    CliPdfPublicationDurabilityException restored = deserialize(serialized);

    assertEquals(
        publication.publishedArtifactPath(), restored.publication().publishedArtifactPath());
    assertEquals(
        publication.retention().retainedStagePath(),
        restored.publication().retention().retainedStagePath());
    assertNotEquals(
        replacementParent.resolve("published.pdf"), restored.publication().publishedArtifactPath());
    assertNotEquals(
        replacementParent.resolve(".published-stage.tmp"),
        restored.publication().retention().retainedStagePath());
  }

  @Test
  void serializedPublicationRequiresBothMandatoryPublicationPaths() {
    String publishedPath = temporaryDirectory.resolve("published.pdf").toString();
    String retainedStagePath = temporaryDirectory.resolve(".published-stage.tmp").toString();

    assertThrows(
        NullPointerException.class,
        () ->
            new CliPdfPublicationDurabilityException.SerializedPublication(
                nullOf(), retainedStagePath));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliPdfPublicationDurabilityException.SerializedPublication(
                publishedPath, nullOf()));
  }

  private ArtifactPublicationResult publication(String finalName, String retainedStageName)
      throws IOException {
    Path publishedArtifact = temporaryDirectory.resolve(finalName);
    Path retainedStage = temporaryDirectory.resolve(retainedStageName);
    Files.writeString(publishedArtifact, "%PDF-published");
    Files.writeString(retainedStage, "%PDF-retained");
    return new ArtifactPublicationResult(
        publishedArtifact, new ArtifactPublicationRetention(retainedStage));
  }

  private static CliPdfPublicationDurabilityException roundTrip(
      CliPdfPublicationDurabilityException exception) throws IOException, ClassNotFoundException {
    return deserialize(serialize(exception));
  }

  private static byte[] serialize(CliPdfPublicationDurabilityException exception)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    return bytes.toByteArray();
  }

  private static CliPdfPublicationDurabilityException deserialize(byte[] serialized)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return CliPdfPublicationDurabilityException.class.cast(input.readObject());
    }
  }
}
