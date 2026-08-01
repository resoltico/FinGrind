package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.ExecutorAccountingTestSupport;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the exact live-chain address and source-state binding required for backup resumption. */
class AttestedProtectedBookBackupAcknowledgementWorkflowTest {
  private static final UUID BOOK_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000002");
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-23T00:00:00Z");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void acceptsOnlyAddressableSourceOrdersAndExactManifestSourceBindings() {
    AttestationVerification verification =
        new AttestationVerification(
            BOOK_ID, BigInteger.ZERO, new byte[32], new byte[32], List.of());

    assertTrue(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.ZERO, 1));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.ZERO, 0));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceOrderIsAddressable(
            BigInteger.valueOf(Long.MAX_VALUE), 1));

    assertTrue(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ZERO, new byte[32]));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification,
            UUID.fromString("018f0000-0000-7000-8000-000000000002"),
            BigInteger.ZERO,
            new byte[32]));
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ONE, new byte[32]));
    byte[] differentHead = new byte[32];
    differentHead[0] = 1;
    assertFalse(
        AttestedProtectedBookBackupAcknowledgementWorkflow.sourceVerificationMatchesArtifact(
            verification, BOOK_ID, BigInteger.ZERO, differentHead));
  }

  @Test
  void rejectsABackupArtifactWhoseSourceOrderCannotAddressTheCurrentLiveChain() throws IOException {
    Path keyPath = temporaryDirectory.resolve("founder.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("founder.passphrase");
    Files.writeString(passphrasePath, "test attestation passphrase\n");
    AttestationKeyFiles.create(keyPath, passphrasePath);
    AttestationCredentialSource credential =
        new AttestationCredentialSource(
            AttestationCustodian.FILE_PKCS8, PRINCIPAL_ID, keyPath, passphrasePath);
    AttestationEvidence genesis =
        AttestationGenesisFactory.prepare(
                ExecutorAccountingTestSupport.bookIdentity(),
                RECORDED_AT,
                List.of(
                    new AttestationFounderInput(
                        AttestationCustodian.FILE_PKCS8, PRINCIPAL_ID, keyPath, passphrasePath)))
            .evidence();
    AttestationVerification genesisVerification = AttestationVerifier.verifyBook(List.of(genesis));

    try (AttestationSigningSession session = AttestationSigningSession.open(List.of(credential))) {
      byte[] artifact =
          session.createBackupArtifact(
              new byte[] {1, 2, 3},
              genesisVerification.bookId(),
              BACKUP_ID,
              genesisVerification.headOrder(),
              genesisVerification.operationHead());

      assertFalse(
          AttestedProtectedBookBackupAcknowledgementWorkflow.artifactSourceIsLive(
              AttestationBackupArtifact.verify(artifact, ignored -> List.of(genesis)), List.of()));
    }
  }
}
