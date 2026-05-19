package dev.erst.fingrind.executor.spi;

/** Local verification failures for protected-book maintenance workflows. */
public enum ProtectedBookMaintenanceVerificationFailure {
  MISSING,
  BLANK_SQLITE,
  FOREIGN_SQLITE,
  UNSUPPORTED_FORMAT_VERSION,
  INCOMPLETE_FINGRIND,
  PROTECTED_BOOK_VERIFICATION_FAILED
}
