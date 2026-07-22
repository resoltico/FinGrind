package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.ListIterator;
import java.util.UUID;

/** Parses founder credential triplets under one explicit custody selection for opening a book. */
final class CliOpenBookFounderArguments {
  private CliOpenBookFounderArguments() {}

  static boolean apply(
      CliOpenBookArgumentValues values, String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.Attestation.CUSTODIAN -> {
        if (values.founders.custodian != null) {
          throw CliArgumentValueParser.invalid(argument, "Duplicate argument: " + argument);
        }
        values.founders.custodian = CliAttestationCustodianArgument.require(argumentIterator);
      }
      case ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID ->
          values.founders.principalIds.add(
              CliArgumentValueParser.requireValidArgument(
                  ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                  () ->
                      UUID.fromString(
                          CliOptionValues.requireValue(
                              argumentIterator,
                              ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID))));
      case ProtocolOptions.Attestation.FOUNDER_KEY_FILE ->
          values.founders.keyFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.FOUNDER_KEY_FILE));
      case ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE ->
          values.founders.passphraseFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE));
      case ProtocolOptions.Presentation.OUTPUT ->
          values.outputMode =
              CliOptionModes.requireOutputMode(
                  values.outputMode,
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default -> {
        return false;
      }
    }
    return true;
  }
}
