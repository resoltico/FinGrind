package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
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
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return new InspectBook(
        parsedArguments.bookAccess(), CliArgumentValueParser.resolvedOutputMode(outputMode));
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
        postingIdValue =
            CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.POSTING_ID);
        continue;
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
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
        CliArgumentValueParser.resolvedOutputMode(outputMode));
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
            CliArgumentValueParser.parseIntegerOption(
                CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.LIMIT),
                ProtocolOptions.LIMIT);
        continue;
      }
      if (ProtocolOptions.CURSOR.equals(argument)) {
        if (cursor != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.CURSOR, "Duplicate argument: " + ProtocolOptions.CURSOR);
        }
        cursor = CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.CURSOR);
        continue;
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(
                  OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV));
    }
    int resolvedLimit = limit == null ? InteractionLimits.DEFAULT_PAGE_LIMIT : limit;
    Optional<AccountPageCursor> resolvedCursor =
        Optional.ofNullable(cursor).map(CliArgumentValueParser::accountPageCursor);
    return new ListAccounts(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.LIMIT, () -> new ListAccountsQuery(resolvedLimit, resolvedCursor)),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseListPostingsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, LIST_POSTINGS_ARGUMENTS);
    @Nullable String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    Integer limit = null;
    @Nullable String cursor = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.ACCOUNT_CODE.equals(argument)) {
        if (accountCodeValue != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.ACCOUNT_CODE, "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
        }
        accountCodeValue =
            CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        if (effectiveDateFrom != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_FROM);
        }
        effectiveDateFrom =
            CliArgumentValueParser.parseLocalDateOption(
                CliArgumentValueParser.requireValue(
                    argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM),
                ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        if (effectiveDateTo != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.EFFECTIVE_DATE_TO,
              "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_TO);
        }
        effectiveDateTo =
            CliArgumentValueParser.parseLocalDateOption(
                CliArgumentValueParser.requireValue(
                    argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO),
                ProtocolOptions.EFFECTIVE_DATE_TO);
        continue;
      }
      if (ProtocolOptions.LIMIT.equals(argument)) {
        if (limit != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
        }
        limit =
            CliArgumentValueParser.parseIntegerOption(
                CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.LIMIT),
                ProtocolOptions.LIMIT);
        continue;
      }
      if (ProtocolOptions.CURSOR.equals(argument)) {
        if (cursor != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.CURSOR, "Duplicate argument: " + ProtocolOptions.CURSOR);
        }
        cursor = CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.CURSOR);
        continue;
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(
                  OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV));
    }
    String resolvedAccountCodeValue = accountCodeValue;
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    int resolvedLimit = limit == null ? InteractionLimits.DEFAULT_PAGE_LIMIT : limit;
    String resolvedCursor = cursor;
    Optional<AccountCode> resolvedAccountCode =
        Optional.ofNullable(resolvedAccountCodeValue)
            .map(
                value ->
                    CliArgumentValueParser.requireValidArgument(
                        ProtocolOptions.ACCOUNT_CODE, () -> new AccountCode(value)));
    EffectiveDateRange resolvedEffectiveDateRange =
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            () -> EffectiveDateRange.of(resolvedEffectiveDateFrom, resolvedEffectiveDateTo));
    Optional<dev.erst.fingrind.contract.PostingPageCursor> resolvedPostingPageCursor =
        Optional.ofNullable(resolvedCursor).map(CliArgumentValueParser::postingPageCursor);
    return new ListPostings(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.LIMIT,
            () ->
                new ListPostingsQuery(
                    resolvedAccountCode,
                    resolvedEffectiveDateRange,
                    resolvedLimit,
                    resolvedPostingPageCursor)),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }
}
