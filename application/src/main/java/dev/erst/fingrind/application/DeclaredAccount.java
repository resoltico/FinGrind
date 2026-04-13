package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Objects;

/** One account currently declared in a book-local registry. */
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt) {
  /** Validates one declared-account snapshot. */
  public DeclaredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(normalBalance, "normalBalance");
    Objects.requireNonNull(declaredAt, "declaredAt");
  }
}
