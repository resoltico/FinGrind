package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Recording workflow used to assert CLI routing without touching SQLite runtime state. */
class CliRecordingWorkflow extends CliBookWorkflowAdapter {
  private final List<BookAccess> openBookAccesses = new ArrayList<>();
  private final List<OpenBookCommand> openBookCommands = new ArrayList<>();
  private final List<BookAccess> rekeyBookAccesses = new ArrayList<>();
  private final List<Path> newRekeyBookKeyFilePaths = new ArrayList<>();
  private final List<BookAccess> backupBookAccesses = new ArrayList<>();
  private final List<Path> backupFilePaths = new ArrayList<>();
  private final List<Path> backupBookKeyFilePaths = new ArrayList<>();
  private final List<UUID> backupIds = new ArrayList<>();
  private final List<Path> restoreBookFilePaths = new ArrayList<>();
  private final List<Path> newRestoreBookKeyFilePaths = new ArrayList<>();
  private final List<Path> restoreBackupFilePaths = new ArrayList<>();
  private final List<Path> restoreBackupBookKeyFilePaths = new ArrayList<>();
  private final List<List<AttestationCredentialSource>> restoreAttestationCredentialSources =
      new ArrayList<>();
  private final List<BookAccess> declareAccountAccesses = new ArrayList<>();
  private final List<BookAccess> listAccountAccesses = new ArrayList<>();
  private final List<ListAccountsQuery> listAccountQueries = new ArrayList<>();
  private final List<BookAccess> executePlanAccesses = new ArrayList<>();
  private final List<BookAccess> preflightAccesses = new ArrayList<>();
  private final List<BookAccess> commitAccesses = new ArrayList<>();
  private final OpenBookResult openBookResult;
  private final RekeyBookResult rekeyBookResult;
  private final DeclareAccountResult declareAccountResult;
  private final ListAccountsResult listAccountsResult;
  private final PreflightEntryResult preflightResult;
  private final CommitEntryResult commitResult;
  private BackupBookResult backupBookResult =
      new BackupBookResult.BackedUp(
          CliWorkflowDoubleSupport.hint(Path.of("books/unused.sqlite")),
          CliWorkflowDoubleSupport.hint(Path.of("books/unused.backup.sqlite")),
          CliWorkflowDoubleSupport.hint(Path.of("keys/unused.backup.key")),
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          ProtectedBookPairPublicationCompletion.PUBLISHED,
          CliFixtureSupport.pairPublicationRetention(
              CliWorkflowDoubleSupport.hint(Path.of("books/unused.backup.sqlite")),
              CliWorkflowDoubleSupport.hint(Path.of("keys/unused.backup.key"))),
          BackupAcknowledgementState.ACKNOWLEDGED,
          CliFixtureSupport.attestationCommit());
  private RestoreBookResult restoreBookResult =
      new RestoreBookResult.Restored(
          CliWorkflowDoubleSupport.hint(Path.of("books/unused.sqlite")),
          CliWorkflowDoubleSupport.hint(Path.of("keys/unused-restored.key")),
          CliFixtureSupport.attestationCommit(),
          ProtectedBookPairPublicationCompletion.PUBLISHED,
          CliFixtureSupport.pairPublicationRetention(
              CliWorkflowDoubleSupport.hint(Path.of("books/unused.sqlite")),
              CliWorkflowDoubleSupport.hint(Path.of("keys/unused-restored.key"))));
  private @Nullable LedgerPlanResult executePlanResult;

  CliRecordingWorkflow(
      OpenBookResult openBookResult,
      RekeyBookResult rekeyBookResult,
      DeclareAccountResult declareAccountResult,
      ListAccountsResult listAccountsResult,
      PreflightEntryResult preflightResult,
      CommitEntryResult commitResult) {
    this.openBookResult = openBookResult;
    this.rekeyBookResult = rekeyBookResult;
    this.declareAccountResult = declareAccountResult;
    this.listAccountsResult = listAccountsResult;
    this.preflightResult = preflightResult;
    this.commitResult = commitResult;
  }

  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    openBookAccesses.add(bookAccess);
    openBookCommands.add(command);
    return CliWorkflowDoubleSupport.accepted(openBookResult);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, Path newBookKeyFilePath) {
    rekeyBookAccesses.add(bookAccess);
    newRekeyBookKeyFilePaths.add(newBookKeyFilePath);
    return CliWorkflowDoubleSupport.accepted(rekeyBookResult);
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath, UUID backupId) {
    backupBookAccesses.add(bookAccess);
    backupFilePaths.add(backupFilePath);
    backupBookKeyFilePaths.add(backupBookKeyFilePath);
    backupIds.add(backupId);
    return CliWorkflowDoubleSupport.accepted(backupBookResult);
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      List<AttestationCredentialSource> attestationCredentialSources) {
    restoreBookFilePaths.add(bookFilePath);
    newRestoreBookKeyFilePaths.add(newBookKeyFilePath);
    restoreBackupFilePaths.add(backupFilePath);
    restoreBackupBookKeyFilePaths.add(backupBookKeyFilePath);
    restoreAttestationCredentialSources.add(List.copyOf(attestationCredentialSources));
    return CliWorkflowDoubleSupport.accepted(restoreBookResult);
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    declareAccountAccesses.add(bookAccess);
    return CliWorkflowDoubleSupport.accepted(declareAccountResult);
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    listAccountAccesses.add(bookAccess);
    listAccountQueries.add(query);
    return CliWorkflowDoubleSupport.accepted(listAccountsResult);
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    executePlanAccesses.add(bookAccess);
    return CliWorkflowDoubleSupport.accepted(
        executePlanResult == null
            ? CliFixtureSupport.successfulPlanResult(plan.planId())
            : executePlanResult);
  }

  @Override
  public ContractDecision<PreflightEntryResult> preflight(
      BookAccess bookAccess, PostEntryCommand command) {
    preflightAccesses.add(bookAccess);
    return CliWorkflowDoubleSupport.accepted(preflightResult);
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    commitAccesses.add(bookAccess);
    return CliWorkflowDoubleSupport.accepted(commitResult);
  }

  List<BookAccess> openBookAccesses() {
    return openBookAccesses;
  }

  List<OpenBookCommand> openBookCommands() {
    return openBookCommands;
  }

  List<BookAccess> declareAccountAccesses() {
    return declareAccountAccesses;
  }

  List<BookAccess> rekeyBookAccesses() {
    return rekeyBookAccesses;
  }

  List<Path> newRekeyBookKeyFilePaths() {
    return newRekeyBookKeyFilePaths;
  }

  List<BookAccess> backupBookAccesses() {
    return backupBookAccesses;
  }

  List<Path> backupFilePaths() {
    return backupFilePaths;
  }

  List<Path> backupBookKeyFilePaths() {
    return backupBookKeyFilePaths;
  }

  List<UUID> backupIds() {
    return backupIds;
  }

  List<Path> restoreBookFilePaths() {
    return restoreBookFilePaths;
  }

  List<Path> restoreBackupFilePaths() {
    return restoreBackupFilePaths;
  }

  List<Path> restoreBackupBookKeyFilePaths() {
    return restoreBackupBookKeyFilePaths;
  }

  List<List<AttestationCredentialSource>> restoreAttestationCredentialSources() {
    return restoreAttestationCredentialSources;
  }

  List<BookAccess> listAccountAccesses() {
    return listAccountAccesses;
  }

  List<ListAccountsQuery> listAccountQueries() {
    return listAccountQueries;
  }

  List<BookAccess> executePlanAccesses() {
    return executePlanAccesses;
  }

  List<BookAccess> preflightAccesses() {
    return preflightAccesses;
  }

  List<BookAccess> commitAccesses() {
    return commitAccesses;
  }

  boolean workflowInvoked() {
    return !openBookAccesses.isEmpty()
        || !rekeyBookAccesses.isEmpty()
        || !backupBookAccesses.isEmpty()
        || !restoreBookFilePaths.isEmpty()
        || !declareAccountAccesses.isEmpty()
        || !listAccountAccesses.isEmpty()
        || !executePlanAccesses.isEmpty()
        || !preflightAccesses.isEmpty()
        || !commitAccesses.isEmpty();
  }

  void setExecutePlanResult(LedgerPlanResult executePlanResult) {
    this.executePlanResult = executePlanResult;
  }

  void setBackupBookResult(BackupBookResult backupBookResult) {
    this.backupBookResult = backupBookResult;
  }

  void setRestoreBookResult(RestoreBookResult restoreBookResult) {
    this.restoreBookResult = restoreBookResult;
  }
}
