package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses the four public non-mutating book-attestation operations. */
final class CliAttestationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec VERIFY_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.Attestation.REQUIRE_CLEAN));
  private static final CliBookArgumentParser.CommandArgumentSpec REVIEW_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT), List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec RECEIPT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Attestation.RECEIPT_FILE, ProtocolOptions.Presentation.OUTPUT),
          List.of());

  private CliAttestationArguments() {}

  static CliCommand parseVerifyBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, VERIFY_BOOK_ARGUMENTS);
    boolean requireClean = false;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> iterator = parsedArguments.commandArguments().listIterator();
    while (iterator.hasNext()) {
      String argument = iterator.next();
      if (ProtocolOptions.Attestation.REQUIRE_CLEAN.equals(argument)) {
        requireClean = true;
      } else {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      }
    }
    return new VerifyBookAttestation(
        parsedArguments.bookAccess(), requireClean, CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseAttestationReviewCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, REVIEW_ARGUMENTS);
    return new AttestationReview(
        parsedArguments.bookAccess(), parseOutput(parsedArguments.commandArguments()));
  }

  static CliCommand parseExportReceiptCommand(List<String> arguments) {
    return parseReceiptCommand(arguments, true);
  }

  static CliCommand parseVerifyReceiptCommand(List<String> arguments) {
    return parseReceiptCommand(arguments, false);
  }

  private static CliCommand parseReceiptCommand(List<String> arguments, boolean export) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, RECEIPT_ARGUMENTS);
    @Nullable Path receiptFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> iterator = parsedArguments.commandArguments().listIterator();
    while (iterator.hasNext()) {
      String argument = iterator.next();
      if (ProtocolOptions.Attestation.RECEIPT_FILE.equals(argument)) {
        if (receiptFilePath != null) {
          throw CliArgumentValueParser.invalid(argument, "Duplicate argument: " + argument);
        }
        receiptFilePath = CliOptionValues.requirePathOptionValue(iterator, argument);
      } else {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      }
    }
    if (receiptFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.RECEIPT_FILE,
          "A " + ProtocolOptions.Attestation.RECEIPT_FILE + " argument is required.");
    }
    return export
        ? new ExportAttestationReceipt(
            parsedArguments.bookAccess(),
            receiptFilePath,
            CliOptionModes.resolvedOutputMode(outputMode))
        : new VerifyAttestationReceipt(
            parsedArguments.bookAccess(),
            receiptFilePath,
            CliOptionModes.resolvedOutputMode(outputMode));
  }

  private static OutputMode parseOutput(List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    ListIterator<String> iterator = arguments.listIterator();
    while (iterator.hasNext()) {
      iterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return CliOptionModes.resolvedOutputMode(outputMode);
  }
}
