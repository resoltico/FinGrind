package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** One admitted attested write: its pre-admission head and transaction ownership. */
record SqliteAttestedWriteAdmission(
    SqliteAttestationEvidenceStore.ObservedHead observedHead,
    SqliteTransactionOwnership transactionOwnership) {
  SqliteAttestedWriteAdmission {
    Objects.requireNonNull(observedHead, "observedHead");
    Objects.requireNonNull(transactionOwnership, "transactionOwnership");
  }
}
