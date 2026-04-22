package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Executes parsed non-discovery CLI commands against the configured FinGrind workflow. */
final class CliCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

  CliCommandExecutor(
      CliRequestReader requestReader,
      CliResponseWriter responseWriter,
      CliBookWorkflow bookWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    return SqliteBookKeyFileGenerator.generateDecision(bookKeyFilePath)
        .fold(
            generatedKeyFile -> {
              responseWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
              return 0;
            },
            failure -> writeDeterministicFailure(failure, outputMode));
  }

  int runOpenBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.openBook(bookAccess),
        outputMode,
        result -> responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runRekeyBookCommand(
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource),
        outputMode,
        result -> responseWriter.writeRekeyBookResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    return writeResolvedResult(
        bookWorkflow.declareAccount(bookAccess, command),
        outputMode,
        result -> responseWriter.writeDeclareAccountResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.inspectBook(bookAccess),
        outputMode,
        inspection ->
            responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode),
        ignored -> 0);
  }

  int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.listAccounts(bookAccess, query),
        outputMode,
        result -> responseWriter.writeListAccountsResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runGetPostingCommand(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId, OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.getPosting(bookAccess, postingId),
        outputMode,
        result -> responseWriter.writeGetPostingResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    return writeResolvedResult(
        bookWorkflow.listPostings(bookAccess, query),
        outputMode,
        result -> responseWriter.writeListPostingsResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    return writeResolvedResult(
        bookWorkflow.accountBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          exportAccountBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeAccountBalanceResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor);
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    return writeResolvedResult(
        bookWorkflow.trialBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          exportTrialBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeTrialBalanceResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor);
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    return writeResolvedResult(
        bookWorkflow.accountLedger(bookAccess, query),
        output.outputMode(),
        result -> {
          exportAccountLedgerIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeAccountLedgerResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor);
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    return writeResolvedResult(
        bookWorkflow.periodSummary(bookAccess, query),
        output.outputMode(),
        result -> {
          exportPeriodSummaryIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writePeriodSummaryResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor);
  }

  int runExecutePlanCommand(BookAccess bookAccess, Path requestFile) {
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    return writeResolvedResult(
        bookWorkflow.executePlan(bookAccess, plan),
        OutputMode.JSON,
        responseWriter::writeLedgerPlanResult,
        CliExecutionPolicy::exitCodeFor);
  }

  int runPreflightEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return writeResolvedResult(
        bookWorkflow.preflight(bookAccess, command),
        outputMode,
        result -> responseWriter.writePostEntryResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  int runPostEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return writeResolvedResult(
        bookWorkflow.commit(bookAccess, command),
        outputMode,
        result -> responseWriter.writePostEntryResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor);
  }

  private <T> int writeResolvedResult(
      ContractDecision<T> decision,
      OutputMode outputMode,
      Consumer<T> successWriter,
      ToIntFunction<T> successExitCode) {
    return decision.fold(
        result -> {
          successWriter.accept(result);
          return successExitCode.applyAsInt(result);
        },
        failure -> writeDeterministicFailure(failure, outputMode));
  }

  private int writeDeterministicFailure(
      dev.erst.fingrind.contract.ContractFailure failure, OutputMode outputMode) {
    responseWriter.writeFailure(CliFailureMapper.contractFailure(failure), outputMode);
    return CliExecutionPolicy.deterministicFailureExitCode();
  }

  private void exportAccountBalanceIfRequested(
      Path bookFilePath, AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountBalanceResult.Reported reported ->
          pdfReportExporter.exportAccountBalance(outputPath, bookFilePath, reported.snapshot());
      case AccountBalanceResult.Rejected _ -> {}
    }
  }

  private void exportTrialBalanceIfRequested(
      Path bookFilePath, TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case TrialBalanceResult.Reported reported ->
          pdfReportExporter.exportTrialBalance(outputPath, bookFilePath, reported.report());
      case TrialBalanceResult.Rejected _ -> {}
    }
  }

  private void exportAccountLedgerIfRequested(
      Path bookFilePath, AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountLedgerResult.Reported reported ->
          pdfReportExporter.exportAccountLedger(outputPath, bookFilePath, reported.report());
      case AccountLedgerResult.Rejected _ -> {}
    }
  }

  private void exportPeriodSummaryIfRequested(
      Path bookFilePath, PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case PeriodSummaryResult.Reported reported ->
          pdfReportExporter.exportPeriodSummary(outputPath, bookFilePath, reported.report());
      case PeriodSummaryResult.Rejected _ -> {}
    }
  }
}
