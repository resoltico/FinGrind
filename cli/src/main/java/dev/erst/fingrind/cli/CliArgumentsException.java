package dev.erst.fingrind.cli;

import java.util.Objects;

/** Signals one invalid or unsupported CLI argument combination. */
final class CliArgumentsException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final String code;
  private final String argument;
  private final String hint;

  CliArgumentsException(String code, String argument, String message, String hint) {
    super(message);
    this.code = code;
    this.argument = argument;
    this.hint = hint;
  }

  CliArgumentsException(
      String code, String argument, String message, String hint, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.argument = argument;
    this.hint = hint;
  }

  String code() {
    return code;
  }

  String argument() {
    return argument;
  }

  String hint() {
    return hint;
  }

  CliFailure failure() {
    return new CliFailure(
        code,
        Objects.requireNonNullElse(getMessage(), "CLI arguments are invalid."),
        hint,
        argument);
  }
}
