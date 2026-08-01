package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Executes configured report handlers with uniform prompting, rendering, and PDF export. */
final class CliReportCommandExecutor {
  private final CliReportCommandCatalog handlers;
  private final CliFailureResponseWriter failureWriter;
  private final CliPdfReportExporter pdfReportExporter;
  private final Clock clock;

  CliReportCommandExecutor(
      CliReportResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookReadWorkflow readWorkflow,
      CliPdfReportExporter pdfReportExporter,
      Clock clock) {
    CliReportResponseWriter requiredResponseWriter =
        Objects.requireNonNull(responseWriter, "responseWriter");
    CliBookReadWorkflow requiredReadWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
    this.handlers = new CliReportCommandCatalog(requiredReadWorkflow, requiredResponseWriter);
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  CliReportCommandCatalog handlers() {
    return handlers;
  }

  int runInventoryValuationCommand(
      BookAccess bookAccess, InventoryValuationQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(
        bookAccess,
        query,
        output,
        CliOperationalReportCommandHandlers.inventoryValuation(
            handlers.readWorkflow(), handlers.responseWriter()));
  }

  int runAccrualCutoffScheduleCommand(
      BookAccess bookAccess, AccrualCutoffScheduleQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(
        bookAccess,
        query,
        output,
        CliOperationalReportCommandHandlers.accrualCutoffSchedule(
            handlers.readWorkflow(), handlers.responseWriter()));
  }

  int runFixedAssetRegisterCommand(
      BookAccess bookAccess, FixedAssetRegisterQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(
        bookAccess,
        query,
        output,
        CliOperationalReportCommandHandlers.fixedAssetRegister(
            handlers.readWorkflow(), handlers.responseWriter()));
  }

  int runLatvianPayrollRegisterCommand(
      BookAccess bookAccess, LatvianPayrollRegisterQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(
        bookAccess,
        query,
        output,
        CliOperationalReportCommandHandlers.latvianPayrollRegister(
            handlers.readWorkflow(), handlers.responseWriter()));
  }

  <QUERY, RESULT, REPORTED> int runConfiguredReportCommand(
      BookAccess bookAccess,
      QUERY query,
      CliReportOutput output,
      CliConfiguredReportHandler<QUERY, RESULT, REPORTED> handler) {
    return runReportCommand(
        bookAccess,
        output,
        () -> handler.workflowCall().run(bookAccess, query),
        handler.reportedValue(),
        handler.reportModelBuilder(),
        handler.resultWriter(),
        handler.successExitCode());
  }

  private <RESULT, REPORTED> int runReportCommand(
      BookAccess bookAccess,
      CliReportOutput output,
      Supplier<ContractDecision<RESULT>> resultSupplier,
      CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
      Function<REPORTED, ReportModel> reportModelBuilder,
      CliConfiguredReportHandler.ResultWriter<RESULT> writeResult,
      ToIntFunction<RESULT> successExitCode) {
    return runPromptedReportCommand(
        bookAccess,
        output,
        resultSupplier,
        result ->
            exportReportedResult(result, output.pdfOutPath(), reportedValue, reportModelBuilder),
        (result, exportedArtifact) ->
            writeResult.write(result, output.outputMode(), exportedArtifact, clock.instant()),
        successExitCode);
  }

  private <RESULT> int runPromptedReportCommand(
      BookAccess bookAccess,
      CliReportOutput output,
      Supplier<ContractDecision<RESULT>> resultSupplier,
      Function<RESULT, @Nullable ArtifactPublicationResult> exportAction,
      BiConsumer<RESULT, @Nullable ArtifactPublicationResult> writeResult,
      ToIntFunction<RESULT> successExitCode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, output.outputMode()));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        resultSupplier.get(),
        result -> {
          @Nullable ArtifactPublicationResult exportedArtifact = exportAction.apply(result);
          writeResult.accept(result, exportedArtifact);
        },
        successExitCode,
        failureWriter,
        output.outputMode());
  }

  private <RESULT, REPORTED> @Nullable ArtifactPublicationResult exportReportedResult(
      RESULT result,
      @Nullable Path outputPath,
      CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
      Function<REPORTED, ReportModel> reportModelBuilder) {
    if (outputPath == null) {
      return null;
    }
    REPORTED reported = reportedValue.apply(result);
    return reported == null
        ? null
        : pdfReportExporter.export(outputPath, reportModelBuilder.apply(reported));
  }
}
