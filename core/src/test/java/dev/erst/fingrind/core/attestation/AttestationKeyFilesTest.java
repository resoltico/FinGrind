package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies public key-file entry points preserve passphrase ownership and reject unsafe input. */
class AttestationKeyFilesTest extends AttestationKeyFileTestFixture {

  @Test
  void validatesAWellFormedPassphraseFileWithoutCreatingAKeyArtifact() throws Exception {
    Path passphrasePath = temporaryDirectory.resolve("operator.passphrase");
    Files.writeString(passphrasePath, "correct horse battery staple\n");

    AttestationKeyFiles.validatePassphraseFile(passphrasePath);

    assertArrayEquals(
        "correct horse battery staple\n".getBytes(StandardCharsets.UTF_8),
        Files.readAllBytes(passphrasePath));
    assertFalse(Files.exists(temporaryDirectory.resolve("operator.fgatk")));
  }

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

  @Test
  void existingCredentialRefusesASymlinkedKeyBeforeItReadsThePassphraseSource() throws Exception {
    Path keyPath = temporaryDirectory.resolve("operator.fgatk");
    AttestationKeyFiles.create(keyPath, "correct horse battery staple".toCharArray());
    Path keyAlias = temporaryDirectory.resolve("operator-alias.fgatk");
    if (!createSymlink(keyAlias, keyPath)) {
      return;
    }

    assertThrows(
        IOException.class,
        () ->
            AttestationKeyFiles.openExistingCredential(
                UUID.randomUUID(), keyAlias, temporaryDirectory.resolve("missing.passphrase")));
  }

  @Test
  void passphraseSourcesRefuseASymlinkInsteadOfFollowingIt() throws Exception {
    Path keyPath = temporaryDirectory.resolve("operator.fgatk");
    AttestationKeyFiles.create(keyPath, "correct horse battery staple".toCharArray());
    Path passphraseTarget = temporaryDirectory.resolve("passphrase-target.txt");
    Files.writeString(passphraseTarget, "correct horse battery staple\n");
    Path passphraseAlias = temporaryDirectory.resolve("passphrase-alias.txt");
    if (!createSymlink(passphraseAlias, passphraseTarget)) {
      return;
    }

    assertThrows(
        IOException.class,
        () ->
            AttestationKeyFiles.openExistingCredential(
                UUID.randomUUID(), keyPath, passphraseAlias));
  }

  @Test
  void passphraseSourcesTranslateUnsupportedNofollowPrimitivesToIOException() {
    UnsupportedOperationException rejection =
        new UnsupportedOperationException("simulated unsupported nofollow passphrase primitive");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                AttestationKeyFiles.openPassphraseFileNoFollow(
                    temporaryDirectory.resolve("operator.passphrase"),
                    ignored -> {
                      throw rejection;
                    }));

    assertEquals(
        "The selected filesystem cannot enforce nofollow access for the attestation passphrase file.",
        failure.getMessage());
    assertSame(rejection, failure.getCause());
  }

  @Test
  void passphraseSourcesTranslateIllegalArgumentNofollowPrimitivesToIOException() {
    IllegalArgumentException rejection =
        new IllegalArgumentException("simulated invalid nofollow passphrase primitive");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                AttestationKeyFiles.openPassphraseFileNoFollow(
                    temporaryDirectory.resolve("operator.passphrase"),
                    ignored -> {
                      throw rejection;
                    }));

    assertEquals(
        "The selected filesystem cannot enforce nofollow access for the attestation passphrase file.",
        failure.getMessage());
    assertSame(rejection, failure.getCause());
  }

  private static boolean createSymlink(Path alias, Path target) throws IOException {
    try {
      Files.createSymbolicLink(alias, target);
      return true;
    } catch (UnsupportedOperationException | SecurityException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return false;
    } catch (IOException exception) {
      assumeTrue(false, "The test process cannot create symbolic-link fixtures.");
      return false;
    }
  }
}
