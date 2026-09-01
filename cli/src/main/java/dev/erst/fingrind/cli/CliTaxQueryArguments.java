package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses tax-context read commands that query declared registrations and filing obligations. */
final class CliTaxQueryArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec LIST_TAX_REGISTRATIONS_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ReportQuery.LIMIT,
              ProtocolOptions.ReportQuery.CURSOR,
              ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.Presentation.WITH_CONTEXT));
  private static final CliBookArgumentParser.CommandArgumentSpec TAX_OBLIGATION_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.Request.TAX_REGISTRATION_ID,
              ProtocolOptions.DateRange.PERIOD_START,
              ProtocolOptions.DateRange.PERIOD_END,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
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
        parsedWindow.withContext(),
        parsedWindow.outputMode());
  }

  static CliCommand parseTaxObligationCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, TAX_OBLIGATION_ARGUMENTS);
    @Nullable String taxRegistrationIdValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Request.TAX_REGISTRATION_ID.equals(argument)) {
        taxRegistrationIdValue =
            CliSingleValueOptionRequirements.requireSingleTextOption(
                taxRegistrationIdValue,
                ProtocolOptions.Request.TAX_REGISTRATION_ID,
                argumentIterator);
        continue;
      }
      if (ProtocolOptions.DateRange.PERIOD_START.equals(argument)) {
        effectiveDateFrom =
            CliSingleValueOptionRequirements.requireSingleDateOption(
                effectiveDateFrom, ProtocolOptions.DateRange.PERIOD_START, argumentIterator);
        continue;
      }
      if (ProtocolOptions.DateRange.PERIOD_END.equals(argument)) {
        effectiveDateTo =
            CliSingleValueOptionRequirements.requireSingleDateOption(
                effectiveDateTo, ProtocolOptions.DateRange.PERIOD_END, argumentIterator);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    List<String> missingRequiredOptions = new java.util.ArrayList<>();
    if (taxRegistrationIdValue == null) {
      missingRequiredOptions.add(ProtocolOptions.Request.TAX_REGISTRATION_ID);
    }
    if (effectiveDateFrom == null) {
      missingRequiredOptions.add(ProtocolOptions.DateRange.PERIOD_START);
    }
    if (effectiveDateTo == null) {
      missingRequiredOptions.add(ProtocolOptions.DateRange.PERIOD_END);
    }
    if (!missingRequiredOptions.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          missingRequiredOptions.getFirst(),
          "Required arguments are missing: " + String.join(", ", missingRequiredOptions) + ".");
    }
    LocalDate requiredEffectiveDateFrom = Objects.requireNonNull(effectiveDateFrom);
    LocalDate requiredEffectiveDateTo = Objects.requireNonNull(effectiveDateTo);
    String requiredTaxRegistrationIdValue = Objects.requireNonNull(taxRegistrationIdValue);
    CliArgumentValueParser.requireOrderedDateRange(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        ProtocolOptions.DateRange.PERIOD_START,
        ProtocolOptions.DateRange.PERIOD_END);
    return new TaxObligation(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.Request.TAX_REGISTRATION_ID,
            () ->
                new TaxObligationQuery(
                    new TaxRegistrationId(requiredTaxRegistrationIdValue),
                    requiredEffectiveDateFrom,
                    requiredEffectiveDateTo)),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }
}
