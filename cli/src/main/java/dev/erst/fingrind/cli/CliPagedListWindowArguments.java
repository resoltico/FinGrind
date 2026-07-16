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
      if (ProtocolOptions.ReportQuery.LIMIT.equals(argument)) {
        limit =
            CliSingleValueOptionRequirements.requireSingleIntegerOption(
                limit, ProtocolOptions.ReportQuery.LIMIT, argumentIterator);
        continue;
      }
      if (ProtocolOptions.ReportQuery.CURSOR.equals(argument)) {
        cursor =
            CliSingleValueOptionRequirements.requireSingleTextOption(
                cursor, ProtocolOptions.ReportQuery.CURSOR, argumentIterator);
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
              CliOptionModes.supportedOutputModes(
                  OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
    }
    return new ParsedListWindow(
        CliArgumentValueParser.requirePageLimit(
            limit == null ? ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT : limit,
            ProtocolOptions.ReportQuery.LIMIT),
        cursor,
        CliOptionModes.resolvedOutputMode(outputMode),
        withContext);
  }

  record ParsedListWindow(
      int limit, @Nullable String cursor, OutputMode outputMode, boolean withContext) {}
}
