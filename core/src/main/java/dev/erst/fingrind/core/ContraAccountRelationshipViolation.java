package dev.erst.fingrind.core;

import java.util.List;

/** Stable reasons a declared contra-account relationship cannot preserve chart meaning. */
public enum ContraAccountRelationshipViolation implements WireValue {
  SELF_REFERENCE,
  TARGET_MISSING,
  TARGET_INACTIVE,
  TARGET_NOT_POSTABLE,
  TARGET_IS_CONTRA,
  ACCOUNT_TYPE_MISMATCH,
  STATEMENT_TAXONOMY_MISMATCH;

  /** Returns the stable wire value. */
  @Override
  public String wireValue() {
    return switch (this) {
      case SELF_REFERENCE -> "self-reference";
      case TARGET_MISSING -> "target-missing";
      case TARGET_INACTIVE -> "target-inactive";
      case TARGET_NOT_POSTABLE -> "target-not-postable";
      case TARGET_IS_CONTRA -> "target-is-contra";
      case ACCOUNT_TYPE_MISMATCH -> "account-type-mismatch";
      case STATEMENT_TAXONOMY_MISMATCH -> "statement-taxonomy-mismatch";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ContraAccountRelationshipViolation.class);
  }
}
