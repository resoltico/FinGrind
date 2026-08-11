package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleRecoveryEvidenceVerifier;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Independently proves that a completed pair's final book matches its recovery operation. */
final class AttestedProtectedBookPairPublicationRecovery {
  private final AttestedProtectedBookMaintenanceStore store;

  AttestedProtectedBookPairPublicationRecovery(AttestedProtectedBookMaintenanceStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  MaintenanceDecision<ProtectedBookRekeyOutcome> recoverRekey(
      Path bookPath, Path newKeyPath, ProtectedBookPairPublication publication) {
    ProtectedBookAccess recoveredAccess =
        new ProtectedBookAccess(bookPath, new ProtectedBookPassphraseSource.KeyFile(newKeyPath));
    try (ProtectedBookMaintenanceStore.VerifiedBook recoveredBook =
        AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, recoveredAccess)) {
      List<AttestationEvidence> evidence = store.loadAttestationEvidence(recoveredBook);
      AttestationVerification verification =
          AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(evidence, bookPath);
      byte[] recoveredHead = verification.operationHead();
      try {
        if (!AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
            evidence, verification.headOrder(), recoveredHead)) {
          throw new IllegalStateException(
              "Recovered publication journal does not match the final rekey attestation head.");
        }
        return MaintenanceDecision.accepted(
            new ProtectedBookRekeyOutcome.Rekeyed(
                bookPath,
                newKeyPath,
                new AttestationCommit(
                    verification.headOrder(), HexFormat.of().formatHex(recoveredHead)),
                ProtectedBookPairPublicationCompletion.RECOVERED,
                publication));
      } finally {
        Arrays.fill(recoveredHead, (byte) 0);
      }
    }
  }

  MaintenanceDecision<ProtectedBookRestoreOutcome> recoverRestore(
      Path bookPath,
      Path newKeyPath,
      AttestationBackupAcknowledgement acknowledgement,
      ProtectedBookPairPublication publication) {
    ProtectedBookAccess recoveredAccess =
        new ProtectedBookAccess(bookPath, new ProtectedBookPassphraseSource.KeyFile(newKeyPath));
    try (ProtectedBookMaintenanceStore.VerifiedBook recoveredBook =
        AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, recoveredAccess)) {
      List<AttestationEvidence> evidence = store.loadAttestationEvidence(recoveredBook);
      AttestationVerification verification =
          AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(evidence, bookPath);
      byte[] head = verification.operationHead();
      try {
        if (!AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
            evidence, acknowledgement, verification.headOrder(), head)) {
          throw new IllegalStateException(
              "Recovered publication journal does not match the final restore attestation head.");
        }
        return MaintenanceDecision.accepted(
            new ProtectedBookRestoreOutcome.Restored(
                bookPath,
                newKeyPath,
                new AttestationCommit(verification.headOrder(), HexFormat.of().formatHex(head)),
                ProtectedBookPairPublicationCompletion.RECOVERED,
                publication));
      } finally {
        Arrays.fill(head, (byte) 0);
      }
    }
  }
}
