package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

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
  String stepId();

  /** Canonical operation represented by this step. */
  OperationId operationId();

  /** Validates a step identifier. */
  static void requireStepId(String stepId) {
    Objects.requireNonNull(stepId, "stepId");
    if (stepId.isBlank()) {
      throw new IllegalArgumentException("Ledger stepId must not be blank.");
    }
  }

  /** Initializes the selected book inside the plan transaction. */
  record OpenBook(String stepId) implements LedgerStep {
    /** Validates the step. */
    public OpenBook {
      requireStepId(stepId);
    }

    @Override
    public OperationId operationId() {
      return OperationId.OPEN_BOOK;
    }
  }

  /** Declares or reactivates one account inside the plan transaction. */
  record DeclareAccount(String stepId, DeclareAccountCommand command) implements LedgerStep {
    /** Validates the step. */
    public DeclareAccount {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public OperationId operationId() {
      return OperationId.DECLARE_ACCOUNT;
    }
  }

  /** Validates one posting request without committing it. */
  record PreflightEntry(String stepId, PostEntryCommand command) implements LedgerStep {
    /** Validates the step. */
    public PreflightEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public OperationId operationId() {
      return OperationId.PREFLIGHT_ENTRY;
    }
  }

  /** Commits one posting request inside the plan transaction. */
  record PostEntry(String stepId, PostEntryCommand command) implements LedgerStep {
    /** Validates the step. */
    public PostEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }

    @Override
    public OperationId operationId() {
      return OperationId.POST_ENTRY;
    }
  }

  /** Inspects the selected book. */
  record InspectBook(String stepId) implements LedgerStep {
    /** Validates the step. */
    public InspectBook {
      requireStepId(stepId);
    }

    @Override
    public OperationId operationId() {
      return OperationId.INSPECT_BOOK;
    }
  }

  /** Lists declared accounts. */
  record ListAccounts(String stepId, ListAccountsQuery query) implements LedgerStep {
    /** Validates the step. */
    public ListAccounts {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public OperationId operationId() {
      return OperationId.LIST_ACCOUNTS;
    }
  }

  /** Gets one committed posting. */
  record GetPosting(String stepId, PostingId postingId) implements LedgerStep {
    /** Validates the step. */
    public GetPosting {
      requireStepId(stepId);
      Objects.requireNonNull(postingId, "postingId");
    }

    @Override
    public OperationId operationId() {
      return OperationId.GET_POSTING;
    }
  }

  /** Lists committed postings. */
  record ListPostings(String stepId, ListPostingsQuery query) implements LedgerStep {
    /** Validates the step. */
    public ListPostings {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public OperationId operationId() {
      return OperationId.LIST_POSTINGS;
    }
  }

  /** Computes one account balance. */
  record AccountBalance(String stepId, AccountBalanceQuery query) implements LedgerStep {
    /** Validates the step. */
    public AccountBalance {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }

    @Override
    public OperationId operationId() {
      return OperationId.ACCOUNT_BALANCE;
    }
  }

  /** Evaluates one first-class ledger assertion. */
  record Assert(String stepId, LedgerAssertion assertion) implements LedgerStep {
    /** Validates the step. */
    public Assert {
      requireStepId(stepId);
      Objects.requireNonNull(assertion, "assertion");
    }

    @Override
    public OperationId operationId() {
      return OperationId.EXECUTE_PLAN;
    }
  }
}
