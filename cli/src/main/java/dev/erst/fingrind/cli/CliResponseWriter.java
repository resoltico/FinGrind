package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Writes deterministic JSON envelopes for FinGrind CLI responses. */
final class CliResponseWriter {
  private static final Object WRITTEN = new Object();
  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final PrintStream outputStream;

  CliResponseWriter(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  /** Writes the canonical help descriptor as a success envelope. */
  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor) {
    writeHelp(helpDescriptor, OutputMode.JSON);
  }

  /** Writes the canonical help descriptor in the selected output mode. */
  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> writeSuccess(helpDescriptor, true),
        () -> writeText(CliDiscoveryOutputRenderer.renderHelpHuman(helpDescriptor)),
        () -> {
          throw new IllegalArgumentException("help does not support CSV output.");
        });
  }

  /** Writes the canonical capabilities descriptor as a success envelope. */
  void writeCapabilities(ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor) {
    writeCapabilities(capabilitiesDescriptor, OutputMode.JSON);
  }

  /** Writes the canonical capabilities descriptor in the selected output mode. */
  void writeCapabilities(
      ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> writeSuccess(capabilitiesDescriptor, true),
        () -> writeText(CliDiscoveryOutputRenderer.renderCapabilitiesHuman(capabilitiesDescriptor)),
        () -> {
          throw new IllegalArgumentException("capabilities does not support CSV output.");
        });
  }

  /** Writes the canonical version descriptor as a success envelope. */
  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor) {
    writeVersion(versionDescriptor, OutputMode.JSON);
  }

  /** Writes the canonical version descriptor in the selected output mode. */
  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> writeSuccess(versionDescriptor, true),
        () -> writeText(CliDiscoveryOutputRenderer.renderVersionHuman(versionDescriptor)),
        () -> {
          throw new IllegalArgumentException("version does not support CSV output.");
        });
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
    writeFailure(failure, OutputMode.JSON);
  }

  /** Writes one deterministic failure in the selected output mode. */
  void writeFailure(CliFailure failure, OutputMode outputMode) {
    if (outputMode == OutputMode.HUMAN) {
      writeText(CliFailureOutputRenderer.renderFailureHuman(failure));
      return;
    }
    writeEnvelope(CliResponsePayloadMapper.failureEnvelope(failure), false);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }

  /** Writes one entry write-boundary result as a deterministic JSON envelope. */
  void writePostEntryResult(PostEntryResult result) {
    writePostEntryResult(result, OutputMode.JSON);
  }

  /** Writes one entry write-boundary result in the selected output mode. */
  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    Object envelope =
        switch (result) {
          case PostEntryResult.PreflightAccepted accepted -> {
            outputMode.run(
                () -> writeEnvelope(CliResponsePayloadMapper.preflightEnvelope(accepted), false),
                () -> writeText(CliMutationOutputRenderer.renderPreflightAcceptedHuman(accepted)),
                () -> {
                  throw new IllegalArgumentException("entry success does not support CSV output.");
                });
            yield WRITTEN;
          }
          case PostEntryResult.Committed committed -> {
            outputMode.run(
                () -> writeEnvelope(CliResponsePayloadMapper.committedEnvelope(committed), false),
                () -> writeText(CliMutationOutputRenderer.renderCommittedHuman(committed)),
                () -> {
                  throw new IllegalArgumentException("entry success does not support CSV output.");
                });
            yield WRITTEN;
          }
          case PostEntryResult.PreflightRejected rejected ->
              rejectedEnvelope(
                  outputMode,
                  CliResponsePayloadMapper.postingRejectedEnvelope(
                      rejected.requestIdempotencyKey().value(), rejected.rejection()),
                  rejected.requestIdempotencyKey().value());
          case PostEntryResult.CommitRejected rejected ->
              rejectedEnvelope(
                  outputMode,
                  CliResponsePayloadMapper.postingRejectedEnvelope(
                      rejected.requestIdempotencyKey().value(), rejected.rejection()),
                  rejected.requestIdempotencyKey().value());
        };
    if (envelope != WRITTEN) {
      writeEnvelope(envelope, false);
    }
  }

  /** Writes one explicit open-book result as a deterministic JSON envelope. */
  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    writeOpenBookResult(bookFilePath, result, OutputMode.JSON);
  }

  /** Writes one explicit open-book result in the selected output mode. */
  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    Object envelope =
        switch (result) {
          case OpenBookResult.Opened opened -> {
            outputMode.run(
                () ->
                    writeEnvelope(
                        CliResponsePayloadMapper.successEnvelope(
                            new CliResponseJsonModels.OpenBookPayload(
                                bookFilePath.toAbsolutePath().normalize().toString(),
                                opened.initializedAt().toString())),
                        false),
                () ->
                    writeText(CliMutationOutputRenderer.renderOpenBookHuman(bookFilePath, opened)),
                () -> {
                  throw new IllegalArgumentException("open-book does not support CSV output.");
                });
            yield WRITTEN;
          }
          case OpenBookResult.Rejected rejected ->
              rejectedEnvelope(
                  outputMode,
                  CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
        };
    if (envelope != WRITTEN) {
      writeEnvelope(envelope, false);
    }
  }

  /** Writes one generated book-key-file result as a deterministic JSON envelope. */
  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.JSON);
  }

  /** Writes one generated book-key-file result in the selected output mode. */
  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile, OutputMode outputMode) {
    outputMode.run(
        () ->
            writeEnvelope(
                CliResponsePayloadMapper.successEnvelope(
                    new CliResponseJsonModels.GeneratedBookKeyFilePayload(
                        absolutePath(generatedKeyFile.bookKeyFilePath()),
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions())),
                false),
        () ->
            writeText(CliMutationOutputRenderer.renderGeneratedBookKeyFileHuman(generatedKeyFile)),
        () -> {
          throw new IllegalArgumentException("generate-book-key-file does not support CSV output.");
        });
  }

  /** Writes one explicit rekey-book result as a deterministic JSON envelope. */
  void writeRekeyBookResult(RekeyBookResult result) {
    writeRekeyBookResult(result, OutputMode.JSON);
  }

  /** Writes one explicit rekey-book result in the selected output mode. */
  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    Object envelope =
        switch (result) {
          case RekeyBookResult.Rekeyed rekeyed -> {
            outputMode.run(
                () ->
                    writeEnvelope(
                        CliResponsePayloadMapper.successEnvelope(
                            new CliResponseJsonModels.RekeyBookPayload(
                                absolutePath(rekeyed.bookFilePath()))),
                        false),
                () -> writeText(CliMutationOutputRenderer.renderRekeyBookHuman(rekeyed)),
                () -> {
                  throw new IllegalArgumentException("rekey-book does not support CSV output.");
                });
            yield WRITTEN;
          }
          case RekeyBookResult.Rejected rejected ->
              rejectedEnvelope(
                  outputMode,
                  CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
        };
    if (envelope != WRITTEN) {
      writeEnvelope(envelope, false);
    }
  }

  /** Writes one account-declaration result as a deterministic JSON envelope. */
  void writeDeclareAccountResult(DeclareAccountResult result) {
    writeDeclareAccountResult(result, OutputMode.JSON);
  }

  /** Writes one account-declaration result in the selected output mode. */
  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    Object envelope =
        switch (result) {
          case DeclareAccountResult.Declared declared -> {
            outputMode.run(
                () ->
                    writeEnvelope(
                        CliResponsePayloadMapper.successEnvelope(
                            CliResponsePayloadMapper.accountPayload(declared.account())),
                        false),
                () ->
                    writeText(
                        CliMutationOutputRenderer.renderDeclaredAccountHuman(declared.account())),
                () -> {
                  throw new IllegalArgumentException(
                      "declare-account does not support CSV output.");
                });
            yield WRITTEN;
          }
          case DeclareAccountResult.Rejected rejected ->
              rejectedEnvelope(
                  outputMode,
                  CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
        };
    if (envelope != WRITTEN) {
      writeEnvelope(envelope, false);
    }
  }

  /** Writes one book-inspection snapshot as a deterministic JSON envelope. */
  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeBookInspection(bookFilePath, inspection, OutputMode.JSON);
  }

  /** Writes one book-inspection snapshot in the selected output mode. */
  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    outputMode.run(
        () ->
            writeEnvelope(
                CliResponsePayloadMapper.successEnvelope(
                    CliResponsePayloadMapper.bookInspectionPayload(bookFilePath, inspection)),
                false),
        () -> writeText(CliQueryOutputRenderer.renderBookInspectionHuman(bookFilePath, inspection)),
        () -> {
          throw new IllegalArgumentException("inspect-book does not support CSV output.");
        });
  }

  /** Writes one account-listing result as a deterministic JSON envelope. */
  void writeListAccountsResult(ListAccountsResult result) {
    writeListAccountsResult(result, OutputMode.JSON);
  }

  /** Writes one account-listing result in the selected output mode. */
  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    result.fold(
        listed -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountPagePayload(listed.page())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderAccountsHuman(listed.page())),
              () -> writeText(CliQueryOutputRenderer.renderAccountsCsv(listed.page())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one committed-posting lookup result as a deterministic JSON envelope. */
  void writeGetPostingResult(GetPostingResult result) {
    writeGetPostingResult(result, OutputMode.JSON);
  }

  /** Writes one committed-posting lookup result in the selected output mode. */
  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    result.fold(
        found -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.postingPayload(found.postingFact())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderPostingHuman(found.postingFact())),
              () -> {
                throw new IllegalArgumentException("get-posting does not support CSV output.");
              });
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one committed-posting page result as a deterministic JSON envelope. */
  void writeListPostingsResult(ListPostingsResult result) {
    writeListPostingsResult(result, OutputMode.JSON);
  }

  /** Writes one committed-posting page result in the selected output mode. */
  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    result.fold(
        listed -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.postingPagePayload(listed.page())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderPostingRegisterHuman(listed.page())),
              () -> writeText(CliQueryOutputRenderer.renderPostingRegisterCsv(listed.page())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one account-balance result as a deterministic JSON envelope. */
  void writeAccountBalanceResult(AccountBalanceResult result) {
    writeAccountBalanceResult(result, OutputMode.JSON);
  }

  /** Writes one account-balance result in the selected output mode. */
  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountBalancePayload(reported.snapshot())),
                      false),
              () ->
                  writeText(CliQueryOutputRenderer.renderAccountBalanceHuman(reported.snapshot())),
              () -> writeText(CliQueryOutputRenderer.renderAccountBalanceCsv(reported.snapshot())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one trial-balance result using the selected output mode. */
  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.trialBalancePayload(reported.report())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderTrialBalanceHuman(reported.report())),
              () -> writeText(CliQueryOutputRenderer.renderTrialBalanceCsv(reported.report())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one account-ledger result using the selected output mode. */
  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountLedgerPayload(reported.report())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderAccountLedgerHuman(reported.report())),
              () -> writeText(CliQueryOutputRenderer.renderAccountLedgerCsv(reported.report())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
  }

  /** Writes one bounded period-summary result using the selected output mode. */
  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
          outputMode.run(
              () ->
                  writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.periodSummaryPayload(reported.report())),
                      false),
              () -> writeText(CliQueryOutputRenderer.renderPeriodSummaryHuman(reported.report())),
              () -> writeText(CliQueryOutputRenderer.renderPeriodSummaryCsv(reported.report())));
          return WRITTEN;
        },
        rejected -> {
          writeEnvelopeOrHumanRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return WRITTEN;
        });
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

  private void writeText(String value) {
    outputStream.print(value);
    outputStream.println();
    outputStream.flush();
  }

  private Object rejectedEnvelope(
      OutputMode outputMode, CliResponseJsonModels.RejectedEnvelope envelope) {
    return rejectedEnvelope(outputMode, envelope, envelope.idempotencyKey());
  }

  private Object rejectedEnvelope(
      OutputMode outputMode,
      CliResponseJsonModels.RejectedEnvelope envelope,
      @Nullable String idempotencyKey) {
    if (outputMode == OutputMode.HUMAN) {
      writeText(
          CliFailureOutputRenderer.renderRejectedHuman(
              envelope.code(), envelope.message(), idempotencyKey));
      return WRITTEN;
    }
    return envelope;
  }

  private void writeEnvelopeOrHumanRejection(
      OutputMode outputMode, CliResponseJsonModels.RejectedEnvelope envelope) {
    outputMode.run(
        () -> writeEnvelope(envelope, false),
        () ->
            writeText(
                CliFailureOutputRenderer.renderRejectedHuman(
                    envelope.code(), envelope.message(), envelope.idempotencyKey())),
        () -> writeEnvelope(envelope, false));
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
