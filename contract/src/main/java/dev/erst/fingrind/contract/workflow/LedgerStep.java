package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One executable step inside an AI-agent-authored ledger plan. */
public sealed interface LedgerStep
    permits LedgerStep.EnsureBook,
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

  /** Canonical journal-visible step identity emitted for this plan step. */
  default LedgerJournalStep journalStep() {
    return LedgerJournalStep.standard(kind());
  }

  /**
   * Returns the nested assertion kind emitted for assertion steps, or {@code null} for every
   * non-assert ledger step.
   */
  default @Nullable LedgerAssertionKind detailKind() {
    return journalStep().detailKind();
  }

  /** Validates a step identifier. */
  static void requireStepId(LedgerStepId stepId) {
    Objects.requireNonNull(stepId, "stepId");
  }

  /** Ensures the selected book is initialized inside the plan transaction. */
  record EnsureBook(LedgerStepId stepId, OpenBookCommand command) implements LedgerStep {
    /** Validates the step. */
    public EnsureBook {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.ENSURE_BOOK;
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

  /**
   * Commits one typed business-entry request or one raw direct-journal fallback request inside the
   * plan transaction.
   */
  record PostEntry(LedgerStepId stepId, PostEntryCommand command) implements LedgerStep {
    /** Validates the step. */
    public PostEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public LedgerStepKind kind() {
      return LedgerStepKind.forCommittedEntryKind(command.entry().entryKind());
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
    public LedgerJournalStep journalStep() {
      return LedgerJournalStep.assertion(assertion.kind());
    }
  }
}
