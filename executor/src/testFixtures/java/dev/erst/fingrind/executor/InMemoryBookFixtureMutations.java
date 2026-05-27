package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Fixture-only account-state mutations for in-memory executor harnesses. */
public final class InMemoryBookFixtureMutations {
  private InMemoryBookFixtureMutations() {}

  /** Deactivates one declared account without widening the production store seam. */
  public static void deactivateAccount(InMemoryBookSession bookSession, AccountCode accountCode) {
    Objects.requireNonNull(bookSession, "bookSession");
    Objects.requireNonNull(accountCode, "accountCode");
    bookSession.deactivateAccount(accountCode);
  }
}
