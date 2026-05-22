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
  public static final String STORAGE_LOCATOR = "replace-before-commit-storage-locator";
  public static final String CONTENT_SHA256 = "replace-before-commit-content-sha256";
  public static final String APPROVAL_ID = "replace-before-commit-approval-id";
  public static final String APPROVAL_TYPE = "replace-before-commit-approval-type";
  public static final String APPROVER_ID = "replace-before-commit-approver-id";

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
          STORAGE_LOCATOR,
          CONTENT_SHA256,
          APPROVAL_ID,
          APPROVAL_TYPE,
          APPROVER_ID);

  private ScaffoldPlaceholders() {}

  /** Returns whether the supplied value is a canonical unreplaced scaffold placeholder. */
  public static boolean isReserved(String value) {
    return RESERVED_VALUES.contains(Objects.requireNonNull(value, "value"));
  }
}
