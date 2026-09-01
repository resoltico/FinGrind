package dev.erst.fingrind.core;

/** Plain-language display name for one declared ledger account. */
public record AccountName(String value) {
  /** Validates an account name without imposing jurisdiction-specific vocabulary. */
  public AccountName {
    value = CanonicalDisplayText.require(value, "Account name");
  }

  /** Reads an existing durable display name without restoring unsafe terminal control bytes. */
  public static AccountName fromPersisted(String value) {
    return new AccountName(CanonicalDisplayText.sanitizePersisted(value));
  }
}
