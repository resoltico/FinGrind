package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses book-query CLI commands that inspect or read posting data without report exports. */
final class CliBookQueryArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec INSPECT_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(List.of(ProtocolOptions.OUTPUT), List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec GET_POSTING_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.POSTING_ID, ProtocolOptions.OUTPUT), List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_ACCOUNTS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.LIMIT, ProtocolOptions.CURSOR, ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_POSTINGS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ACCOUNT_CODE,
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.LIMIT,
              ProtocolOptions.CURSOR,
              ProtocolOptions.OUTPUT),
          List.of());

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
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
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
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.POSTING_ID.equals(argument)) {
        if (postingIdValue != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.POSTING_ID, "Duplicate argument: " + ProtocolOptions.POSTING_ID);
        }
        postingIdValue = CliOptionValues.requireValue(argumentIterator, ProtocolOptions.POSTING_ID);
        continue;
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    if (postingIdValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.POSTING_ID, "A " + ProtocolOptions.POSTING_ID + " argument is required.");
    }
    String requiredPostingIdValue = postingIdValue;
    return new GetPosting(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.POSTING_ID, () -> new PostingId(requiredPostingIdValue)),
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseListAccountsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, LIST_ACCOUNTS_ARGUMENTS);
    Integer limit = null;
    @Nullable String cursor = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.LIMIT.equals(argument)) {
        if (limit != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
        }
        limit =
            CliOptionValues.parseIntegerOption(
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.LIMIT),
                ProtocolOptions.LIMIT);
        continue;
      }
      if (ProtocolOptions.CURSOR.equals(argument)) {
        if (cursor != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.CURSOR, "Duplicate argument: " + ProtocolOptions.CURSOR);
        }
        cursor = CliOptionValues.requireValue(argumentIterator, ProtocolOptions.CURSOR);
        continue;
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(
                  OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
    }
    int resolvedLimit =
        CliArgumentValueParser.requirePageLimit(
            limit == null ? InteractionLimits.DEFAULT_PAGE_LIMIT : limit, ProtocolOptions.LIMIT);
    Optional<AccountPageCursor> resolvedCursor =
        Optional.ofNullable(cursor).map(CliOptionModes::accountPageCursor);
    return new ListAccounts(
        parsedArguments.bookAccess(),
        new ListAccountsQuery(resolvedLimit, resolvedCursor),
        CliOptionModes.resolvedOutputMode(outputMode));
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
                ? InteractionLimits.DEFAULT_PAGE_LIMIT
                : argumentValues.limit,
            ProtocolOptions.LIMIT);
    Optional<AccountCode> resolvedAccountCode =
        Optional.ofNullable(argumentValues.accountCodeValue)
            .map(
                value ->
                    CliArgumentValueParser.requireValidArgument(
                        ProtocolOptions.ACCOUNT_CODE, () -> new AccountCode(value)));
    if (argumentValues.effectiveDateFrom != null && argumentValues.effectiveDateTo != null) {
      CliArgumentValueParser.requireOrderedDateRange(
          argumentValues.effectiveDateFrom,
          argumentValues.effectiveDateTo,
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          ProtocolOptions.EFFECTIVE_DATE_TO);
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
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static void applyListPostingsArgument(
      ListPostingsArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    if (ProtocolOptions.ACCOUNT_CODE.equals(argument)) {
      argumentValues.accountCodeValue =
          requireSingleTextOption(
              argumentValues.accountCodeValue, ProtocolOptions.ACCOUNT_CODE, argumentIterator);
      return;
    }
    if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
      argumentValues.effectiveDateFrom =
          requireSingleDateOption(
              argumentValues.effectiveDateFrom,
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              argumentIterator);
      return;
    }
    if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
      argumentValues.effectiveDateTo =
          requireSingleDateOption(
              argumentValues.effectiveDateTo, ProtocolOptions.EFFECTIVE_DATE_TO, argumentIterator);
      return;
    }
    if (ProtocolOptions.LIMIT.equals(argument)) {
      argumentValues.limit =
          requireSingleIntegerOption(argumentValues.limit, ProtocolOptions.LIMIT, argumentIterator);
      return;
    }
    if (ProtocolOptions.CURSOR.equals(argument)) {
      argumentValues.cursor =
          requireSingleTextOption(argumentValues.cursor, ProtocolOptions.CURSOR, argumentIterator);
      return;
    }
    argumentValues.outputMode =
        CliOptionModes.requireOutputMode(
            argumentValues.outputMode,
            CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
            CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
  }

  private static String requireSingleTextOption(
      @Nullable String currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requireValue(argumentIterator, optionName);
  }

  private static LocalDate requireSingleDateOption(
      @Nullable LocalDate currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseLocalDateOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }

  private static Integer requireSingleIntegerOption(
      @Nullable Integer currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseIntegerOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }

  /** Mutable parse accumulator for list-postings command options before query resolution. */
  private static final class ListPostingsArgumentValues {
    private @Nullable String accountCodeValue;
    private @Nullable LocalDate effectiveDateFrom;
    private @Nullable LocalDate effectiveDateTo;
    private @Nullable Integer limit;
    private @Nullable String cursor;
    private @Nullable OutputMode outputMode;
  }
}
