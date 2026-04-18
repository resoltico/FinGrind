package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;

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

  /** Canonical request and journal kind represented by this step. */
  LedgerStepKind kind();

  /** Optional detail kind emitted alongside the step kind in execution journals. */
  default Optional<LedgerAssertionKind> detailKind() {
    return Optional.empty();
  }

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
    public LedgerStepKind kind() {
      return LedgerStepKind.OPEN_BOOK;
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
    public LedgerStepKind kind() {
      return LedgerStepKind.DECLARE_ACCOUNT;
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
    public LedgerStepKind kind() {
      return LedgerStepKind.PREFLIGHT_ENTRY;
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
    public LedgerStepKind kind() {
      return LedgerStepKind.POST_ENTRY;
    }
  }

  /** Inspects the selected book. */
  record InspectBook(String stepId) implements LedgerStep {
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
  record ListAccounts(String stepId, ListAccountsQuery query) implements LedgerStep {
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
  record GetPosting(String stepId, PostingId postingId) implements LedgerStep {
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
  record ListPostings(String stepId, ListPostingsQuery query) implements LedgerStep {
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
  record AccountBalance(String stepId, AccountBalanceQuery query) implements LedgerStep {
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
  record Assert(String stepId, LedgerAssertion assertion) implements LedgerStep {
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
    public Optional<LedgerAssertionKind> detailKind() {
      return Optional.of(assertion.kind());
    }
  }
}
