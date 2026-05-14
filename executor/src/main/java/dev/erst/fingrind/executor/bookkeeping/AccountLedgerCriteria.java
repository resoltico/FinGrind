package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local bookkeeping criteria for one running account-ledger view. */
public record AccountLedgerCriteria(
    AccountCode accountCode,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage) {
  public AccountLedgerCriteria {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
  }

  /** Creates one unbounded account-ledger criteria object with explicit posting coverage. */
  public static AccountLedgerCriteria unbounded(
      AccountCode accountCode, PostingCoverage postingCoverage) {
    return new AccountLedgerCriteria(accountCode, EffectiveDateRange.unbounded(), postingCoverage);
  }

  /** Creates one unbounded account-ledger criteria object across all posting kinds. */
  public static AccountLedgerCriteria unbounded(AccountCode accountCode) {
    return unbounded(accountCode, PostingCoverage.ALL_POSTING_KINDS);
  }

  /** Convenience constructor that lifts nullable bounds into the shared-kernel date range. */
  public AccountLedgerCriteria(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      PostingCoverage postingCoverage) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), postingCoverage);
  }

  /** Convenience constructor that defaults to all posting kinds. */
  public AccountLedgerCriteria(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    this(
        accountCode,
        EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo),
        PostingCoverage.ALL_POSTING_KINDS);
  }
}
