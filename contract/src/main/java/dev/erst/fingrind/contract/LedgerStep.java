package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One executable step inside an AI-agent-authored ledger plan. */
public sealed interface LedgerStep
    permits LedgerStep.OpenBook,
        LedgerStep.DeclareAccount,
        LedgerStep.PreflightEntry,
        LedgerStep.PostEntry,
        LedgerStep.InspectBook,
        LedgerStep.ListAccounts,
        LedgerStep.GetPosting,
        LedgerStep.ListPostings,
        LedgerStep.AccountBalance,
        LedgerStep.Assert {
  /** Stable caller-supplied step identifier used for journal correlation. */
  LedgerStepId stepId();

  /** Canonical request and journal kind represented by this step. */
  LedgerStepKind kind();

  /** Nullable detail kind emitted alongside the step kind in execution journals. */
  default @Nullable LedgerAssertionKind detailKind() {
    return null;
  }

  /** Validates a step identifier. */
  static void requireStepId(LedgerStepId stepId) {
    Objects.requireNonNull(stepId, "stepId");
  }

  /** Initializes the selected book inside the plan transaction. */
  record OpenBook(LedgerStepId stepId) implements LedgerStep {
    /** Validates the step. */
    public OpenBook {
      requireStepId(stepId);
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.OPEN_BOOK;
    }
  }

  /** Declares or reactivates one account inside the plan transaction. */
  record DeclareAccount(LedgerStepId stepId, DeclareAccountCommand command) implements LedgerStep {
    /** Validates the step. */
    public DeclareAccount {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.DECLARE_ACCOUNT;
    }
  }

  /** Validates one posting request without committing it. */
  record PreflightEntry(LedgerStepId stepId, PostEntryCommand command) implements LedgerStep {
    /** Validates the step. */
    public PreflightEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.PREFLIGHT_ENTRY;
    }
  }

  /** Commits one posting request inside the plan transaction. */
  record PostEntry(LedgerStepId stepId, PostEntryCommand command) implements LedgerStep {
    /** Validates the step. */
    public PostEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.POST_ENTRY;
    }
  }

  /** Inspects the selected book. */
  record InspectBook(LedgerStepId stepId) implements LedgerStep {
    /** Validates the step. */
    public InspectBook {
      requireStepId(stepId);
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.INSPECT_BOOK;
    }
  }

  /** Lists declared accounts. */
  record ListAccounts(LedgerStepId stepId, ListAccountsQuery query) implements LedgerStep {
    /** Validates the step. */
    public ListAccounts {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.LIST_ACCOUNTS;
    }
  }

  /** Gets one committed posting. */
  record GetPosting(LedgerStepId stepId, PostingId postingId) implements LedgerStep {
    /** Validates the step. */
    public GetPosting {
      requireStepId(stepId);
      Objects.requireNonNull(postingId, "postingId");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.GET_POSTING;
    }
  }

  /** Lists committed postings. */
  record ListPostings(LedgerStepId stepId, ListPostingsQuery query) implements LedgerStep {
    /** Validates the step. */
    public ListPostings {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.LIST_POSTINGS;
    }
  }

  /** Computes one account balance. */
  record AccountBalance(LedgerStepId stepId, AccountBalanceQuery query) implements LedgerStep {
    /** Validates the step. */
    public AccountBalance {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.ACCOUNT_BALANCE;
    }
  }

  /** Evaluates one first-class ledger assertion. */
  record Assert(LedgerStepId stepId, LedgerAssertion assertion) implements LedgerStep {
    /** Validates the step. */
    public Assert {
      requireStepId(stepId);
      Objects.requireNonNull(assertion, "assertion");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.ASSERT;
    }

    @Override
    public LedgerAssertionKind detailKind() {
      return assertion.kind();
    }
  }
}
