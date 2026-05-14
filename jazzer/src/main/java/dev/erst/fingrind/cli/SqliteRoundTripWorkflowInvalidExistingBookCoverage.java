package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Covers corrupt or directory-backed preexisting-book paths for SQLite round-trip fuzzing. */
final class SqliteRoundTripWorkflowInvalidExistingBookCoverage {
  private SqliteRoundTripWorkflowInvalidExistingBookCoverage() {}

  /** Models a workflow attempt that either returned a decision or failed before one existed. */
  private sealed interface DecisionAttempt<T> permits DecisionSuccess, DecisionRuntimeFailure {}

  private record DecisionSuccess<T>(ContractDecision<T> decision) implements DecisionAttempt<T> {
    private DecisionSuccess {
      Objects.requireNonNull(decision, "decision");
    }
  }

  private record DecisionRuntimeFailure<T>(RuntimeException runtimeFailure)
      implements DecisionAttempt<T> {
    private DecisionRuntimeFailure {
      Objects.requireNonNull(runtimeFailure, "runtimeFailure");
    }
  }

  private record WorkflowInspectionSupplier(SqliteCliBookWorkflow workflow, BookAccess bookAccess)
      implements Supplier<ContractDecision<BookInspection>> {
    @Override
    public ContractDecision<BookInspection> get() {
      return workflow.inspectBook(bookAccess);
    }
  }

  private record WorkflowOpenSupplier(
      SqliteCliBookWorkflow workflow, BookAccess bookAccess, OpenBookCommand command)
      implements Supplier<ContractDecision<OpenBookResult>> {
    @Override
    public ContractDecision<OpenBookResult> get() {
      return workflow.openBook(bookAccess, command);
    }
  }

  private record WorkflowCommitSupplier(
      SqliteCliBookWorkflow workflow, BookAccess bookAccess, PostEntryCommand command)
      implements Supplier<ContractDecision<CommitEntryResult>> {
    @Override
    public ContractDecision<CommitEntryResult> get() {
      return workflow.commit(bookAccess, command);
    }
  }

  private record WorkflowListAccountsSupplier(
      SqliteCliBookWorkflow workflow, BookAccess bookAccess, ListAccountsQuery query)
      implements Supplier<ContractDecision<ListAccountsResult>> {
    @Override
    public ContractDecision<ListAccountsResult> get() {
      return workflow.listAccounts(bookAccess, query);
    }
  }

  static void exerciseInvalidExistingBookCoverage(PostEntryCommand command, Path invalidRoot)
      throws IOException {
    SqliteCliBookWorkflow workflow = SqliteRoundTripWorkflowResources.sqliteWorkflow();

    Path directoryBookPath = invalidRoot.resolve("directory-backed-book");
    Files.createDirectories(directoryBookPath);
    Path directoryKeyPath = invalidRoot.resolve("directory-backed-book.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(directoryKeyPath);
    BookAccess directoryBookAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(directoryBookPath, directoryKeyPath);
    assertNonInitializedInspection(
        new WorkflowInspectionSupplier(workflow, directoryBookAccess), directoryBookPath);
    assertNotOpened(
        new WorkflowOpenSupplier(workflow, directoryBookAccess, CliFuzzFixtures.openBookCommand()),
        directoryBookPath);
    assertNotCommitted(
        new WorkflowCommitSupplier(
            workflow,
            directoryBookAccess,
            SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                command, "directory-backed")),
        null);

    Path plaintextBookPath = invalidRoot.resolve("plaintext-book.sqlite");
    Files.createDirectories(plaintextBookPath.getParent());
    Files.writeString(plaintextBookPath, "not sqlite", StandardCharsets.UTF_8);
    Path plaintextKeyPath = invalidRoot.resolve("plaintext-book.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(plaintextKeyPath);
    BookAccess plaintextBookAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(plaintextBookPath, plaintextKeyPath);
    assertNonInitializedInspection(
        new WorkflowInspectionSupplier(workflow, plaintextBookAccess), plaintextBookPath);
    assertNotOpened(
        new WorkflowOpenSupplier(workflow, plaintextBookAccess, CliFuzzFixtures.openBookCommand()),
        plaintextBookPath);
    assertNotCommitted(
        new WorkflowCommitSupplier(
            workflow,
            plaintextBookAccess,
            SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                command, "plaintext-book")),
        null);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
        new WorkflowListAccountsSupplier(
            workflow, plaintextBookAccess, new ListAccountsQuery(50, Optional.empty())),
        OutputMode.JSON,
        SqliteRoundTripWorkflowRenderingAssertions::writeListAccountsJson,
        null);
  }

  static void assertNonInitializedInspection(
      Supplier<ContractDecision<BookInspection>> inspectionSupplier, Path bookPath)
      throws IOException {
    switch (captureDecisionAttempt(inspectionSupplier)) {
      case DecisionRuntimeFailure<BookInspection>(RuntimeException runtimeFailure) ->
          SqliteRoundTripWorkflowRenderingAssertions.assertRenderedRuntimeFailure(
              runtimeFailure, OutputMode.JSON, null);
      case DecisionSuccess<BookInspection>(ContractDecision<BookInspection> decision) -> {
        switch (decision) {
          case ContractDecision.Accepted<BookInspection>(BookInspection inspection) -> {
            if (inspection.status().initialized()) {
              throw new IllegalStateException(
                  "Invalid existing book path unexpectedly inspected as initialized: " + bookPath);
            }
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
                ContractDecision.accepted(inspection),
                OutputMode.JSON,
                (writer, accepted) ->
                    writer.writeBookInspection(bookPath, accepted, OutputMode.JSON),
                null);
          }
          case ContractDecision.Rejected<BookInspection>(ContractFailure failure) ->
              SqliteRoundTripWorkflowRenderingAssertions.assertRenderedFailure(
                  failure, OutputMode.JSON, null);
        }
      }
    }
  }

  static void assertNotOpened(
      Supplier<ContractDecision<OpenBookResult>> openSupplier, Path bookPath) throws IOException {
    switch (captureDecisionAttempt(openSupplier)) {
      case DecisionRuntimeFailure<OpenBookResult>(RuntimeException runtimeFailure) ->
          SqliteRoundTripWorkflowRenderingAssertions.assertRenderedRuntimeFailure(
              runtimeFailure, OutputMode.JSON, null);
      case DecisionSuccess<OpenBookResult>(ContractDecision<OpenBookResult> decision) -> {
        switch (decision) {
          case ContractDecision.Accepted<OpenBookResult>(OpenBookResult result) -> {
            if (result instanceof OpenBookResult.Opened) {
              throw new IllegalStateException(
                  "Invalid existing book path unexpectedly opened as a valid book: " + bookPath);
            }
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
                ContractDecision.accepted(result),
                OutputMode.JSON,
                (writer, accepted) ->
                    writer.writeOpenBookResult(bookPath, accepted, OutputMode.JSON),
                null);
          }
          case ContractDecision.Rejected<OpenBookResult>(ContractFailure failure) ->
              SqliteRoundTripWorkflowRenderingAssertions.assertRenderedFailure(
                  failure, OutputMode.JSON, null);
        }
      }
    }
  }

  static void assertNotCommitted(
      Supplier<ContractDecision<CommitEntryResult>> commitSupplier,
      @Nullable String requiredFragment)
      throws IOException {
    switch (captureDecisionAttempt(commitSupplier)) {
      case DecisionRuntimeFailure<CommitEntryResult>(RuntimeException runtimeFailure) ->
          SqliteRoundTripWorkflowRenderingAssertions.assertRenderedRuntimeFailure(
              runtimeFailure, OutputMode.JSON, requiredFragment);
      case DecisionSuccess<CommitEntryResult>(ContractDecision<CommitEntryResult> decision) -> {
        switch (decision) {
          case ContractDecision.Accepted<CommitEntryResult>(CommitEntryResult result) -> {
            if (result instanceof Committed) {
              throw new IllegalStateException(
                  "Invalid existing book path unexpectedly committed a posting fact.");
            }
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
                ContractDecision.accepted(result),
                OutputMode.JSON,
                (writer, accepted) ->
                    writer.writePostEntryResult((PostEntryResult) accepted, OutputMode.JSON),
                requiredFragment);
          }
          case ContractDecision.Rejected<CommitEntryResult>(ContractFailure failure) ->
              SqliteRoundTripWorkflowRenderingAssertions.assertRenderedFailure(
                  failure, OutputMode.JSON, requiredFragment);
        }
      }
    }
  }

  private static <T> DecisionAttempt<T> captureDecisionAttempt(
      Supplier<ContractDecision<T>> decisionSupplier) {
    try {
      return new DecisionSuccess<>(decisionSupplier.get());
    } catch (RuntimeException runtimeException) {
      return new DecisionRuntimeFailure<>(runtimeException);
    }
  }
}
