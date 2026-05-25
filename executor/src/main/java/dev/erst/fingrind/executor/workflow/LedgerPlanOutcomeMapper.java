package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared fact and failure mapping for ledger-plan execution steps. */
public final class LedgerPlanOutcomeMapper {
  private LedgerPlanOutcomeMapper() {}

  /** Returns one successful workflow step outcome containing the canonical balance fact payload. */
  public static LedgerPlanStepOutcome balanceFacts(AccountBalanceView view) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(view));
  }

  /** Expands one committed posting into the canonical workflow fact payload. */
  public static List<BookWorkflowFact> postingFacts(CommittedPosting postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  /** Converts one local administration rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome administrationRejection(
      BookkeepingAdministrationRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(administrationFailure(rejection));
  }

  /** Converts one local query rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome queryRejection(BookkeepingQueryRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(queryFailure(rejection));
  }

  /** Converts one local posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(BookkeepingPostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(postingFailure(rejection));
  }

  /** Converts one published posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(PostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(postingFailure(rejection));
  }

  /** Creates one local assertion-failure outcome with the supplied workflow facts. */
  public static LedgerPlanStepOutcome assertionFailure(String message, BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.AssertionFailed(
        new BookWorkflowFailure("assertion-failed", message, List.of(facts)));
  }

  /** Chooses the public-facing missing-book code that matches the first workflow step family. */
  public static String missingBookCode(BookWorkflowStep firstStep) {
    Objects.requireNonNull(firstStep, "firstStep");
    if (firstStep instanceof BookWorkflowStep.OpenBook
        || firstStep instanceof BookWorkflowStep.DeclareAccount) {
      return "administration-book-not-initialized";
    }
    if (firstStep instanceof BookWorkflowStep.PreflightEntry
        || firstStep instanceof BookWorkflowStep.PostEntry) {
      return "posting-book-not-initialized";
    }
    return BookkeepingQueryRejection.bookNotInitializedCode();
  }

  /** Creates one successful workflow step outcome from the supplied facts. */
  public static LedgerPlanStepOutcome stepSucceeded(BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  /** Creates one successful workflow step outcome from the supplied fact list. */
  public static LedgerPlanStepOutcome stepSucceeded(List<BookWorkflowFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  /** Wraps one unexpected step exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedExecutionFailure(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    List<BookWorkflowFact> facts =
        List.of(BookWorkflowFact.text("exceptionType", failure.getClass().getName()));
    return new BookWorkflowJournalEntry.Rejected(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        startedAt,
        finishedAt,
        facts,
        new BookWorkflowFailure(
            "unexpected-step-failure", unexpectedExecutionFailureMessage(step, failure), facts));
  }

  /** Wraps one unexpected plan-boundary exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryPhase phase,
      Instant startedAt,
      Instant finishedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    List<BookWorkflowFact> failureFacts = new ArrayList<>();
    failureFacts.add(BookWorkflowFact.text("phase", phase.wireValue()));
    failureFacts.add(BookWorkflowFact.text("exceptionType", failure.getClass().getName()));
    if (triggerStepId != null) {
      failureFacts.add(BookWorkflowFact.text("triggerStepId", triggerStepId.value()));
    }
    if (triggerDescriptor != null) {
      appendTriggerDescriptorFacts(failureFacts, triggerDescriptor);
    }
    if (cleanupFailure != null) {
      failureFacts.add(
          BookWorkflowFact.group(
              "cleanupFailure",
              List.of(
                  BookWorkflowFact.text("exceptionType", cleanupFailure.getClass().getName()))));
    }
    if (priorFailure != null) {
      failureFacts.add(
          BookWorkflowFact.group(
              "priorFailure",
              List.of(
                  BookWorkflowFact.text("code", priorFailure.code()),
                  BookWorkflowFact.text("message", priorFailure.message()))));
    }
    return new BookWorkflowJournalEntry.Rejected(
        boundaryStepId(phase),
        new BookWorkflowJournalDescriptor.Boundary(phase),
        startedAt,
        finishedAt,
        List.of(),
        new BookWorkflowFailure(
            "unexpected-plan-failure",
            unexpectedPlanFailureMessage(phase, triggerStepId, failure),
            failureFacts));
  }

  private static BookWorkflowFailure administrationFailure(
      BookkeepingAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    if (rejection instanceof BookkeepingAdministrationRejection.BookAlreadyInitialized) {
      return new BookWorkflowFailure(
          "book-already-initialized", "The selected book is already initialized.", List.of());
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookNotInitialized) {
      return new BookWorkflowFailure(
          "administration-book-not-initialized", missingBookMessage(), List.of());
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookContainsSchema) {
      return new BookWorkflowFailure(
          "book-contains-schema",
          "The selected SQLite file already contains schema objects and cannot be initialized as a new book.",
          List.of());
    }
    if (rejection instanceof BookkeepingAdministrationRejection.AccountTypeConflict conflict) {
      return new BookWorkflowFailure(
          "account-type-conflict",
          accountTypeConflictMessage(
              conflict.accountCode(),
              conflict.existingAccountType(),
              conflict.requestedAccountType()),
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text(
                  "existingAccountType", conflict.existingAccountType().wireValue()),
              BookWorkflowFact.text(
                  "requestedAccountType", conflict.requestedAccountType().wireValue())));
    }
    BookkeepingAdministrationRejection.AccountRoleConflict conflict =
        (BookkeepingAdministrationRejection.AccountRoleConflict) rejection;
    return new BookWorkflowFailure(
        "account-role-conflict",
        accountRoleConflictMessage(
            conflict.accountCode(),
            conflict.existingAccountRole(),
            conflict.requestedAccountRole()),
        List.of(
            BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
            BookWorkflowFact.text(
                "existingAccountRole", conflict.existingAccountRole().wireValue()),
            BookWorkflowFact.text(
                "requestedAccountRole", conflict.requestedAccountRole().wireValue())));
  }

  private static BookWorkflowFailure queryFailure(BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingQueryRejection.BookNotInitialized _ ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.bookNotInitializedCode(), missingBookMessage(), List.of());
      case BookkeepingQueryRejection.UnknownAccount unknownAccount ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.wireCode(unknownAccount),
              "Account '%s' is not declared in this book."
                  .formatted(unknownAccount.accountCode().value()),
              List.of(BookWorkflowFact.text("accountCode", unknownAccount.accountCode().value())));
      case BookkeepingQueryRejection.PostingNotFound postingNotFound ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.wireCode(postingNotFound),
              "Posting '%s' does not exist in this book."
                  .formatted(postingNotFound.postingId().value()),
              List.of(BookWorkflowFact.text("postingId", postingNotFound.postingId().value())));
    };
  }

  private static BookWorkflowFailure postingFailure(BookkeepingPostingRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    PostingRejection publishedRejection =
        dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator.toPublished(
            rejection);
    return postingFailure(publishedRejection);
  }

  private static BookWorkflowFailure postingFailure(PostingRejection publishedRejection) {
    Objects.requireNonNull(publishedRejection, "publishedRejection");
    return new BookWorkflowFailure(
        PostingRejection.wireCode(publishedRejection),
        RejectionNarrative.message(publishedRejection),
        postingRejectionFacts(publishedRejection));
  }

  private static String missingBookMessage() {
    return "The selected book does not exist or has not been initialized with an open book step.";
  }

  private static String accountRoleConflictMessage(
      AccountCode accountCode, AccountRole existingAccountRole, AccountRole requestedAccountRole) {
    return "Account '%s' already exists with account role '%s'; FinGrind will not amend it to '%s'."
        .formatted(
            accountCode.value(), existingAccountRole.wireValue(), requestedAccountRole.wireValue());
  }

  private static String accountTypeConflictMessage(
      AccountCode accountCode, AccountType existingAccountType, AccountType requestedAccountType) {
    return "Account '%s' already exists with account type '%s'; FinGrind will not amend it to '%s'."
        .formatted(
            accountCode.value(), existingAccountType.wireValue(), requestedAccountType.wireValue());
  }

  private static List<BookWorkflowFact> priorPostingFacts(PostingId priorPostingId) {
    return List.of(BookWorkflowFact.text("priorPostingId", priorPostingId.value()));
  }

  private static List<BookWorkflowFact> postingRejectionFacts(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> List.of();
      case PostingRejection.AccountStateViolations violations -> accountStateFacts(violations);
      case PostingRejection.EntrySemanticsViolations violations -> entrySemanticsFacts(violations);
      case PostingRejection.DuplicateIdempotencyKey _ -> List.of();
      case PostingRejection.BookFunctionalCurrencyMismatch mismatch ->
          List.of(
              BookWorkflowFact.text("functionalCurrency", mismatch.functionalCurrency().code()),
              BookWorkflowFact.text("attemptedCurrency", mismatch.attemptedCurrency().code()));
      case PostingRejection.TransferredPeriodResultViolation transferredPeriodResultViolation ->
          List.of(
              BookWorkflowFact.text(
                  "transferredThroughEffectiveDate",
                  transferredPeriodResultViolation.transferredThroughEffectiveDate().toString()),
              BookWorkflowFact.text(
                  "attemptedEffectiveDate",
                  transferredPeriodResultViolation.attemptedEffectiveDate().toString()));
      case PostingRejection.OpeningBalanceWindowClosed openingBalanceWindowClosed ->
          List.of(
              BookWorkflowFact.text(
                  "firstBlockingPostingKind",
                  openingBalanceWindowClosed.firstBlockingPostingKind().wireValue()),
              BookWorkflowFact.text(
                  "firstBlockingEffectiveDate",
                  openingBalanceWindowClosed.firstBlockingEffectiveDate().toString()));
      case PostingRejection.OpeningBalanceTouchesNominalAccount openingBalanceNominal ->
          List.of(
              BookWorkflowFact.text("accountCode", openingBalanceNominal.accountCode().value()),
              BookWorkflowFact.text(
                  "accountType", openingBalanceNominal.accountType().wireValue()));
      case PostingRejection.ResultHoldingAccountReserved resultHoldingReserved ->
          List.of(
              BookWorkflowFact.text("accountCode", resultHoldingReserved.accountCode().value()));
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          priorPostingFacts(reversalTargetNotFound.priorPostingId());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          priorPostingFacts(reversalAlreadyExists.priorPostingId());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          priorPostingFacts(reversalDoesNotNegateTarget.priorPostingId());
    };
  }

  private static List<BookWorkflowFact> accountStateFacts(
      PostingRejection.AccountStateViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.AccountStateViolation violation : violations.violations()) {
      switch (violation) {
        case PostingRejection.UnknownAccount unknownAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "unknown-account"),
                        BookWorkflowFact.text(
                            "accountCode", unknownAccount.accountCode().value()))));
        case PostingRejection.InactiveAccount inactiveAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "inactive-account"),
                        BookWorkflowFact.text(
                            "accountCode", inactiveAccount.accountCode().value()))));
        case PostingRejection.NonPostableAccount nonPostableAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "non-postable-account"),
                        BookWorkflowFact.text(
                            "accountCode", nonPostableAccount.accountCode().value()),
                        BookWorkflowFact.text(
                            "accountNodeKind", nonPostableAccount.accountNodeKind().wireValue()))));
      }
    }
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> entrySemanticsFacts(
      PostingRejection.EntrySemanticsViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.EntrySemanticsViolation violation : violations.violations()) {
      facts.add(entrySemanticsViolationFact(violation));
    }
    return List.copyOf(facts);
  }

  private static BookWorkflowFact entrySemanticsViolationFact(
      PostingRejection.EntrySemanticsViolation violation) {
    List<BookWorkflowFact> detailFacts = new ArrayList<>();
    detailFacts.add(BookWorkflowFact.text("code", violation.code()));
    if (violation.field() != null) {
      detailFacts.add(BookWorkflowFact.text("field", violation.field()));
    }
    detailFacts.add(BookWorkflowFact.text("message", violation.message()));
    return BookWorkflowFact.group("violation", detailFacts);
  }

  private static String unexpectedExecutionFailureMessage(
      BookWorkflowStep step, RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly during step '%s'."
          .formatted(step.stepId().value());
    }
    return "Ledger plan execution failed unexpectedly during step '%s': %s"
        .formatted(step.stepId().value(), detail);
  }

  private static String unexpectedPlanFailureMessage(
      BookWorkflowBoundaryPhase phase,
      @Nullable BookWorkflowStepId triggerStepId,
      RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    String phaseContext;
    if (phase == BookWorkflowBoundaryPhase.BEGIN) {
      phaseContext = "during begin";
    } else if (phase == BookWorkflowBoundaryPhase.INITIALIZATION_CHECK) {
      phaseContext =
          triggerStepId == null
              ? "during initialization-check"
              : "during initialization-check before step '%s'".formatted(triggerStepId.value());
    } else if (phase == BookWorkflowBoundaryPhase.COMMIT) {
      phaseContext =
          triggerStepId == null
              ? "during commit"
              : "during commit after step '%s'".formatted(triggerStepId.value());
    } else {
      phaseContext =
          triggerStepId == null
              ? "during rollback"
              : "during rollback after step '%s'".formatted(triggerStepId.value());
    }
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly %s.".formatted(phaseContext);
    }
    return "Ledger plan execution failed unexpectedly %s: %s".formatted(phaseContext, detail);
  }

  private static void appendTriggerDescriptorFacts(
      List<BookWorkflowFact> facts, BookWorkflowJournalDescriptor descriptor) {
    var journalStep = BookWorkflowPublishedLanguageTranslator.toPublishedJournalStep(descriptor);
    facts.add(BookWorkflowFact.text("triggerStepKind", journalStep.kind().wireValue()));
    if (journalStep.detailKind() != null) {
      facts.add(BookWorkflowFact.text("triggerDetailKind", journalStep.detailKind().wireValue()));
    }
    if (descriptor instanceof BookWorkflowJournalDescriptor.Boundary boundary) {
      facts.add(BookWorkflowFact.text("triggerBoundaryPhase", boundary.phase().wireValue()));
    }
  }

  private static BookWorkflowStepId boundaryStepId(BookWorkflowBoundaryPhase phase) {
    return new BookWorkflowStepId("@plan-boundary:" + phase.wireValue());
  }
}
