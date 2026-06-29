package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses tax-context read commands that query declared registrations and filing obligations. */
final class CliTaxQueryArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_TAX_REGISTRATIONS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.LIMIT, ProtocolOptions.CURSOR, ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec TAX_OBLIGATION_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.TAX_REGISTRATION_ID,
              ProtocolOptions.PERIOD_START,
              ProtocolOptions.PERIOD_END,
              ProtocolOptions.OUTPUT),
          List.of());

  private CliTaxQueryArguments() {}

  static CliCommand parseListTaxRegistrationsCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            arguments, LIST_TAX_REGISTRATIONS_ARGUMENTS);
    CliPagedListWindowArguments.ParsedListWindow parsedWindow =
        CliPagedListWindowArguments.parse(parsedArguments.commandArguments().listIterator());
    Optional<TaxRegistrationPageCursor> resolvedCursor =
        Optional.ofNullable(parsedWindow.cursor()).map(CliOptionModes::taxRegistrationPageCursor);
    return new ListTaxRegistrations(
        parsedArguments.bookAccess(),
        new ListTaxRegistrationsQuery(parsedWindow.limit(), resolvedCursor),
        parsedWindow.outputMode());
  }

  static CliCommand parseTaxObligationCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, TAX_OBLIGATION_ARGUMENTS);
    @Nullable String taxRegistrationIdValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.TAX_REGISTRATION_ID.equals(argument)) {
        taxRegistrationIdValue =
            CliSingleValueOptionRequirements.requireSingleTextOption(
                taxRegistrationIdValue, ProtocolOptions.TAX_REGISTRATION_ID, argumentIterator);
        continue;
      }
      if (ProtocolOptions.PERIOD_START.equals(argument)) {
        effectiveDateFrom =
            CliSingleValueOptionRequirements.requireSingleDateOption(
                effectiveDateFrom, ProtocolOptions.PERIOD_START, argumentIterator);
        continue;
      }
      if (ProtocolOptions.PERIOD_END.equals(argument)) {
        effectiveDateTo =
            CliSingleValueOptionRequirements.requireSingleDateOption(
                effectiveDateTo, ProtocolOptions.PERIOD_END, argumentIterator);
        continue;
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(
                  OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
    }
    if (taxRegistrationIdValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_REGISTRATION_ID,
          "A " + ProtocolOptions.TAX_REGISTRATION_ID + " argument is required.");
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.PERIOD_START,
          "A " + ProtocolOptions.PERIOD_START + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.PERIOD_END, "A " + ProtocolOptions.PERIOD_END + " argument is required.");
    }
    CliArgumentValueParser.requireOrderedDateRange(
        effectiveDateFrom,
        effectiveDateTo,
        ProtocolOptions.PERIOD_START,
        ProtocolOptions.PERIOD_END);
    String requiredTaxRegistrationIdValue = taxRegistrationIdValue;
    LocalDate requiredEffectiveDateFrom = effectiveDateFrom;
    LocalDate requiredEffectiveDateTo = effectiveDateTo;
    return new TaxObligation(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.TAX_REGISTRATION_ID,
            () ->
                new TaxObligationQuery(
                    new TaxRegistrationId(requiredTaxRegistrationIdValue),
                    requiredEffectiveDateFrom,
                    requiredEffectiveDateTo)),
        CliOptionModes.resolvedOutputMode(outputMode));
  }
}
