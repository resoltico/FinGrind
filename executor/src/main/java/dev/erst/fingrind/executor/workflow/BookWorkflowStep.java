package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.Objects;

/** Internal workflow step family for executing ordered book plans. */
public sealed interface BookWorkflowStep
    permits BookWorkflowStep.OpenBook,
        BookWorkflowStep.DeclareAccount,
        BookWorkflowStep.PreflightEntry,
        BookWorkflowStep.PostEntry,
        BookWorkflowStep.InspectBook,
        BookWorkflowStep.ListAccounts,
        BookWorkflowStep.GetPosting,
        BookWorkflowStep.ListPostings,
        BookWorkflowStep.AccountBalance,
        BookWorkflowStep.Assert {
  /** Stable caller-supplied step identifier. */
  BookWorkflowStepId stepId();

  /** Initializes the selected book inside the workflow transaction. */
  record OpenBook(BookWorkflowStepId stepId, BookIdentity bookIdentity)
      implements BookWorkflowStep {
    /** Validates the step. */
    public OpenBook {
      requireStepId(stepId);
      Objects.requireNonNull(bookIdentity, "bookIdentity");
    }
  }

  /** Declares or reactivates one account. */
  record DeclareAccount(BookWorkflowStepId stepId, AccountDeclaration command)
      implements BookWorkflowStep {
    /** Validates the step. */
    public DeclareAccount {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }
  }

  /** Validates one posting request without committing it. */
  record PreflightEntry(BookWorkflowStepId stepId, PostEntryCommand command)
      implements BookWorkflowStep {
    /** Validates the step. */
    public PreflightEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }
  }

  /** Commits one posting request. */
  record PostEntry(BookWorkflowStepId stepId, PostEntryCommand command)
      implements BookWorkflowStep {
    /** Validates the step. */
    public PostEntry {
      requireStepId(stepId);
      Objects.requireNonNull(command, "command");
    }
  }

  /** Inspects the selected book. */
  record InspectBook(BookWorkflowStepId stepId) implements BookWorkflowStep {
    /** Validates the step. */
    public InspectBook {
      requireStepId(stepId);
    }
  }

  /** Lists declared accounts. */
  record ListAccounts(BookWorkflowStepId stepId, AccountRegistryQuery query)
      implements BookWorkflowStep {
    /** Validates the step. */
    public ListAccounts {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }
  }

  /** Gets one committed posting. */
  record GetPosting(BookWorkflowStepId stepId, PostingId postingId) implements BookWorkflowStep {
    /** Validates the step. */
    public GetPosting {
      requireStepId(stepId);
      Objects.requireNonNull(postingId, "postingId");
    }
  }

  /** Lists committed postings. */
  record ListPostings(BookWorkflowStepId stepId, PostingHistoryQuery query)
      implements BookWorkflowStep {
    /** Validates the step. */
    public ListPostings {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }
  }

  /** Computes one account balance. */
  record AccountBalance(BookWorkflowStepId stepId, AccountBalanceCriteria query)
      implements BookWorkflowStep {
    /** Validates the step. */
    public AccountBalance {
      requireStepId(stepId);
      Objects.requireNonNull(query, "query");
    }
  }

  /** Evaluates one workflow assertion. */
  record Assert(BookWorkflowStepId stepId, BookWorkflowAssertion assertion)
      implements BookWorkflowStep {
    /** Validates the step. */
    public Assert {
      requireStepId(stepId);
      Objects.requireNonNull(assertion, "assertion");
    }
  }

  private static void requireStepId(BookWorkflowStepId stepId) {
    Objects.requireNonNull(stepId, "stepId");
  }
}
