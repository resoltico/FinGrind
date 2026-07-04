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
import java.util.List;
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

  static void exerciseInvalidExistingBookCoverage(PostEntryCommand command, Path invalidRoot)
      throws IOException {
    CliBookLifecycleWorkflow lifecycleWorkflow =
        SqliteRoundTripWorkflowResources.sqliteLifecycleWorkflow();
    CliBookMutationWorkflow mutationWorkflow =
        SqliteRoundTripWorkflowResources.sqliteMutationWorkflow();
    CliBookReadWorkflow readWorkflow = SqliteRoundTripWorkflowResources.sqliteReadWorkflow();
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(invalidRoot);

    Path directoryBookPath = invalidRoot.resolve("directory-backed-book");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(directoryBookPath);
    Path directoryKeyPath = invalidRoot.resolve("directory-backed-book.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(directoryKeyPath);
    BookAccess directoryBookAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(directoryBookPath, directoryKeyPath);
    assertNonInitializedInspection(
        inspectionSupplier(readWorkflow, directoryBookAccess), directoryBookPath);
    assertNotOpened(
        openSupplier(
            lifecycleWorkflow, directoryBookAccess, CliFuzzWorkflowFixtures.openBookCommand()),
        directoryBookPath);
    assertNotCommitted(
        commitSupplier(
            mutationWorkflow,
            directoryBookAccess,
            SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                command, "directory-backed")),
        null);

    Path plaintextBookPath = invalidRoot.resolve("plaintext-book.sqlite");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(
        Objects.requireNonNull(plaintextBookPath.getParent(), "plaintextBookPath parent"));
    Files.writeString(plaintextBookPath, "not sqlite", StandardCharsets.UTF_8);
    Path plaintextKeyPath = invalidRoot.resolve("plaintext-book.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(plaintextKeyPath);
    BookAccess plaintextBookAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(plaintextBookPath, plaintextKeyPath);
    assertNonInitializedInspection(
        inspectionSupplier(readWorkflow, plaintextBookAccess), plaintextBookPath);
    assertNotOpened(
        openSupplier(
            lifecycleWorkflow, plaintextBookAccess, CliFuzzWorkflowFixtures.openBookCommand()),
        plaintextBookPath);
    assertNotCommitted(
        commitSupplier(
            mutationWorkflow,
            plaintextBookAccess,
            SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                command, "plaintext-book")),
        null);
    SqliteRoundTripWorkflowRenderingAssertions.assertRenderedDecision(
        listAccountsSupplier(
            readWorkflow, plaintextBookAccess, new ListAccountsQuery(50, Optional.empty())),
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
                (writers, accepted, mode) ->
                    writers.query().writeBookInspection(bookPath, accepted, mode),
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
                (writers, accepted, mode) ->
                    writers.mutation().writeOpenBookResult(bookPath, List.of(), accepted, mode),
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
                (writers, accepted, mode) ->
                    writers.mutation().writePostEntryResult((PostEntryResult) accepted, mode),
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

  private static Supplier<ContractDecision<BookInspection>> inspectionSupplier(
      CliBookReadWorkflow workflow, BookAccess bookAccess) {
    return () -> workflow.inspectBook(bookAccess);
  }

  private static Supplier<ContractDecision<OpenBookResult>> openSupplier(
      CliBookLifecycleWorkflow workflow, BookAccess bookAccess, OpenBookCommand command) {
    return () -> workflow.openBook(bookAccess, command);
  }

  private static Supplier<ContractDecision<CommitEntryResult>> commitSupplier(
      CliBookMutationWorkflow workflow, BookAccess bookAccess, PostEntryCommand command) {
    return () -> workflow.commit(bookAccess, command);
  }

  private static Supplier<ContractDecision<ListAccountsResult>> listAccountsSupplier(
      CliBookReadWorkflow workflow, BookAccess bookAccess, ListAccountsQuery query) {
    return () -> workflow.listAccounts(bookAccess, query);
  }
}
