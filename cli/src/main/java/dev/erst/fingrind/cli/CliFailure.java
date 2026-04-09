package dev.erst.fingrind.cli;

import java.util.Objects;

/** Structured CLI failure payload used for deterministic error envelopes. */
record CliFailure(String code, String message, String hint, String argument) {
  CliFailure {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }
}
