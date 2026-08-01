package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.sqlite.SqliteFuzzArtifactFixtures;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Shared execution assertions for Jazzer harnesses that parse and run ledger plans. */
public final class LedgerPlanFuzzAssertions {
  private static final LedgerPlanWorkspace SYSTEM_WORKSPACE =
      () ->
          SqliteFuzzArtifactFixtures.createOwnerOnlyTemporaryArtifactDirectory(
              "fingrind-jazzer-ledger-plan-");

  private LedgerPlanFuzzAssertions() {}

  /** Owns the retained secure workspace used for one real ledger-plan execution. */
  @FunctionalInterface
  interface LedgerPlanWorkspace {
    /** Creates one isolated workspace. */
    Path create() throws IOException;
  }

  /** Executes one parsed plan within the workspace admitted for a fuzzing invocation. */
  @FunctionalInterface
  interface LedgerPlanExecutor {
    /** Executes the plan and returns the public invariant summary. */
    ExecutionSnapshot execute(LedgerPlan plan, Path scratchRoot) throws IOException;
  }

  private record JournalScanSummary(int listQueryStepCount, int structuredListQueryStepCount) {
    private JournalScanSummary {
      if (listQueryStepCount < 0
          || structuredListQueryStepCount < 0
          || structuredListQueryStepCount > listQueryStepCount) {
        throw new IllegalArgumentException("Ledger journal scan counts must be non-negative.");
      }
    }

    private JournalScanSummary recordStructuredListQuery() {
      return new JournalScanSummary(listQueryStepCount + 1, structuredListQueryStepCount + 1);
    }

    private JournalScanSummary recordRejectedListQuery() {
      return new JournalScanSummary(listQueryStepCount + 1, structuredListQueryStepCount);
    }
  }

  /** Stable execution summary returned after one parsed ledger plan is executed and asserted. */
  public record ExecutionSnapshot(
      LedgerPlanStatus executionStatus,
      int journalStepCount,
      int listQueryStepCount,
      int structuredListQueryStepCount) {
    /** Validates one execution summary. */
    public ExecutionSnapshot {
      Objects.requireNonNull(executionStatus, "executionStatus must not be null");
      if (journalStepCount < 0
          || listQueryStepCount < 0
          || structuredListQueryStepCount < 0
          || structuredListQueryStepCount > listQueryStepCount) {
        throw new IllegalArgumentException("Ledger plan execution counts must be non-negative.");
      }
    }
  }

  /** Executes one parsed ledger plan and asserts public journal invariants. */
  public static ExecutionSnapshot executeAndAssert(LedgerPlan plan) {
    return executeAndAssert(plan, SYSTEM_WORKSPACE);
  }

  static ExecutionSnapshot executeAndAssert(LedgerPlan plan, LedgerPlanWorkspace workspace) {
    return executeAndAssert(plan, workspace, LedgerPlanFuzzAssertions::executeInWorkspace);
  }

  static ExecutionSnapshot executeAndAssert(
      LedgerPlan plan, LedgerPlanWorkspace workspace, LedgerPlanExecutor executor) {
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(workspace, "workspace");
    Objects.requireNonNull(executor, "executor");
    Path scratchRoot;
    try {
      scratchRoot =
          SqliteFuzzArtifactFixtures.requireOwnerOnlyArtifactDirectory(workspace.create());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not create or admit the ledger-plan fuzz workspace.", exception);
    }
    try {
      return executor.execute(plan, scratchRoot);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Ledger-plan fuzz execution did not complete; inspect the retained workspace: "
              + scratchRoot,
          exception);
    } catch (RuntimeException exception) {
      recordRetainedWorkspace(scratchRoot, exception);
      throw exception;
    } catch (Error failure) {
      recordRetainedWorkspace(scratchRoot, failure);
      throw failure;
    }
  }

  private static void recordRetainedWorkspace(Path scratchRoot, Throwable primaryFailure) {
    primaryFailure.addSuppressed(
        new IOException(
            "Ledger-plan fuzz execution retained its workspace for inspection: " + scratchRoot));
  }

  private static ExecutionSnapshot executeInWorkspace(LedgerPlan plan, Path scratchRoot)
      throws IOException {
    Path bookPath = scratchRoot.resolve("book.sqlite");
    Path keyPath = scratchRoot.resolve("book.key");
    SqliteFuzzArtifactFixtures.writeDeterministicBookKeyFile(keyPath);
    boolean mutatesBook = plan.steps().stream().anyMatch(step -> step.kind().mutatesBook());
    BookAccess bookAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(
            bookPath,
            keyPath,
            mutatesBook ? CliFuzzWorkflowFixtures.attestationCredentialSources() : List.of());
    if (mutatesBook) {
      SqliteRoundTripWorkflowResources.sqliteLifecycleWorkflow()
          .openBook(bookAccess, CliFuzzWorkflowFixtures.openBookCommand(functionalCurrency(plan)))
          .requireAccepted();
    }
    LedgerPlanResult result =
        SqliteRoundTripWorkflowResources.sqliteMutationWorkflow()
            .executePlan(bookAccess, plan)
            .requireAccepted();
    return assertPlanResult(plan, result);
  }

  static CurrencyUnit functionalCurrency(LedgerPlan plan) {
    return plan.steps().stream()
        .flatMap(
            step ->
                switch (step) {
                  case LedgerStep.PreflightEntry preflight ->
                      java.util.stream.Stream.of(
                          CliFuzzFixtures.journalEntry(preflight.command()).currencyUnit());
                  case LedgerStep.PostEntry post ->
                      java.util.stream.Stream.of(
                          CliFuzzFixtures.journalEntry(post.command()).currencyUnit());
                  default -> java.util.stream.Stream.empty();
                })
        .findFirst()
        .orElseGet(() -> CurrencyUnit.of("EUR"));
  }

  static ExecutionSnapshot assertPlanResult(LedgerPlan plan, LedgerPlanResult result) {
    List<LedgerJournalEntry> journalSteps = result.journal().steps();
    assertPlanIdentity(plan, result);
    assertJournalBounds(plan, result.status(), journalSteps);
    JournalScanSummary journalSummary = scanJournal(plan, journalSteps);
    return new ExecutionSnapshot(
        result.status(),
        journalSteps.size(),
        journalSummary.listQueryStepCount(),
        journalSummary.structuredListQueryStepCount());
  }

  private static void assertPlanIdentity(LedgerPlan plan, LedgerPlanResult result) {
    if (!result.planId().equals(plan.planId())) {
      throw new IllegalStateException("Ledger plan execution changed the plan id.");
    }
  }

  private static void assertJournalBounds(
      LedgerPlan plan, LedgerPlanStatus status, List<LedgerJournalEntry> journalSteps) {
    boolean terminalBoundary =
        journalSteps.getLast().kind() == LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY;
    int allowedJournalSteps = plan.steps().size() + (terminalBoundary ? 1 : 0);
    if (journalSteps.size() > allowedJournalSteps) {
      throw new IllegalStateException("Ledger plan journal exceeded the declared step count.");
    }
    if (status == LedgerPlanStatus.SUCCEEDED && journalSteps.size() != plan.steps().size()) {
      throw new IllegalStateException("Successful ledger plan execution omitted journal steps.");
    }
  }

  private static JournalScanSummary scanJournal(
      LedgerPlan plan, List<LedgerJournalEntry> journalSteps) {
    JournalScanSummary summary = new JournalScanSummary(0, 0);
    int declaredIndex = 0;
    for (int index = 0; index < journalSteps.size(); index++) {
      LedgerJournalEntry journalEntry = journalSteps.get(index);
      if (journalEntry.kind() == LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY) {
        assertTerminalBoundary(index, journalSteps.size());
        continue;
      }
      assertDeclaredStep(plan, journalEntry, declaredIndex);
      declaredIndex++;
      summary = tallyListQuerySummary(summary, journalEntry);
    }
    return summary;
  }

  private static void assertTerminalBoundary(int index, int journalSize) {
    if (index != journalSize - 1) {
      throw new IllegalStateException("Ledger plan boundary journal entries must be terminal.");
    }
  }

  private static JournalScanSummary tallyListQuerySummary(
      JournalScanSummary summary, LedgerJournalEntry journalEntry) {
    if (!isListQueryKind(journalEntry.kind())) {
      return summary;
    }
    if (journalEntry.status() == LedgerStepStatus.SUCCEEDED) {
      LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(journalEntry);
      return summary.recordStructuredListQuery();
    }
    LedgerPlanListQueryAssertions.assertRejectedListQueryFacts(journalEntry);
    return summary.recordRejectedListQuery();
  }

  private static boolean isListQueryKind(LedgerJournalKind kind) {
    return kind == LedgerStepKind.LIST_ACCOUNTS || kind == LedgerStepKind.LIST_POSTINGS;
  }

  private static void assertDeclaredStep(
      LedgerPlan plan, LedgerJournalEntry journalEntry, int declaredIndex) {
    var declaredStep = plan.steps().get(declaredIndex);
    if (!journalEntry.stepId().equals(declaredStep.stepId())) {
      throw new IllegalStateException("Ledger plan journal changed step order or identity.");
    }
    if (!journalEntry.kind().wireValue().equals(declaredStep.kind().wireValue())) {
      throw new IllegalStateException("Ledger plan journal changed the declared step kind.");
    }
  }
}
