package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
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
  private static final CliBookArgumentParser.CommandArgumentSpec ACCOUNT_REPORT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ACCOUNT_CODE,
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.POSTING_COVERAGE,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());

  private CliAccountReportArguments() {}

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    ParsedAccountReportArguments parsedArguments = parseAccountScopedReportArguments(arguments);
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
    ParsedAccountReportArguments parsedArguments = parseAccountScopedReportArguments(arguments);
    EffectiveDateRange resolvedEffectiveDateRange =
        validatedEffectiveDateRange(
            parsedArguments.effectiveDateFrom(), parsedArguments.effectiveDateTo());
    return new AccountLedger(
        parsedArguments.bookAccess(),
        new AccountLedgerQuery(
            parsedArguments.accountCode(),
            resolvedEffectiveDateRange,
            parsedArguments.postingCoverage()),
        parsedArguments.output());
  }

  private static ParsedAccountReportArguments parseAccountScopedReportArguments(
      List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, ACCOUNT_REPORT_ARGUMENTS);
    @Nullable String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable PostingCoverage postingCoverage = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.ACCOUNT_CODE.equals(argument)) {
        if (accountCodeValue != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.ACCOUNT_CODE, "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
        }
        accountCodeValue =
            CliOptionValues.requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        continue;
      }
      if (ProtocolOptions.POSTING_COVERAGE.equals(argument)) {
        postingCoverage = CliOptionModes.requirePostingCoverage(postingCoverage, argumentIterator);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    if (accountCodeValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ACCOUNT_CODE,
          "A " + ProtocolOptions.ACCOUNT_CODE + " argument is required.");
    }
    String requiredAccountCodeValue = accountCodeValue;
    AccountCode resolvedAccountCode =
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.ACCOUNT_CODE, () -> new AccountCode(requiredAccountCodeValue));
    return new ParsedAccountReportArguments(
        parsedArguments.bookAccess(),
        resolvedAccountCode,
        effectiveDateFrom,
        effectiveDateTo,
        postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage,
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }

  private record ParsedAccountReportArguments(
      dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      PostingCoverage postingCoverage,
      CliReportOutput output) {}

  private static EffectiveDateRange validatedEffectiveDateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    if (effectiveDateFrom != null && effectiveDateTo != null) {
      CliArgumentValueParser.requireOrderedDateRange(
          effectiveDateFrom,
          effectiveDateTo,
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          ProtocolOptions.EFFECTIVE_DATE_TO);
    }
    return EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo);
  }
}
