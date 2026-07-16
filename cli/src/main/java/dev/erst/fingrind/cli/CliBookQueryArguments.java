package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses book-query CLI commands that inspect or read posting data without report exports. */
final class CliBookQueryArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec INSPECT_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT), List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec GET_POSTING_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Request.POSTING_ID, ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.Presentation.WITH_CONTEXT));
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_ACCOUNTS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ReportQuery.LIMIT,
              ProtocolOptions.ReportQuery.CURSOR,
              ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.Presentation.WITH_CONTEXT));
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_POSTINGS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.Request.ACCOUNT_CODE,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_TO,
              ProtocolOptions.ReportQuery.LIMIT,
              ProtocolOptions.ReportQuery.CURSOR,
              ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.Presentation.WITH_CONTEXT));

  private CliBookQueryArguments() {}

  static CliCommand parseInspectBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, INSPECT_BOOK_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return new InspectBook(
        parsedArguments.bookAccess(), CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseGetPostingCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, GET_POSTING_ARGUMENTS);
    @Nullable String postingIdValue = null;
    @Nullable OutputMode outputMode = null;
    boolean withContext = false;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Request.POSTING_ID.equals(argument)) {
        if (postingIdValue != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.Request.POSTING_ID,
              "Duplicate argument: " + ProtocolOptions.Request.POSTING_ID);
        }
        postingIdValue =
            CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Request.POSTING_ID);
        continue;
      }
      if (ProtocolOptions.Presentation.WITH_CONTEXT.equals(argument)) {
        withContext = true;
        continue;
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    if (postingIdValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Request.POSTING_ID,
          "A " + ProtocolOptions.Request.POSTING_ID + " argument is required.");
    }
    String requiredPostingIdValue = postingIdValue;
    return new GetPosting(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.Request.POSTING_ID, () -> new PostingId(requiredPostingIdValue)),
        withContext,
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseListAccountsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, LIST_ACCOUNTS_ARGUMENTS);
    CliPagedListWindowArguments.ParsedListWindow parsedWindow =
        CliPagedListWindowArguments.parse(parsedArguments.commandArguments().listIterator());
    Optional<AccountPageCursor> resolvedCursor =
        Optional.ofNullable(parsedWindow.cursor()).map(CliOptionModes::accountPageCursor);
    return new ListAccounts(
        parsedArguments.bookAccess(),
        new ListAccountsQuery(parsedWindow.limit(), resolvedCursor),
        parsedWindow.withContext(),
        parsedWindow.outputMode());
  }

  static CliCommand parseListPostingsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, LIST_POSTINGS_ARGUMENTS);
    ListPostingsArgumentValues argumentValues = new ListPostingsArgumentValues();
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      applyListPostingsArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    int resolvedLimit =
        CliArgumentValueParser.requirePageLimit(
            argumentValues.limit == null
                ? ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT
                : argumentValues.limit,
            ProtocolOptions.ReportQuery.LIMIT);
    Optional<AccountCode> resolvedAccountCode =
        Optional.ofNullable(argumentValues.accountCodeValue)
            .map(
                value ->
                    CliArgumentValueParser.requireValidArgument(
                        ProtocolOptions.Request.ACCOUNT_CODE, () -> new AccountCode(value)));
    if (argumentValues.effectiveDateFrom != null && argumentValues.effectiveDateTo != null) {
      CliArgumentValueParser.requireOrderedDateRange(
          argumentValues.effectiveDateFrom,
          argumentValues.effectiveDateTo,
          ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
          ProtocolOptions.DateRange.EFFECTIVE_DATE_TO);
    }
    EffectiveDateRange resolvedEffectiveDateRange =
        EffectiveDateRange.of(argumentValues.effectiveDateFrom, argumentValues.effectiveDateTo);
    Optional<dev.erst.fingrind.contract.bookkeeping.PostingPageCursor> resolvedPostingPageCursor =
        Optional.ofNullable(argumentValues.cursor).map(CliOptionModes::postingPageCursor);
    return new ListPostings(
        parsedArguments.bookAccess(),
        new ListPostingsQuery(
            resolvedAccountCode,
            resolvedEffectiveDateRange,
            resolvedLimit,
            resolvedPostingPageCursor),
        argumentValues.withContext,
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static void applyListPostingsArgument(
      ListPostingsArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    if (ProtocolOptions.Request.ACCOUNT_CODE.equals(argument)) {
      argumentValues.accountCodeValue =
          CliSingleValueOptionRequirements.requireSingleTextOption(
              argumentValues.accountCodeValue,
              ProtocolOptions.Request.ACCOUNT_CODE,
              argumentIterator);
      return;
    }
    if (ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM.equals(argument)) {
      argumentValues.effectiveDateFrom =
          CliSingleValueOptionRequirements.requireSingleDateOption(
              argumentValues.effectiveDateFrom,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM,
              argumentIterator);
      return;
    }
    if (ProtocolOptions.DateRange.EFFECTIVE_DATE_TO.equals(argument)) {
      argumentValues.effectiveDateTo =
          CliSingleValueOptionRequirements.requireSingleDateOption(
              argumentValues.effectiveDateTo,
              ProtocolOptions.DateRange.EFFECTIVE_DATE_TO,
              argumentIterator);
      return;
    }
    if (ProtocolOptions.ReportQuery.LIMIT.equals(argument)) {
      argumentValues.limit =
          CliSingleValueOptionRequirements.requireSingleIntegerOption(
              argumentValues.limit, ProtocolOptions.ReportQuery.LIMIT, argumentIterator);
      return;
    }
    if (ProtocolOptions.ReportQuery.CURSOR.equals(argument)) {
      argumentValues.cursor =
          CliSingleValueOptionRequirements.requireSingleTextOption(
              argumentValues.cursor, ProtocolOptions.ReportQuery.CURSOR, argumentIterator);
      return;
    }
    if (ProtocolOptions.Presentation.WITH_CONTEXT.equals(argument)) {
      argumentValues.withContext = true;
      return;
    }
    argumentValues.outputMode =
        CliOptionModes.requireOutputMode(
            argumentValues.outputMode,
            CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
            CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
  }

  /** Mutable parse accumulator for list-postings command options before query resolution. */
  private static final class ListPostingsArgumentValues {
    private @Nullable String accountCodeValue;
    private @Nullable LocalDate effectiveDateFrom;
    private @Nullable LocalDate effectiveDateTo;
    private @Nullable Integer limit;
    private @Nullable String cursor;
    private @Nullable OutputMode outputMode;
    private boolean withContext;
  }
}
