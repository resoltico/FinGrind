package dev.erst.fingrind.contract.discovery;

import java.util.Objects;
import java.util.Set;

/** Canonical scaffold placeholder values that must be replaced before submission. */
public final class ScaffoldPlaceholders {
  public static final String EFFECTIVE_DATE = "replace-before-commit-effective-date";
  public static final String RECORDED_AT = "replace-before-commit-recorded-at";
  public static final String ACTOR_ID = "replace-before-commit-actor-id";
  public static final String COMMAND_ID = "replace-before-commit-command-id";
  public static final String IDEMPOTENCY_KEY = "replace-before-commit-idempotency-key";
  public static final String CAUSATION_ID = "replace-before-commit-causation-id";
  public static final String SOURCE_DOCUMENT_ID = "replace-before-commit-source-document-id";
  public static final String SOURCE_DOCUMENT_TYPE = "replace-before-commit-source-document-type";
  public static final String APPROVAL_ID = "replace-before-commit-approval-id";
  public static final String APPROVAL_TYPE = "replace-before-commit-approval-type";
  public static final String APPROVER_ID = "replace-before-commit-approver-id";
  public static final String TAX_REGISTRATION_ID = "replace-before-commit-tax-registration-id";
  public static final String TAX_JURISDICTION = "<ISO-3166-alpha-2>";
  public static final String TAX_REGISTRATION_NUMBER = "replace-before-commit-registration-number";
  public static final String OUTPUT_TAX_CODE = "replace-before-commit-output-tax-code";
  public static final String INPUT_TAX_CODE = "replace-before-commit-input-tax-code";

  private static final Set<String> RESERVED_VALUES =
      Set.of(
          EFFECTIVE_DATE,
          RECORDED_AT,
          ACTOR_ID,
          COMMAND_ID,
          IDEMPOTENCY_KEY,
          CAUSATION_ID,
          SOURCE_DOCUMENT_ID,
          SOURCE_DOCUMENT_TYPE,
          APPROVAL_ID,
          APPROVAL_TYPE,
          APPROVER_ID,
          TAX_REGISTRATION_ID,
          TAX_JURISDICTION,
          TAX_REGISTRATION_NUMBER,
          OUTPUT_TAX_CODE,
          INPUT_TAX_CODE);

  private ScaffoldPlaceholders() {}

  /** Returns whether the supplied value is a canonical unreplaced scaffold placeholder. */
  public static boolean isReserved(String value) {
    return RESERVED_VALUES.contains(Objects.requireNonNull(value, "value"));
  }
}
