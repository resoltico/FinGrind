package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies public key-file entry points preserve passphrase ownership and reject unsafe input. */
class AttestationKeyFilesTest extends AttestationKeyFileTestFixture {

  @Test
  void rejectsMalformedPassphraseFileBeforeCreatingAnEncryptedCredential() throws Exception {
    Path keyPath = temporaryDirectory.resolve("operator.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("operator.passphrase");
    Files.write(passphrasePath, new byte[] {(byte) 0xC3, (byte) 0x28});

    IllegalArgumentException rejection =
        assertThrows(
            IllegalArgumentException.class,
            () -> AttestationKeyFiles.create(keyPath, passphrasePath));

    assertEquals("Attestation passphrase file is not valid UTF-8.", rejection.getMessage());
    assertFalse(Files.exists(keyPath));
  }

  @Test
  void retainsCallerOwnedPassphraseWhenCreationRejectsTheOutputPath() throws Exception {
    char[] passphrase = "correct horse battery staple".toCharArray();
    Path nonDirectoryParent = temporaryDirectory.resolve("not-a-directory");
    Files.writeString(nonDirectoryParent, "not a directory");

    assertThrows(
        IOException.class,
        () -> AttestationKeyFiles.create(nonDirectoryParent.resolve("operator.fgatk"), passphrase));

    assertArrayEquals("correct horse battery staple".toCharArray(), passphrase);
  }
}
