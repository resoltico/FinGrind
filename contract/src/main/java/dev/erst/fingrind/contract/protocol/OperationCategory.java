package dev.erst.fingrind.contract.protocol;

/** Top-level operation groups published by the FinGrind protocol catalog. */
public enum OperationCategory {
  /** Discovery operations that do not access a book. */
  DISCOVERY,
  /** Book administration operations that mutate lifecycle or account-registry state. */
  ADMINISTRATION,
  /** Read-side operations that inspect book state without mutating it. */
  QUERY,
  /** Write-side posting operations. */
  WRITE
}
