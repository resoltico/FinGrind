package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Parses the closed standalone attestation-credential custody command language. */
final class CliAttestationKeyFileArguments {
  private static final List<String> GENERATE_OPTIONS =
      List.of(
          ProtocolOptions.Attestation.CUSTODIAN,
          ProtocolOptions.Attestation.NEW_KEY_FILE,
          ProtocolOptions.Attestation.PASSPHRASE_FILE,
          ProtocolOptions.Presentation.OUTPUT);
  private static final List<String> INSPECT_OPTIONS =
      List.of(
          ProtocolOptions.Attestation.CUSTODIAN,
          ProtocolOptions.Attestation.KEY_FILE,
          ProtocolOptions.Presentation.OUTPUT);

  private CliAttestationKeyFileArguments() {}

  static CliCommand parseGenerateAttestationKeyFileCommand(List<String> arguments) {
    GenerateArguments parsed = new GenerateArguments();
    parseOptions(
        arguments,
        GENERATE_OPTIONS,
        Map.of(
            ProtocolOptions.Attestation.CUSTODIAN,
            parsed::readCustodian,
            ProtocolOptions.Attestation.NEW_KEY_FILE,
            parsed::readKeyFilePath,
            ProtocolOptions.Attestation.PASSPHRASE_FILE,
            parsed::readPassphraseFilePath,
            ProtocolOptions.Presentation.OUTPUT,
            parsed::readOutputMode));
    return parsed.command();
  }

  static CliCommand parseInspectAttestationKeyFileCommand(List<String> arguments) {
    InspectArguments parsed = new InspectArguments();
    parseOptions(
        arguments,
        INSPECT_OPTIONS,
        Map.of(
            ProtocolOptions.Attestation.CUSTODIAN,
            parsed::readCustodian,
            ProtocolOptions.Attestation.KEY_FILE,
            parsed::readKeyFilePath,
            ProtocolOptions.Presentation.OUTPUT,
            parsed::readOutputMode));
    return parsed.command();
  }

  private static void parseOptions(
      List<String> arguments,
      List<String> supportedOptions,
      Map<String, OptionValueReader> optionReaders) {
    ListIterator<String> iterator = arguments.listIterator(1);
    while (iterator.hasNext()) {
      String argument = iterator.next();
      OptionValueReader reader = optionReaders.get(argument);
      if (reader == null) {
        throw CliArgumentValueParser.unsupportedArgument(argument, supportedOptions);
      }
      reader.read(iterator);
    }
  }

  private static CliArgumentsException duplicate(String option) {
    return CliArgumentValueParser.invalid(option, "Duplicate argument: " + option);
  }

  private static CliArgumentsException required(String option) {
    return CliArgumentValueParser.invalid(option, "A " + option + " argument is required.");
  }

  /** Reads one option value from a command argument iterator. */
  @FunctionalInterface
  private interface OptionValueReader {
    /** Reads the selected option value. */
    void read(ListIterator<String> iterator);
  }

  /** Holds and validates the generation command's unique option values. */
  private static final class GenerateArguments {
    private @Nullable AttestationCustodian custodian;
    private @Nullable Path keyFilePath;
    private @Nullable Path passphraseFilePath;
    private @Nullable OutputMode outputMode;

    void readKeyFilePath(ListIterator<String> iterator) {
      if (keyFilePath != null) {
        throw duplicate(ProtocolOptions.Attestation.NEW_KEY_FILE);
      }
      keyFilePath =
          CliOptionValues.requirePathOptionValue(
              iterator, ProtocolOptions.Attestation.NEW_KEY_FILE);
    }

    void readCustodian(ListIterator<String> iterator) {
      if (custodian != null) {
        throw duplicate(ProtocolOptions.Attestation.CUSTODIAN);
      }
      custodian = CliAttestationCustodianArgument.require(iterator);
    }

    void readPassphraseFilePath(ListIterator<String> iterator) {
      if (passphraseFilePath != null) {
        throw duplicate(ProtocolOptions.Attestation.PASSPHRASE_FILE);
      }
      passphraseFilePath =
          CliOptionValues.requirePathOptionValue(
              iterator, ProtocolOptions.Attestation.PASSPHRASE_FILE);
    }

    void readOutputMode(ListIterator<String> iterator) {
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }

    CliCommand command() {
      if (custodian == null) {
        throw required(ProtocolOptions.Attestation.CUSTODIAN);
      }
      if (keyFilePath == null) {
        throw required(ProtocolOptions.Attestation.NEW_KEY_FILE);
      }
      if (passphraseFilePath == null) {
        throw required(ProtocolOptions.Attestation.PASSPHRASE_FILE);
      }
      return new GenerateAttestationKeyFile(
          custodian,
          keyFilePath,
          passphraseFilePath,
          CliOptionModes.resolvedOutputMode(outputMode));
    }
  }

  /** Holds and validates the inspection command's unique option values. */
  private static final class InspectArguments {
    private @Nullable AttestationCustodian custodian;
    private @Nullable Path keyFilePath;
    private @Nullable OutputMode outputMode;

    void readKeyFilePath(ListIterator<String> iterator) {
      if (keyFilePath != null) {
        throw duplicate(ProtocolOptions.Attestation.KEY_FILE);
      }
      keyFilePath =
          CliOptionValues.requirePathOptionValue(iterator, ProtocolOptions.Attestation.KEY_FILE);
    }

    void readCustodian(ListIterator<String> iterator) {
      if (custodian != null) {
        throw duplicate(ProtocolOptions.Attestation.CUSTODIAN);
      }
      custodian = CliAttestationCustodianArgument.require(iterator);
    }

    void readOutputMode(ListIterator<String> iterator) {
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }

    CliCommand command() {
      if (custodian == null) {
        throw required(ProtocolOptions.Attestation.CUSTODIAN);
      }
      if (keyFilePath == null) {
        throw required(ProtocolOptions.Attestation.KEY_FILE);
      }
      return new InspectAttestationKeyFile(
          custodian, keyFilePath, CliOptionModes.resolvedOutputMode(outputMode));
    }
  }
}
