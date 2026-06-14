package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Resolves the default stdout mode for commands that support explicit output selection. */
final class CliOutputModeDefaults {
  static final String DEFAULT_OUTPUT_ENVIRONMENT_VARIABLE = "FINGRIND_DEFAULT_OUTPUT";

  private CliOutputModeDefaults() {}

  static OutputMode resolved(@Nullable OutputMode explicitOutputMode, OutputSurface outputSurface) {
    return explicitOutputMode == null ? outputDefault(outputSurface).mode() : explicitOutputMode;
  }

  static OutputMode inferredDefault(OutputSurface outputSurface) {
    return inferredDefault(configuredOutputValue(), outputSurface);
  }

  static OutputMode inferredDefault(
      @Nullable String rawConfiguredOutput, OutputSurface outputSurface) {
    if (rawConfiguredOutput == null || rawConfiguredOutput.isBlank()) {
      return outputSurface.builtInDefault();
    }
    try {
      return parseConfiguredOutputMode(rawConfiguredOutput);
    } catch (CliArgumentsException exception) {
      return outputSurface.builtInDefault();
    }
  }

  static OutputDefault outputDefault(OutputSurface outputSurface) {
    return outputDefault(configuredOutputValue(), outputSurface);
  }

  static OutputDefault outputDefault(
      @Nullable String rawConfiguredOutput, OutputSurface outputSurface) {
    if (rawConfiguredOutput == null || rawConfiguredOutput.isBlank()) {
      return new OutputDefault(outputSurface.builtInDefault(), null);
    }
    return new OutputDefault(
        parseConfiguredOutputMode(rawConfiguredOutput), DEFAULT_OUTPUT_ENVIRONMENT_VARIABLE);
  }

  private static @Nullable String configuredOutputValue() {
    return System.getenv(DEFAULT_OUTPUT_ENVIRONMENT_VARIABLE);
  }

  private static OutputMode parseConfiguredOutputMode(String rawConfiguredOutput) {
    String normalizedOutputMode = rawConfiguredOutput.strip().toLowerCase(Locale.ROOT);
    try {
      return OutputMode.fromWireValue(normalizedOutputMode);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalidEnvironmentSelection(
          DEFAULT_OUTPUT_ENVIRONMENT_VARIABLE,
          "Unsupported value for "
              + DEFAULT_OUTPUT_ENVIRONMENT_VARIABLE
              + ": "
              + rawConfiguredOutput
              + ". Accepted values: "
              + String.join(", ", supportedConfiguredOutputModes())
              + ".",
          exception);
    }
  }

  private static List<String> supportedConfiguredOutputModes() {
    return List.of(OutputMode.TEXT.wireValue(), OutputMode.JSON.wireValue());
  }

  /** Declares which CLI surface is requesting the default output mode. */
  enum OutputSurface {
    SELECTABLE(OutputMode.TEXT),
    DISCOVERY(OutputMode.TEXT);

    private final OutputMode builtInDefault;

    OutputSurface(OutputMode builtInDefault) {
      this.builtInDefault = builtInDefault;
    }

    private OutputMode builtInDefault() {
      return builtInDefault;
    }
  }

  record OutputDefault(OutputMode mode, @Nullable String source) {
    OutputDefault {
      if (mode == OutputMode.CSV) {
        throw new IllegalArgumentException("Configured default output mode must not be csv.");
      }
    }
  }
}
