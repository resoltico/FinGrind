package dev.erst.fingrind.contract.bookkeeping;

/** Exact public response boundaries at which an attestation failure can be emitted. */
enum AttestationDiagnosticReachability {
  /** Reaches every live operation-admission and historical chain-verification surface. */
  OPERATION_EVIDENCE(true, true, true, true),

  /** Reaches ordinary live admission when a backup manifest is being authorized. */
  MANIFEST_ADMISSION(true, false, false, false),

  /** Reaches ordinary live admission and receipt verification for receipt evidence. */
  RECEIPT_ADMISSION(true, false, false, true),

  /** Reaches receipt verification only when the selected receipt artifact cannot be decoded. */
  RECEIPT_ARTIFACT(false, false, false, true);

  private final boolean ordinaryLiveAdmission;
  private final boolean operationAdmission;
  private final boolean bookChainVerification;
  private final boolean receiptVerification;

  AttestationDiagnosticReachability(
      boolean ordinaryLiveAdmission,
      boolean operationAdmission,
      boolean bookChainVerification,
      boolean receiptVerification) {
    this.ordinaryLiveAdmission = ordinaryLiveAdmission;
    this.operationAdmission = operationAdmission;
    this.bookChainVerification = bookChainVerification;
    this.receiptVerification = receiptVerification;
  }

  boolean ordinaryLiveAdmission() {
    return ordinaryLiveAdmission;
  }

  boolean operationAdmission() {
    return operationAdmission;
  }

  boolean bookChainVerification() {
    return bookChainVerification;
  }

  boolean receiptVerification() {
    return receiptVerification;
  }
}
