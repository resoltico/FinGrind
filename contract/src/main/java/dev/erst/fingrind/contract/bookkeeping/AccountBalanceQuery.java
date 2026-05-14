package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Filter request for computing balances on one declared account. */
public record AccountBalanceQuery(
    AccountCode accountCode,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage) {
  /** Validates one account-balance query. */
  public AccountBalanceQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
  }

  /** Creates one unbounded balance query with explicit posting coverage. */
  public static AccountBalanceQuery unbounded(
      AccountCode accountCode, PostingCoverage postingCoverage) {
    return new AccountBalanceQuery(accountCode, EffectiveDateRange.unbounded(), postingCoverage);
  }

  /** Creates one unbounded balance query across all posting kinds. */
  public static AccountBalanceQuery unbounded(AccountCode accountCode) {
    return unbounded(accountCode, PostingCoverage.ALL_POSTING_KINDS);
  }

  /** Convenience constructor that lifts nullable bounds into a typed range. */
  public AccountBalanceQuery(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      PostingCoverage postingCoverage) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), postingCoverage);
  }

  /** Convenience constructor that defaults to all posting kinds. */
  public AccountBalanceQuery(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo) {
    this(
        accountCode,
        EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo),
        PostingCoverage.ALL_POSTING_KINDS);
  }

  /** Returns the optional lower effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateFrom() {
    return effectiveDateRange.effectiveDateFrom();
  }

  /** Returns the optional upper effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateTo() {
    return effectiveDateRange.effectiveDateTo();
  }
}
