package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal workflow assertion family for agent-authored book plans. */
public sealed interface BookWorkflowAssertion
    permits BookWorkflowAssertion.AccountDeclared,
        BookWorkflowAssertion.AccountActive,
        BookWorkflowAssertion.PostingExists,
        BookWorkflowAssertion.AccountBalanceEquals {
  /** Asserts that one account exists in the selected book. */
  record AccountDeclared(AccountCode accountCode) implements BookWorkflowAssertion {
    /** Validates the assertion. */
    public AccountDeclared {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Asserts that one account exists and is active. */
  record AccountActive(AccountCode accountCode) implements BookWorkflowAssertion {
    /** Validates the assertion. */
    public AccountActive {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Asserts that one durable posting exists. */
  record PostingExists(PostingId postingId) implements BookWorkflowAssertion {
    /** Validates the assertion. */
    public PostingExists {
      Objects.requireNonNull(postingId, "postingId");
    }
  }

  /** Asserts one grouped balance snapshot for an account/date range/currency. */
  record AccountBalanceEquals(
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      Money netAmount,
      BalanceSide balanceSide)
      implements BookWorkflowAssertion {
    /** Validates the assertion. */
    public AccountBalanceEquals {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(netAmount, "netAmount");
      Objects.requireNonNull(balanceSide, "balanceSide");
    }

    /** Returns the bookkeeping balance query implied by this assertion. */
    public AccountBalanceQuery query() {
      return new AccountBalanceQuery(accountCode, effectiveDateFrom, effectiveDateTo);
    }
  }
}
