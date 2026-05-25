package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Facade that routes deterministic CLI response rendering to narrower discovery, mutation, and
 * query writers.
 */
final class CliResponseWriter {
  private final CliOutputChannel outputChannel;
  private final CliDiscoveryResponseWriter discoveryWriter;
  private final CliMutationResponseWriter mutationWriter;
  private final CliQueryResponseWriter queryWriter;

  CliResponseWriter(PrintStream outputStream) {
    this.outputChannel = new CliOutputChannel(Objects.requireNonNull(outputStream, "outputStream"));
    this.discoveryWriter = new CliDiscoveryResponseWriter(outputChannel);
    this.mutationWriter = new CliMutationResponseWriter(outputChannel);
    this.queryWriter = new CliQueryResponseWriter(outputChannel);
  }

  void writeHelp(HelpDescriptor helpDescriptor) {
    writeHelp(helpDescriptor, OutputMode.JSON, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode) {
    writeHelp(helpDescriptor, outputMode, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode, DiscoveryDetail detail) {
    discoveryWriter.writeHelp(helpDescriptor, outputMode, detail);
  }

  void writeCapabilities(CapabilitiesDescriptor capabilitiesDescriptor) {
    writeCapabilities(capabilitiesDescriptor, OutputMode.JSON, DiscoveryDetail.MINIMAL);
  }

  void writeCapabilities(CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
    writeCapabilities(capabilitiesDescriptor, outputMode, DiscoveryDetail.MINIMAL);
  }

  void writeCapabilities(
      CapabilitiesDescriptor capabilitiesDescriptor,
      OutputMode outputMode,
      DiscoveryDetail detail) {
    discoveryWriter.writeCapabilities(capabilitiesDescriptor, outputMode, detail);
  }

  void writeEnvironment(EnvironmentDescriptor environmentDescriptor, OutputMode outputMode) {
    discoveryWriter.writeEnvironment(environmentDescriptor, outputMode);
  }

  void writeVersion(VersionDescriptor versionDescriptor) {
    writeVersion(versionDescriptor, OutputMode.JSON);
  }

  void writeVersion(VersionDescriptor versionDescriptor, OutputMode outputMode) {
    discoveryWriter.writeVersion(versionDescriptor, outputMode);
  }

  void writeRequestTemplate(ContractTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    discoveryWriter.writeRawTemplate(requestTemplate);
  }

  void writePlanTemplate(ContractTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    discoveryWriter.writeRawTemplate(planTemplate);
  }

  void writeRawTemplate(Object template) {
    discoveryWriter.writeRawTemplate(template);
  }

  void writeFailure(CliFailure failure) {
    writeFailure(failure, OutputMode.JSON);
  }

  void writeFailure(CliFailure failure, OutputMode outputMode) {
    if (outputMode == OutputMode.TEXT) {
      outputChannel.writeText(CliFailureOutputRenderer.renderFailureText(failure));
      return;
    }
    outputChannel.writeEnvelope(CliResponsePayloadMapper.failureEnvelope(failure));
  }

  void writeDeterministicFailure(CliFailure failure, OutputMode outputMode) {
    if (outputMode == OutputMode.TEXT) {
      outputChannel.writeText(CliFailureOutputRenderer.renderDeterministicFailureText(failure));
      return;
    }
    outputChannel.writeEnvelope(CliResponsePayloadMapper.failureEnvelope(failure));
  }

  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }

  void writePostEntryResult(PostEntryResult result) {
    writePostEntryResult(result, OutputMode.JSON);
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    mutationWriter.writePostEntryResult(result, outputMode);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    writeOpenBookResult(bookFilePath, result, OutputMode.JSON);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    mutationWriter.writeOpenBookResult(bookFilePath, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.JSON);
  }

  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile, OutputMode outputMode) {
    mutationWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
  }

  void writeRekeyBookResult(
      RekeyBookResult result, BookAccess.PassphraseSource replacementPassphraseSource) {
    writeRekeyBookResult(result, replacementPassphraseSource, OutputMode.JSON);
  }

  void writeRekeyBookResult(
      RekeyBookResult result,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    mutationWriter.writeRekeyBookResult(result, replacementPassphraseSource, outputMode);
  }

  void writeBackupBookResult(BackupBookResult result, OutputMode outputMode) {
    mutationWriter.writeBackupBookResult(result, outputMode);
  }

  void writeRestoreBookResult(RestoreBookResult result, OutputMode outputMode) {
    mutationWriter.writeRestoreBookResult(result, outputMode);
  }

  void writeInspectRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeInspectRekeyRollbackResult(result, outputMode);
  }

  void writeRestoreRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeRestoreRekeyRollbackResult(result, outputMode);
  }

  void writeDeleteRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeDeleteRekeyRollbackResult(result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result) {
    writeDeclareAccountResult(result, OutputMode.JSON);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    mutationWriter.writeDeclareAccountResult(result, outputMode);
  }

  void writePeriodResultTransferResult(PeriodResultTransferResult result, OutputMode outputMode) {
    mutationWriter.writePeriodResultTransferResult(result, outputMode);
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeBookInspection(bookFilePath, inspection, OutputMode.JSON);
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    queryWriter.writeBookInspection(bookFilePath, inspection, outputMode);
  }

  void writeListAccountsResult(ListAccountsResult result) {
    writeListAccountsResult(result, OutputMode.JSON);
  }

  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    queryWriter.writeListAccountsResult(result, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result) {
    writeGetPostingResult(result, OutputMode.JSON);
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    queryWriter.writeGetPostingResult(result, outputMode);
  }

  void writeListPostingsResult(ListPostingsResult result) {
    writeListPostingsResult(result, OutputMode.JSON);
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    queryWriter.writeListPostingsResult(result, outputMode);
  }

  void writeAccountBalanceResult(AccountBalanceResult result) {
    writeAccountBalanceResult(result, OutputMode.JSON);
  }

  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    queryWriter.writeAccountBalanceResult(result, outputMode);
  }

  void writeAccountBalanceResult(
      AccountBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeAccountBalanceResult(result, outputMode, exportedArtifactPath);
  }

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    queryWriter.writeTrialBalanceResult(result, outputMode);
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeTrialBalanceResult(result, outputMode, exportedArtifactPath);
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    queryWriter.writeAccountLedgerResult(result, outputMode);
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeAccountLedgerResult(result, outputMode, exportedArtifactPath);
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    queryWriter.writePeriodSummaryResult(result, outputMode);
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writePeriodSummaryResult(result, outputMode, exportedArtifactPath);
  }

  void writeFinancialPositionResult(FinancialPositionResult result, OutputMode outputMode) {
    queryWriter.writeFinancialPositionResult(result, outputMode);
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeFinancialPositionResult(result, outputMode, exportedArtifactPath);
  }

  void writeIncomeStatementResult(IncomeStatementResult result, OutputMode outputMode) {
    queryWriter.writeIncomeStatementResult(result, outputMode);
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeIncomeStatementResult(result, outputMode, exportedArtifactPath);
  }

  void writeChangesInEquityResult(ChangesInEquityResult result, OutputMode outputMode) {
    queryWriter.writeChangesInEquityResult(result, outputMode);
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    queryWriter.writeChangesInEquityResult(result, outputMode, exportedArtifactPath);
  }

  void writeLedgerPlanResult(LedgerPlanResult result, PlanResultDetail resultDetail) {
    queryWriter.writeLedgerPlanResult(result, resultDetail);
  }

  void writeJson(Object value) {
    outputChannel.writeJson(value);
  }
}
