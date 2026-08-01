package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable public verification failures for protected-book maintenance workflows. */
public enum BookMaintenanceVerificationFailure implements WireValue {
  MISSING,
  BLANK_SQLITE,
  FOREIGN_SQLITE,
  INCOMPLETE_FINGRIND,
  PROTECTED_BOOK_VERIFICATION_FAILED;

  @Override
  public String wireValue() {
    return switch (this) {
      case MISSING -> "missing";
      case BLANK_SQLITE -> "blank-sqlite";
      case FOREIGN_SQLITE -> "foreign-sqlite";
      case INCOMPLETE_FINGRIND -> "incomplete-fingrind";
      case PROTECTED_BOOK_VERIFICATION_FAILED -> "protected-book-verification-failed";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMaintenanceVerificationFailure.class);
  }
}
