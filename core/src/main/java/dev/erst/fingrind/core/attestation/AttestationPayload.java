package dev.erst.fingrind.core.attestation;

/** One canonical signed payload in the attestation format. */
sealed interface AttestationPayload
    permits AttestationOperationPayload,
        AttestationBackupManifestPayload,
        AttestationReceiptPayload {
  /** Returns the canonical payload bytes excluding the shared signature envelope. */
  byte[] encoded();

  /** Returns the received payload algorithm identifier without normalizing its wire value. */
  String algorithmId();
}
