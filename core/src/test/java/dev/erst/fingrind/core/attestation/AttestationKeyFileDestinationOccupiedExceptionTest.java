package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies an admitted key-target collision remains actionable across exception serialization. */
class AttestationKeyFileDestinationOccupiedExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedCollisionRetainsItsCapturedCanonicalTargetStageAndCause() throws Exception {
    Path keyFilePath = temporaryDirectory.resolve("founder.fgatk").toAbsolutePath().normalize();
    Path retainedStagePath =
        temporaryDirectory.resolve(".founder.fgatk-stage.tmp").toAbsolutePath().normalize();
    AttestationKeyFileDestinationOccupiedException restored =
        roundTrip(
            new AttestationKeyFileDestinationOccupiedException(
                keyFilePath,
                new ArtifactPublicationRetention(retainedStagePath),
                new FileAlreadyExistsException(keyFilePath.toString())));

    assertEquals(keyFilePath, restored.keyFilePath());
    assertEquals(retainedStagePath, restored.retainedStage().retainedStagePath());
    assertInstanceOf(FileAlreadyExistsException.class, restored.getCause());
  }

  private static AttestationKeyFileDestinationOccupiedException roundTrip(
      AttestationKeyFileDestinationOccupiedException exception)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return AttestationKeyFileDestinationOccupiedException.class.cast(input.readObject());
    }
  }
}
