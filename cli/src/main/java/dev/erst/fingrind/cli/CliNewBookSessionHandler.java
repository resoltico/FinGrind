package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import java.util.Objects;

/** Couples every new-book session outcome with its mandatory close-failure translation. */
final class CliNewBookSessionHandler {
  private final SqliteCliOpenBookWorkflow workflow;
  private final BookAccess bookAccess;
  private final OpenBookCommand command;
  private final CliOpenBookSessionCloseFailureMapper closeFailureMapper;

  CliNewBookSessionHandler(
      SqliteCliOpenBookWorkflow workflow,
      BookAccess bookAccess,
      OpenBookCommand command,
      CliOpenBookSessionCloseFailureMapper closeFailureMapper) {
    this.workflow = Objects.requireNonNull(workflow, "workflow");
    this.bookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    this.command = Objects.requireNonNull(command, "command");
    this.closeFailureMapper = Objects.requireNonNull(closeFailureMapper, "closeFailureMapper");
  }

  ContractDecision<SqliteCliOpenBookWorkflow.OpenBookExecution> work(
      SqliteAdministrationSession bookSession) {
    return workflow
        .genesisPreparation(command)
        .fold(
            preparation ->
                workflow.completeOpenedBookExecution(
                    bookAccess.bookFilePath(), bookSession, command, preparation),
            rejection ->
                ContractDecision.rejected(
                    closeFailureMapper.retainedNewBookSessionFailure(rejection)));
  }

  ContractDecision<SqliteCliOpenBookWorkflow.OpenBookExecution> rejected(
      ContractFailure rejection, RuntimeException closeFailure) {
    return SqliteCliOpenBookWorkflow.rejectedExecution(
        closeFailureMapper.rejectedSessionCloseFailure(rejection, closeFailure));
  }

  ContractDecision<SqliteCliOpenBookWorkflow.OpenBookExecution> workFailure(
      RuntimeException workFailure, RuntimeException closeFailure) {
    return SqliteCliOpenBookWorkflow.rejectedExecution(
        closeFailureMapper.workAndSessionCloseFailure(workFailure, closeFailure));
  }

  ContractDecision<SqliteCliOpenBookWorkflow.OpenBookExecution> opened(
      SqliteCliOpenBookWorkflow.OpenBookExecution opened, RuntimeException closeFailure) {
    return SqliteCliOpenBookWorkflow.sessionCloseFailureExecution(
        opened, closeFailureMapper, closeFailure);
  }
}
