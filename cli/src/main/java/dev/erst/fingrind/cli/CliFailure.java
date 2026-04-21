package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** Structured CLI failure payload used for deterministic error envelopes. */
record CliFailure(String code, String message, @Nullable String hint, @Nullable String argument) {
  CliFailure {
    code = requireText(code, "code");
    message = requireText(message, "message");
    hint = requireOptionalText(hint, "hint");
    argument = requireOptionalText(argument, "argument");
  }
}
