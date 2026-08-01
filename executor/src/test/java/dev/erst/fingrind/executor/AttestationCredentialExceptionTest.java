package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.attestation.AttestationCredentialUseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies executor credential refusals retain nested credential-use facts after serialization. */
class AttestationCredentialExceptionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void serializedFailureRestoresCanonicalPathAndNestedCredentialUseFailure() throws Exception {
    Path credentialPath =
        temporaryDirectory.resolve("nested").resolve("..").resolve("founder.fgatk");
    AttestationCredentialException failure =
        new AttestationCredentialException(
            credentialPath,
            new AttestationCredentialUseException(
                credentialPath,
                "The founder credential cannot sign.",
                new IOException("read failed")));

    assertEquals(credentialPath.toAbsolutePath().normalize(), failure.credentialPath());
    AttestationCredentialException restored = roundTrip(failure);

    assertEquals(credentialPath.toAbsolutePath().normalize(), restored.credentialPath());
    AttestationCredentialUseException credentialUseFailure =
        assertInstanceOf(AttestationCredentialUseException.class, restored.getCause());
    assertEquals(
        credentialPath.toAbsolutePath().normalize(), credentialUseFailure.credentialPath());
    assertInstanceOf(IOException.class, credentialUseFailure.getCause());
  }

  private static AttestationCredentialException roundTrip(AttestationCredentialException exception)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return AttestationCredentialException.class.cast(input.readObject());
    }
  }
}
