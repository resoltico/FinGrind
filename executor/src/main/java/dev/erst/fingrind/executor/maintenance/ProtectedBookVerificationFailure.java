package dev.erst.fingrind.executor.maintenance;

/** Closed verification-failure vocabulary for protected-book maintenance artifact checks. */
public enum ProtectedBookVerificationFailure {
  MISSING,
  BLANK_SQLITE,
  FOREIGN_SQLITE,
  UNSUPPORTED_FORMAT_VERSION,
  INCOMPLETE_FINGRIND,
  PROTECTED_BOOK_VERIFICATION_FAILED
}
