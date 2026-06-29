package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical policy owner for the current FinGrind chart-of-accounts code semantics. */
public final class AccountCodePolicy {
  /** Current semantic meaning of one declared account code. */
  public enum Meaning {
    OPAQUE_BOOK_LOCAL_IDENTIFIER
  }

  /** Current chart-of-accounts structure supported by FinGrind. */
  public enum ChartStructure {
    PARENT_CHILD_HIERARCHY
  }

  private AccountCodePolicy() {}

  /** Returns the canonical meaning of one FinGrind account code. */
  public static Meaning meaning() {
    return Meaning.OPAQUE_BOOK_LOCAL_IDENTIFIER;
  }

  /** Returns the canonical chart structure supported by current FinGrind books. */
  public static ChartStructure chartStructure() {
    return ChartStructure.PARENT_CHILD_HIERARCHY;
  }

  /**
   * Validates one account declaration against the current policy.
   *
   * <p>Current FinGrind books intentionally treat account codes as opaque book-local identifiers
   * rather than type-carrying ranges, so every semantic restriction remains local to explicit
   * taxonomy metadata instead of being inferred from the code text.
   */
  public static void validate(
      AccountCode accountCode, AccountType accountType, AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
  }
}
