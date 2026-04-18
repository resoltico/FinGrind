package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Writes deterministic JSON envelopes for FinGrind CLI responses. */
final class CliResponseWriter {
  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final PrintStream outputStream;

  CliResponseWriter(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  /** Writes the canonical help descriptor as a success envelope. */
  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor) {
    writeSuccess(helpDescriptor, true);
  }

  /** Writes the canonical capabilities descriptor as a success envelope. */
  void writeCapabilities(ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor) {
    writeSuccess(capabilitiesDescriptor, true);
  }

  /** Writes the canonical version descriptor as a success envelope. */
  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor) {
    writeSuccess(versionDescriptor, true);
  }

  /** Writes the canonical request-template descriptor as raw JSON. */
  void writeRequestTemplate(ContractTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    writeJson(requestTemplate, true);
  }

  /** Writes the canonical ledger-plan template descriptor as raw JSON. */
  void writePlanTemplate(ContractTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    writeJson(planTemplate, true);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(CliFailure failure) {
    writeEnvelope(CliResponsePayloadMapper.failureEnvelope(failure), false);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }

  /** Writes one entry write-boundary result as a deterministic JSON envelope. */
  void writePostEntryResult(PostEntryResult result) {
    Object envelope =
        switch (result) {
          case PostEntryResult.PreflightAccepted accepted ->
              CliResponsePayloadMapper.preflightEnvelope(accepted);
          case PostEntryResult.Committed committed ->
              CliResponsePayloadMapper.committedEnvelope(committed);
          case PostEntryResult.PreflightRejected rejected ->
              CliResponsePayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection());
          case PostEntryResult.CommitRejected rejected ->
              CliResponsePayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one explicit open-book result as a deterministic JSON envelope. */
  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    Object envelope =
        switch (result) {
          case OpenBookResult.Opened opened ->
              CliResponsePayloadMapper.successEnvelope(
                  new CliResponseJsonModels.OpenBookPayload(
                      bookFilePath.toAbsolutePath().normalize().toString(),
                      opened.initializedAt().toString()));
          case OpenBookResult.Rejected rejected ->
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one generated book-key-file result as a deterministic JSON envelope. */
  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    writeEnvelope(
        CliResponsePayloadMapper.successEnvelope(
            new CliResponseJsonModels.GeneratedBookKeyFilePayload(
                absolutePath(generatedKeyFile.bookKeyFilePath()),
                generatedKeyFile.encoding(),
                generatedKeyFile.entropyBits(),
                generatedKeyFile.permissions())),
        false);
  }

  /** Writes one explicit rekey-book result as a deterministic JSON envelope. */
  void writeRekeyBookResult(RekeyBookResult result) {
    Object envelope =
        switch (result) {
          case RekeyBookResult.Rekeyed rekeyed ->
              CliResponsePayloadMapper.successEnvelope(
                  new CliResponseJsonModels.RekeyBookPayload(absolutePath(rekeyed.bookFilePath())));
          case RekeyBookResult.Rejected rejected ->
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-declaration result as a deterministic JSON envelope. */
  void writeDeclareAccountResult(DeclareAccountResult result) {
    Object envelope =
        switch (result) {
          case DeclareAccountResult.Declared declared ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.accountPayload(declared.account()));
          case DeclareAccountResult.Rejected rejected ->
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one book-inspection snapshot as a deterministic JSON envelope. */
  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeEnvelope(
        CliResponsePayloadMapper.successEnvelope(
            CliResponsePayloadMapper.bookInspectionPayload(bookFilePath, inspection)),
        false);
  }

  /** Writes one account-listing result as a deterministic JSON envelope. */
  void writeListAccountsResult(ListAccountsResult result) {
    Object envelope =
        switch (result) {
          case ListAccountsResult.Listed listed ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.accountPagePayload(listed.page()));
          case ListAccountsResult.Rejected rejected ->
              CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one committed-posting lookup result as a deterministic JSON envelope. */
  void writeGetPostingResult(GetPostingResult result) {
    Object envelope =
        switch (result) {
          case GetPostingResult.Found found ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.postingPayload(found.postingFact()));
          case GetPostingResult.Rejected rejected ->
              CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one committed-posting page result as a deterministic JSON envelope. */
  void writeListPostingsResult(ListPostingsResult result) {
    Object envelope =
        switch (result) {
          case ListPostingsResult.Listed listed ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.postingPagePayload(listed.page()));
          case ListPostingsResult.Rejected rejected ->
              CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-balance result as a deterministic JSON envelope. */
  void writeAccountBalanceResult(AccountBalanceResult result) {
    Object envelope =
        switch (result) {
          case AccountBalanceResult.Reported reported ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.accountBalancePayload(reported.snapshot()));
          case AccountBalanceResult.Rejected rejected ->
              CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one ledger-plan execution result as a deterministic JSON envelope. */
  void writeLedgerPlanResult(LedgerPlanResult result) {
    Object envelope =
        switch (result) {
          case LedgerPlanResult.Succeeded succeeded ->
              new CliResponseJsonModels.SuccessEnvelope(
                  ProtocolStatuses.PLAN_COMMITTED,
                  CliResponsePayloadMapper.ledgerPlanPayload(succeeded));
          case LedgerPlanResult.Rejected rejected ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  rejected, ProtocolStatuses.PLAN_REJECTED);
          case LedgerPlanResult.AssertionFailed assertionFailed ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  assertionFailed, ProtocolStatuses.PLAN_ASSERTION_FAILED);
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one raw JSON document, optionally pretty-printed. */
  void writeJson(Object value, boolean pretty) {
    byte[] document =
        pretty
            ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
            : objectMapper.writeValueAsBytes(value);
    outputStream.write(document, 0, document.length);
    outputStream.println();
    outputStream.flush();
  }

  private void writeSuccess(Object payload, boolean pretty) {
    writeEnvelope(CliResponsePayloadMapper.successEnvelope(payload), pretty);
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return CliResponsePayloadMapper.planRejectionStatus(status);
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }

  private void writeEnvelope(Object envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }

  private static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
        .build();
  }
}
