package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Appends one authorized operation and owns the narrowly permitted backup retry. */
final class SqliteAttestedOperationAppender {
  private SqliteAttestedOperationAppender() {}

  static AttestationVerification append(
      SqliteVerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    return append(
        verifiedBook,
        operationKind,
        recordedAt,
        preimages,
        authorizer,
        backupAcknowledgement,
        null);
  }

  static AttestationVerification appendRegistryMutation(
      SqliteVerifiedBook verifiedBook,
      AttestationRegistryMutation mutation,
      Instant recordedAt,
      AttestationOperationAuthorizer authorizer) {
    AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
    return append(
        verifiedBook,
        checkedMutation.operationKind(),
        recordedAt,
        checkedMutation.preimages(),
        authorizer,
        null,
        checkedMutation);
  }

  private static AttestationVerification append(
      SqliteVerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement,
      @Nullable AttestationRegistryMutation registryMutation) {
    return retryStaleHead(
        retriesStaleHead(operationKind, backupAcknowledgement),
        () ->
            appendAttempt(
                verifiedBook,
                operationKind,
                recordedAt,
                preimages,
                authorizer,
                backupAcknowledgement,
                registryMutation));
  }

  static <T> T retryStaleHead(boolean retryStaleHead, StaleHeadRetryAttempt<T> attempt) {
    StaleHeadRetryAttempt<T> checkedAttempt = Objects.requireNonNull(attempt, "attempt");
    while (true) {
      try {
        return checkedAttempt.run();
      } catch (AttestationStaleHeadException exception) {
        if (!retryStaleHead) {
          throw exception;
        }
      }
    }
  }

  static boolean retriesStaleHead(
      AttestationOperationKind operationKind,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    return operationKind == AttestationOperationKind.BACKUP_CREATED
        && backupAcknowledgement != null;
  }

  /** Supplies one append attempt whose authenticated head may become stale. */
  @FunctionalInterface
  interface StaleHeadRetryAttempt<T> {
    /** Performs one append attempt. */
    T run();
  }

  private static AttestationVerification appendAttempt(
      SqliteVerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement,
      @Nullable AttestationRegistryMutation registryMutation) {
    try (SqliteBookPassphrase passphrase = verifiedBook.passphraseCopy();
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                verifiedBook.artifactPath(),
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      SqliteAttestationEvidenceStore.ObservedHead observedHead =
          SqliteAttestationEvidenceStore.observeRequired(database);
      database.executeStatement("begin immediate");
      try {
        AttestationVerification verification =
            registryMutation == null
                ? SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    observedHead,
                    operationKind,
                    recordedAt,
                    preimages,
                    authorizer,
                    backupAcknowledgement)
                : SqliteAttestationEvidenceStore.appendAuthorizedRegistryMutation(
                    database, observedHead, registryMutation, recordedAt, authorizer);
        database.executeStatement("commit");
        return verification;
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }
}
