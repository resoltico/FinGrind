package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Folds the verified protected-book lifecycle facts that must remain sequential across the chain.
 */
final class AttestationLifecycleHistory {
  private static final BigInteger INITIAL_KEY_EPOCH = BigInteger.ONE;
  private static final BigInteger FIRST_REKEY_EPOCH = INITIAL_KEY_EPOCH.add(BigInteger.ONE);

  private final BigInteger nextRekeyEpoch;
  private final Set<UUID> acknowledgedBackupIds;

  private AttestationLifecycleHistory(BigInteger nextRekeyEpoch, Set<UUID> acknowledgedBackupIds) {
    this.nextRekeyEpoch = Objects.requireNonNull(nextRekeyEpoch, "nextRekeyEpoch");
    this.acknowledgedBackupIds = Set.copyOf(acknowledgedBackupIds);
  }

  static AttestationLifecycleHistory genesis() {
    return new AttestationLifecycleHistory(FIRST_REKEY_EPOCH, Set.of());
  }

  static BigInteger firstRekeyEpoch() {
    return FIRST_REKEY_EPOCH;
  }

  AttestationLifecycleHistory accept(
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    return switch (Objects.requireNonNull(operationKind, "operationKind")) {
      case BACKUP_CREATED -> acceptBackup(requestPreimage);
      case REKEY_BOOK -> acceptRekey(effectPreimage);
      default -> this;
    };
  }

  BigInteger nextRekeyEpoch() {
    return nextRekeyEpoch;
  }

  private AttestationLifecycleHistory acceptBackup(AttestationPreimage requestPreimage) {
    Set<UUID> nextBackupIds = new HashSet<>(acknowledgedBackupIds);
    if (!nextBackupIds.add(AttestationLifecycleEffectProfile.backupId(requestPreimage))) {
      throw AttestationOperationProfile.failure();
    }
    return new AttestationLifecycleHistory(nextRekeyEpoch, nextBackupIds);
  }

  private AttestationLifecycleHistory acceptRekey(AttestationPreimage effectPreimage) {
    BigInteger actualEpoch = AttestationLifecycleEffectProfile.rekeyEpoch(effectPreimage);
    if (!actualEpoch.equals(nextRekeyEpoch)) {
      throw AttestationOperationProfile.failure();
    }
    return new AttestationLifecycleHistory(
        nextRekeyEpoch.add(BigInteger.ONE), acknowledgedBackupIds);
  }
}
