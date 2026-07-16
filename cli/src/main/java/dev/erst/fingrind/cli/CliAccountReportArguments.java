package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses account-specific report commands that require one account code. */
final class CliAccountReportArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ACCOUNT_BALANCE_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.Request.ACCOUNT_CODE,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_TO,
              ProtocolOptions.ReportQuery.POSTING_COVERAGE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec ACCOUNT_LEDGER_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.Request.ACCOUNT_CODE,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_TO,
              ProtocolOptions.ReportQuery.POSTING_COVERAGE,
              ProtocolOptions.ReportQuery.LIMIT,
              ProtocolOptions.ReportQuery.CURSOR,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());

  private CliAccountReportArguments() {}

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    ParsedAccountReportArguments parsedArguments =
        parseAccountScopedReportArguments(arguments, ACCOUNT_BALANCE_ARGUMENTS);
    EffectiveDateRange resolvedEffectiveDateRange =
        validatedEffectiveDateRange(
            parsedArguments.effectiveDateFrom(), parsedArguments.effectiveDateTo());
    return new AccountBalance(
        parsedArguments.bookAccess(),
        new AccountBalanceQuery(
            parsedArguments.accountCode(),
            resolvedEffectiveDateRange,
            parsedArguments.postingCoverage()),
        parsedArguments.output());
  }

  static CliCommand parseAccountLedgerCommand(List<String> arguments) {
    ParsedAccountReportArguments parsedArguments =
        parseAccountScopedReportArguments(arguments, ACCOUNT_LEDGER_ARGUMENTS);
    EffectiveDateRange resolvedEffectiveDateRange =
        validatedEffectiveDateRange(
            parsedArguments.effectiveDateFrom(), parsedArguments.effectiveDateTo());
    return new AccountLedger(
        parsedArguments.bookAccess(),
        new AccountLedgerQuery(
            parsedArguments.accountCode(),
            resolvedEffectiveDateRange,
            parsedArguments.postingCoverage(),
            parsedArguments.limit(),
            parsedArguments.cursor()),
        parsedArguments.output());
  }

  private static ParsedAccountReportArguments parseAccountScopedReportArguments(
      List<String> arguments, CliBookArgumentParser.CommandArgumentSpec argumentSpec) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, argumentSpec);
    AccountReportOptionValues optionValues = new AccountReportOptionValues();
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      consumeAccountReportOption(argumentIterator.next(), argumentIterator, optionValues);
    }
    return optionValues.toParsedArguments(parsedArguments.bookAccess());
  }

  private static void consumeAccountReportOption(
      String argument,
      ListIterator<String> argumentIterator,
      AccountReportOptionValues optionValues) {
    switch (argument) {
      case ProtocolOptions.Request.ACCOUNT_CODE ->
          optionValues.accountCodeValue =
              CliSingleValueOptionRequirements.requireSingleTextOption(
                  optionValues.accountCodeValue,
                  ProtocolOptions.Request.ACCOUNT_CODE,
                  argumentIterator);
      case ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM ->
          optionValues.effectiveDateFrom =
              CliReportOptionArguments.requireDateOption(
                  optionValues.effectiveDateFrom,
                  argumentIterator,
                  ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM);
      case ProtocolOptions.DateRange.EFFECTIVE_DATE_TO ->
          optionValues.effectiveDateTo =
              CliReportOptionArguments.requireDateOption(
                  optionValues.effectiveDateTo,
                  argumentIterator,
                  ProtocolOptions.DateRange.EFFECTIVE_DATE_TO);
      case ProtocolOptions.ReportQuery.POSTING_COVERAGE ->
          optionValues.postingCoverage =
              CliOptionModes.requirePostingCoverage(optionValues.postingCoverage, argumentIterator);
      case ProtocolOptions.ReportQuery.LIMIT ->
          optionValues.limit =
              CliSingleValueOptionRequirements.requireSingleIntegerOption(
                  optionValues.limit, ProtocolOptions.ReportQuery.LIMIT, argumentIterator);
      case ProtocolOptions.ReportQuery.CURSOR ->
          optionValues.cursor =
              CliSingleValueOptionRequirements.requireSingleTextOption(
                  optionValues.cursor, ProtocolOptions.ReportQuery.CURSOR, argumentIterator);
      case ProtocolOptions.Presentation.OUTPUT ->
          optionValues.outputMode =
              CliReportOptionArguments.requireReportOutputMode(
                  optionValues.outputMode, argumentIterator);
      default ->
          optionValues.pdfOutPath =
              CliOptionModes.requirePdfOutPath(optionValues.pdfOutPath, argumentIterator);
    }
  }

  /** Mutable option accumulator used only while parsing an account-scoped report command. */
  private static final class AccountReportOptionValues {
    @Nullable private String accountCodeValue;
    @Nullable private LocalDate effectiveDateFrom;
    @Nullable private LocalDate effectiveDateTo;
    @Nullable private PostingCoverage postingCoverage;
    @Nullable private Integer limit;
    @Nullable private String cursor;
    @Nullable private OutputMode outputMode;
    @Nullable private Path pdfOutPath;

    private ParsedAccountReportArguments toParsedArguments(
        dev.erst.fingrind.contract.runtime.BookAccess bookAccess) {
      if (accountCodeValue == null) {
        throw CliArgumentValueParser.invalid(
            ProtocolOptions.Request.ACCOUNT_CODE,
            "A " + ProtocolOptions.Request.ACCOUNT_CODE + " argument is required.");
      }
      String requiredAccountCodeValue = accountCodeValue;
      AccountCode resolvedAccountCode =
          CliArgumentValueParser.requireValidArgument(
              ProtocolOptions.Request.ACCOUNT_CODE,
              () -> new AccountCode(requiredAccountCodeValue));
      return new ParsedAccountReportArguments(
          bookAccess,
          resolvedAccountCode,
          effectiveDateFrom,
          effectiveDateTo,
          postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage,
          CliArgumentValueParser.requirePageLimit(
              limit == null ? ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT : limit,
              ProtocolOptions.ReportQuery.LIMIT),
          java.util.Optional.ofNullable(cursor).map(CliOptionModes::accountLedgerPageCursor),
          CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
    }
  }

  private record ParsedAccountReportArguments(
      dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      PostingCoverage postingCoverage,
      int limit,
      java.util.Optional<dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor> cursor,
      CliReportOutput output) {}

  private static EffectiveDateRange validatedEffectiveDateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    if (effectiveDateFrom != null && effectiveDateTo != null) {
      CliArgumentValueParser.requireOrderedDateRange(
          effectiveDateFrom,
          effectiveDateTo,
          ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
          ProtocolOptions.DateRange.EFFECTIVE_DATE_TO);
    }
    return EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo);
  }
}
