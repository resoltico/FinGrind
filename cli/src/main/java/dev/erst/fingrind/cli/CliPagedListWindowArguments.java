package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses the shared limit/cursor/output window used by paged list-style read commands. */
final class CliPagedListWindowArguments {
  private CliPagedListWindowArguments() {}

  static ParsedListWindow parse(ListIterator<String> argumentIterator) {
    Integer limit = null;
    @Nullable String cursor = null;
    @Nullable OutputMode outputMode = null;
    boolean withContext = false;
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.LIMIT.equals(argument)) {
        limit =
            CliSingleValueOptionRequirements.requireSingleIntegerOption(
                limit, ProtocolOptions.LIMIT, argumentIterator);
        continue;
      }
      if (ProtocolOptions.CURSOR.equals(argument)) {
        cursor =
            CliSingleValueOptionRequirements.requireSingleTextOption(
                cursor, ProtocolOptions.CURSOR, argumentIterator);
        continue;
      }
      if (ProtocolOptions.WITH_CONTEXT.equals(argument)) {
        withContext = true;
        continue;
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(
                  OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
    }
    return new ParsedListWindow(
        CliArgumentValueParser.requirePageLimit(
            limit == null ? ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT : limit,
            ProtocolOptions.LIMIT),
        cursor,
        CliOptionModes.resolvedOutputMode(outputMode),
        withContext);
  }

  record ParsedListWindow(
      int limit, @Nullable String cursor, OutputMode outputMode, boolean withContext) {}
}
