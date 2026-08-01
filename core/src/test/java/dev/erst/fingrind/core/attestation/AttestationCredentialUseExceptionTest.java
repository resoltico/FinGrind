package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies credential-use refusals retain their actionable path across Java serialization. */
class AttestationCredentialUseExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedFailureRestoresCanonicalCredentialPathAndCause() throws Exception {
    Path credentialPath =
        temporaryDirectory.resolve("nested").resolve("..").resolve("founder.fgatk");
    AttestationCredentialUseException failure =
        new AttestationCredentialUseException(
            credentialPath, "The founder credential cannot sign.", new IOException("read failed"));

    assertEquals(credentialPath.toAbsolutePath().normalize(), failure.credentialPath());
    AttestationCredentialUseException restored = roundTrip(failure);

    assertEquals(credentialPath.toAbsolutePath().normalize(), restored.credentialPath());
    assertEquals("The founder credential cannot sign.", restored.getMessage());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  @Test
  void serializedIdentityMismatchRetainsItsCredentialPathWithoutInventingACause() throws Exception {
    Path credentialPath = temporaryDirectory.resolve("identity-mismatch.fgatk");
    AttestationCredentialUseException failure =
        new AttestationCredentialUseException(
            credentialPath, "The founder credential does not match its declared identity.");

    assertEquals(credentialPath.toAbsolutePath().normalize(), failure.credentialPath());
    AttestationCredentialUseException restored = roundTrip(failure);

    assertEquals(credentialPath.toAbsolutePath().normalize(), restored.credentialPath());
    assertNull(restored.getCause());
  }

  private static AttestationCredentialUseException roundTrip(
      AttestationCredentialUseException exception) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return AttestationCredentialUseException.class.cast(input.readObject());
    }
  }
}
