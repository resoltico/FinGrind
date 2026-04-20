package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses account-specific report commands that require one account code. */
final class CliAccountReportArguments {
  private CliAccountReportArguments() {}

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    ParsedAccountReportArguments parsedArguments = parseAccountScopedReportArguments(arguments);
    EffectiveDateRange resolvedEffectiveDateRange =
        CliArgumentSupport.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            () ->
                EffectiveDateRange.of(
                    Optional.ofNullable(parsedArguments.effectiveDateFrom()),
                    Optional.ofNullable(parsedArguments.effectiveDateTo())));
    return new CliCommand.AccountBalance(
        parsedArguments.bookAccess(),
        new AccountBalanceQuery(parsedArguments.accountCode(), resolvedEffectiveDateRange),
        parsedArguments.output());
  }

  static CliCommand parseAccountLedgerCommand(List<String> arguments) {
    ParsedAccountReportArguments parsedArguments = parseAccountScopedReportArguments(arguments);
    EffectiveDateRange resolvedEffectiveDateRange =
        CliArgumentSupport.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            () ->
                EffectiveDateRange.of(
                    Optional.ofNullable(parsedArguments.effectiveDateFrom()),
                    Optional.ofNullable(parsedArguments.effectiveDateTo())));
    return new CliCommand.AccountLedger(
        parsedArguments.bookAccess(),
        new AccountLedgerQuery(parsedArguments.accountCode(), resolvedEffectiveDateRange),
        parsedArguments.output());
  }

  private static ParsedAccountReportArguments parseAccountScopedReportArguments(
      List<String> arguments) {
    CliBookArgumentSupport.ParsedBookArguments parsedArguments =
        CliBookArgumentSupport.parseBookAndCommandArguments(arguments);
    @Nullable String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.ACCOUNT_CODE -> {
          if (accountCodeValue != null) {
            throw CliArgumentSupport.invalid(
                ProtocolOptions.ACCOUNT_CODE,
                "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
          }
          accountCodeValue =
              CliArgumentSupport.requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        }
        case ProtocolOptions.EFFECTIVE_DATE_FROM ->
            effectiveDateFrom =
                CliReportArguments.requireDateOption(
                    effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        case ProtocolOptions.EFFECTIVE_DATE_TO ->
            effectiveDateTo =
                CliReportArguments.requireDateOption(
                    effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        case ProtocolOptions.OUTPUT ->
            outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        case ProtocolOptions.PDF_OUT ->
            pdfOutPath = CliArgumentSupport.requirePdfOutPath(pdfOutPath, argumentIterator);
        default -> throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (accountCodeValue == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.ACCOUNT_CODE,
          "A " + ProtocolOptions.ACCOUNT_CODE + " argument is required.");
    }
    String requiredAccountCodeValue = accountCodeValue;
    AccountCode resolvedAccountCode =
        CliArgumentSupport.requireValidArgument(
            ProtocolOptions.ACCOUNT_CODE, () -> new AccountCode(requiredAccountCodeValue));
    return new ParsedAccountReportArguments(
        parsedArguments.bookAccess(),
        resolvedAccountCode,
        effectiveDateFrom,
        effectiveDateTo,
        CliArgumentSupport.resolvedReportOutput(outputMode, pdfOutPath));
  }

  private record ParsedAccountReportArguments(
      dev.erst.fingrind.contract.BookAccess bookAccess,
      AccountCode accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      CliCommand.ReportOutput output) {}
}
