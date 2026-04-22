package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses book-query CLI commands that inspect or read posting data without report exports. */
final class CliBookQueryArguments {
  private CliBookQueryArguments() {}

  static CliCommand parseInspectBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.OUTPUT.equals(argument)) {
        throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return new CliCommand.InspectBook(
        parsedArguments.bookAccess(), CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseGetPostingCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    @Nullable String postingIdValue = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.POSTING_ID -> {
          if (postingIdValue != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.POSTING_ID, "Duplicate argument: " + ProtocolOptions.POSTING_ID);
          }
          postingIdValue =
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.POSTING_ID);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (postingIdValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.POSTING_ID, "A " + ProtocolOptions.POSTING_ID + " argument is required.");
    }
    String requiredPostingIdValue = postingIdValue;
    return new CliCommand.GetPosting(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.POSTING_ID, () -> new PostingId(requiredPostingIdValue)),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseListAccountsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    Integer limit = null;
    Integer offset = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.LIMIT -> {
          if (limit != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
          }
          limit =
              CliArgumentValueParser.parseIntegerOption(
                  CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.LIMIT),
                  ProtocolOptions.LIMIT);
        }
        case ProtocolOptions.OFFSET -> {
          if (offset != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.OFFSET, "Duplicate argument: " + ProtocolOptions.OFFSET);
          }
          offset =
              CliArgumentValueParser.parseIntegerOption(
                  CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OFFSET),
                  ProtocolOptions.OFFSET);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(
                        OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    int resolvedLimit = limit == null ? ProtocolLimits.DEFAULT_PAGE_LIMIT : limit;
    int resolvedOffset = offset == null ? ProtocolLimits.DEFAULT_PAGE_OFFSET : offset;
    return new CliCommand.ListAccounts(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            resolvedOffset < ProtocolLimits.PAGE_OFFSET_MIN
                ? ProtocolOptions.OFFSET
                : ProtocolOptions.LIMIT,
            () -> new ListAccountsQuery(resolvedLimit, resolvedOffset)),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseListPostingsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    @Nullable String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    Integer limit = null;
    @Nullable String cursor = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.ACCOUNT_CODE -> {
          if (accountCodeValue != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.ACCOUNT_CODE,
                "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
          }
          accountCodeValue =
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        }
        case ProtocolOptions.EFFECTIVE_DATE_FROM -> {
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
        }
        case ProtocolOptions.EFFECTIVE_DATE_TO -> {
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
        }
        case ProtocolOptions.LIMIT -> {
          if (limit != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
          }
          limit =
              CliArgumentValueParser.parseIntegerOption(
                  CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.LIMIT),
                  ProtocolOptions.LIMIT);
        }
        case ProtocolOptions.CURSOR -> {
          if (cursor != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.CURSOR, "Duplicate argument: " + ProtocolOptions.CURSOR);
          }
          cursor = CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.CURSOR);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(
                        OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    String resolvedAccountCodeValue = accountCodeValue;
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    int resolvedLimit = limit == null ? ProtocolLimits.DEFAULT_PAGE_LIMIT : limit;
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
            () ->
                EffectiveDateRange.of(
                    Optional.ofNullable(resolvedEffectiveDateFrom),
                    Optional.ofNullable(resolvedEffectiveDateTo)));
    Optional<dev.erst.fingrind.contract.PostingPageCursor> resolvedPostingPageCursor =
        Optional.ofNullable(resolvedCursor).map(CliArgumentValueParser::postingPageCursor);
    return new CliCommand.ListPostings(
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
