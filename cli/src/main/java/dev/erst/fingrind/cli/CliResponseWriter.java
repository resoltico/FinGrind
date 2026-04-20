package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.ContractTemplates;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Facade that routes deterministic CLI response rendering to narrower discovery, mutation, and
 * query writers.
 */
final class CliResponseWriter {
  private final CliOutputChannel outputChannel;
  private final CliDiscoveryResponseSupport discoveryWriter;
  private final CliMutationResponseSupport mutationWriter;
  private final CliQueryResponseSupport queryWriter;

  CliResponseWriter(PrintStream outputStream) {
    this.outputChannel = new CliOutputChannel(Objects.requireNonNull(outputStream, "outputStream"));
    this.discoveryWriter = new CliDiscoveryResponseSupport(outputChannel);
    this.mutationWriter = new CliMutationResponseSupport(outputChannel);
    this.queryWriter = new CliQueryResponseSupport(outputChannel);
  }

  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor) {
    writeHelp(helpDescriptor, OutputMode.JSON);
  }

  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor, OutputMode outputMode) {
    discoveryWriter.writeHelp(helpDescriptor, outputMode);
  }

  void writeCapabilities(ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor) {
    writeCapabilities(capabilitiesDescriptor, OutputMode.JSON);
  }

  void writeCapabilities(
      ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
    discoveryWriter.writeCapabilities(capabilitiesDescriptor, outputMode);
  }

  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor) {
    writeVersion(versionDescriptor, OutputMode.JSON);
  }

  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor, OutputMode outputMode) {
    discoveryWriter.writeVersion(versionDescriptor, outputMode);
  }

  void writeRequestTemplate(ContractTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    discoveryWriter.writeRequestTemplate(requestTemplate);
  }

  void writePlanTemplate(ContractTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    discoveryWriter.writePlanTemplate(planTemplate);
  }

  void writeFailure(CliFailure failure) {
    writeFailure(failure, OutputMode.JSON);
  }

  void writeFailure(CliFailure failure, OutputMode outputMode) {
    if (outputMode == OutputMode.HUMAN) {
      outputChannel.writeText(CliFailureOutputRenderer.renderFailureHuman(failure));
      return;
    }
    outputChannel.writeEnvelope(CliResponsePayloadMapper.failureEnvelope(failure), false);
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

  void writeRekeyBookResult(RekeyBookResult result) {
    writeRekeyBookResult(result, OutputMode.JSON);
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    mutationWriter.writeRekeyBookResult(result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result) {
    writeDeclareAccountResult(result, OutputMode.JSON);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    mutationWriter.writeDeclareAccountResult(result, outputMode);
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

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    queryWriter.writeTrialBalanceResult(result, outputMode);
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    queryWriter.writeAccountLedgerResult(result, outputMode);
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    queryWriter.writePeriodSummaryResult(result, outputMode);
  }

  void writeLedgerPlanResult(LedgerPlanResult result) {
    queryWriter.writeLedgerPlanResult(result);
  }

  void writeJson(Object value, boolean pretty) {
    outputChannel.writeJson(value, pretty);
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return CliQueryResponseSupport.planRejectionStatus(status);
  }
}
