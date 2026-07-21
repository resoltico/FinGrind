package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationSigner;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Real encrypted-key signing support shared by SQLite integration fixtures. */
final class SqliteAttestationTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final KeyMaterial KEY_MATERIAL = KeyMaterial.create();

  private SqliteAttestationTestSupport() {}

  static AttestationEvidence genesis(BookIdentity bookIdentity, Instant initializedAt) {
    try (AttestationSigningCredential founder = KEY_MATERIAL.signingCredential()) {
      return AttestationGenesis.create(BOOK_ID, bookIdentity, initializedAt, List.of(founder));
    }
  }

  static AttestationOperationAuthorizer authorizer() {
    return request -> {
      try (AttestationSigningCredential signer = KEY_MATERIAL.signingCredential()) {
        return AttestationOperationSigner.sign(
            request.bookId(),
            request.operationOrder(),
            request.operationKind(),
            request.previousHead(),
            request.recordedAt(),
            request.requestPreimage(),
            request.effectPreimage(),
            List.of(signer));
      }
    };
  }

  static byte[] signedBackupArtifact(
      byte[] snapshot, AttestationVerification sourceVerification, java.util.UUID backupId) {
    try (AttestationSigningSession session =
        AttestationSigningSession.open(List.of(KEY_MATERIAL.credentialSource()))) {
      return session.createBackupArtifact(
          snapshot,
          sourceVerification.bookId(),
          backupId,
          sourceVerification.headOrder(),
          sourceVerification.operationHead());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not sign the SQLite test backup artifact.", exception);
    }
  }

  /** Owns the reusable encrypted founder credential fixture and its protected passphrase copy. */
  private static final class KeyMaterial {
    private final AttestationPublicCredential publicCredential;
    private final Path encryptedKeyPath;
    private final Path passphrasePath;
    private final char[] passphrase;

    private KeyMaterial(
        AttestationPublicCredential publicCredential,
        Path encryptedKeyPath,
        Path passphrasePath,
        char[] passphrase) {
      this.publicCredential = publicCredential;
      this.encryptedKeyPath = encryptedKeyPath;
      this.passphrasePath = passphrasePath;
      this.passphrase = passphrase.clone();
    }

    private static KeyMaterial create() {
      char[] passphrase = "sqlite-attestation-test-credential".toCharArray();
      try {
        Path directory = Files.createTempDirectory("fingrind-sqlite-attestation-");
        Path encryptedKeyPath = directory.resolve("founder.fgatk");
        Path passphrasePath = directory.resolve("founder.passphrase");
        directory.toFile().deleteOnExit();
        encryptedKeyPath.toFile().deleteOnExit();
        passphrasePath.toFile().deleteOnExit();
        Files.writeString(passphrasePath, String.valueOf(passphrase) + System.lineSeparator());
        return new KeyMaterial(
            AttestationKeyFiles.create(encryptedKeyPath, passphrase),
            encryptedKeyPath,
            passphrasePath,
            passphrase);
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Could not provision SQLite test attestation credential.", exception);
      } finally {
        java.util.Arrays.fill(passphrase, '\0');
      }
    }

    private AttestationSigningCredential signingCredential() {
      return new AttestationSigningCredential(
          PRINCIPAL_ID, publicCredential, encryptedKeyPath, passphrase);
    }

    private AttestationCredentialSource credentialSource() {
      return new AttestationCredentialSource(PRINCIPAL_ID, encryptedKeyPath, passphrasePath);
    }
  }
}
