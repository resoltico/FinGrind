package dev.erst.fingrind.contract;

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
  /** Asserts that an account exists in the selected book. */
  record AccountDeclared(AccountCode accountCode) implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountDeclared {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Asserts that an account exists and is active. */
  record AccountActive(AccountCode accountCode) implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountActive {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Asserts that a committed posting exists. */
  record PostingExists(PostingId postingId) implements LedgerAssertion {
    /** Validates the assertion. */
    public PostingExists {
      Objects.requireNonNull(postingId, "postingId");
    }
  }

  /** Asserts that one account balance bucket equals the expected net amount and side. */
  record AccountBalanceEquals(
      AccountCode accountCode,
      Optional<LocalDate> effectiveDateFrom,
      Optional<LocalDate> effectiveDateTo,
      Money netAmount,
      NormalBalance balanceSide)
      implements LedgerAssertion {
    /** Validates the assertion. */
    public AccountBalanceEquals {
      Objects.requireNonNull(accountCode, "accountCode");
      effectiveDateFrom = effectiveDateFrom == null ? Optional.empty() : effectiveDateFrom;
      effectiveDateTo = effectiveDateTo == null ? Optional.empty() : effectiveDateTo;
      Objects.requireNonNull(netAmount, "netAmount");
      Objects.requireNonNull(balanceSide, "balanceSide");
      if (effectiveDateFrom.isPresent()
          && effectiveDateTo.isPresent()
          && effectiveDateFrom.orElseThrow().isAfter(effectiveDateTo.orElseThrow())) {
        throw new IllegalArgumentException(
            "Ledger assertion effectiveDateFrom must be on or before effectiveDateTo.");
      }
    }

    /** Converts this assertion to the query required to evaluate it. */
    public AccountBalanceQuery query() {
      return new AccountBalanceQuery(accountCode, effectiveDateFrom, effectiveDateTo);
    }
  }
}
