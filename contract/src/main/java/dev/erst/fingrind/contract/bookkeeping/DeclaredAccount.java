package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Objects;

/** One account currently declared in a book-local registry. */
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    boolean active,
    Instant declaredAt) {
  /** Validates one declared-account snapshot. */
  public DeclaredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    Objects.requireNonNull(declaredAt, "declaredAt");
    AccountSemantics.validate(accountType, accountRole);
  }

  /** Returns the doctrinal journal side that increases this account. */
  public NormalBalance normalBalance() {
    return AccountSemantics.normalBalance(accountType, accountRole);
  }
}
