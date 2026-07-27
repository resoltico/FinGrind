package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationKeyFileDestinationOccupiedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the founder lifecycle collision preserves its typed core cause across serialization. */
class AttestationFounderKeyTargetOccupiedExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedFounderCollisionRetainsTheCanonicalTargetAndTypedCauseChain() throws Exception {
    Path keyFilePath = temporaryDirectory.resolve("founder.fgatk").toAbsolutePath().normalize();
    ArtifactPublicationRetention retainedStage =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-stage"));
    AttestationKeyFileDestinationOccupiedException coreCollision =
        new AttestationKeyFileDestinationOccupiedException(
            keyFilePath, retainedStage, new FileAlreadyExistsException(keyFilePath.toString()));

    AttestationFounderKeyTargetOccupiedException original =
        new AttestationFounderKeyTargetOccupiedException(keyFilePath, coreCollision);
    assertEquals(keyFilePath, original.keyFilePath());
    AttestationFounderKeyTargetOccupiedException restored = roundTrip(original);

    assertEquals(keyFilePath, restored.keyFilePath());
    AttestationKeyFileDestinationOccupiedException restoredCoreCollision =
        assertInstanceOf(AttestationKeyFileDestinationOccupiedException.class, restored.getCause());
    assertEquals(keyFilePath, restoredCoreCollision.keyFilePath());
    assertEquals(retainedStage, restoredCoreCollision.retainedStage());
    assertInstanceOf(FileAlreadyExistsException.class, restoredCoreCollision.getCause());
  }

  private static AttestationFounderKeyTargetOccupiedException roundTrip(
      AttestationFounderKeyTargetOccupiedException exception)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return AttestationFounderKeyTargetOccupiedException.class.cast(input.readObject());
    }
  }
}
