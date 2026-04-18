package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** First-class assertions an AI agent can include in a ledger plan. */
public sealed interface LedgerAssertion
    permits LedgerAssertion.AccountDeclared,
        LedgerAssertion.AccountActive,
        LedgerAssertion.PostingExists,
        LedgerAssertion.AccountBalanceEquals {
  /** Returns the canonical assertion kind nested inside an `assert` ledger-plan step. */
  LedgerAssertionKind kind();

  /** Asserts that an account exists in the selected book. */
  record AccountDeclared(AccountCode accountCode) implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountDeclared {
      Objects.requireNonNull(accountCode, "accountCode");
    }

    @Override
    public LedgerAssertionKind kind() {
      return LedgerAssertionKind.ACCOUNT_DECLARED;
    }
  }

  /** Asserts that an account exists and is active. */
  record AccountActive(AccountCode accountCode) implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountActive {
      Objects.requireNonNull(accountCode, "accountCode");
    }

    @Override
    public LedgerAssertionKind kind() {
      return LedgerAssertionKind.ACCOUNT_ACTIVE;
    }
  }

  /** Asserts that a committed posting exists. */
  record PostingExists(PostingId postingId) implements LedgerAssertion {
    /** Validates the assertion. */
    public PostingExists {
      Objects.requireNonNull(postingId, "postingId");
    }

    @Override
    public LedgerAssertionKind kind() {
      return LedgerAssertionKind.POSTING_EXISTS;
    }
  }

  /** Asserts that one account balance bucket equals the expected net amount and side. */
  record AccountBalanceEquals(
      AccountCode accountCode,
      EffectiveDateRange effectiveDateRange,
      Money netAmount,
      NormalBalance balanceSide)
      implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountBalanceEquals {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
      Objects.requireNonNull(netAmount, "netAmount");
      Objects.requireNonNull(balanceSide, "balanceSide");
    }

    /** Compatibility constructor that lifts optional bounds into a typed range. */
    public AccountBalanceEquals(
        AccountCode accountCode,
        Optional<LocalDate> effectiveDateFrom,
        Optional<LocalDate> effectiveDateTo,
        Money netAmount,
        NormalBalance balanceSide) {
      this(
          accountCode,
          EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo),
          netAmount,
          balanceSide);
    }

    @Override
    public LedgerAssertionKind kind() {
      return LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS;
    }

    /** Returns the optional lower effective-date bound carried by this assertion. */
    public Optional<LocalDate> effectiveDateFrom() {
      return effectiveDateRange.effectiveDateFrom();
    }

    /** Returns the optional upper effective-date bound carried by this assertion. */
    public Optional<LocalDate> effectiveDateTo() {
      return effectiveDateRange.effectiveDateTo();
    }

    /** Converts this assertion to the query required to evaluate it. */
    public AccountBalanceQuery query() {
      return new AccountBalanceQuery(accountCode, effectiveDateRange);
    }
  }
}
