package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Signals one invalid request document or request-shape failure. */
final class CliRequestException extends IllegalArgumentException implements CliCommandException {
  private static final long serialVersionUID = 1L;

  private final String code;
  private final String hint;
  private final @Nullable String argument;
  private final CliErrorJsonModels.@Nullable ErrorDetails details;

  CliRequestException(String code, String message, String hint, @Nullable Throwable cause) {
    this(code, message, hint, cause, null, null);
  }

  CliRequestException(
      String code,
      String message,
      String hint,
      @Nullable Throwable cause,
      @Nullable String argument) {
    this(code, message, hint, cause, argument, null);
  }

  CliRequestException(
      String code,
      String message,
      String hint,
      @Nullable Throwable cause,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    super(message, cause);
    this.code = code;
    this.hint = hint;
    this.argument = argument;
    this.details = details;
  }

  @Override
  public CliFailure failure() {
    return new CliFailure(
        code,
        Objects.requireNonNullElse(getMessage(), "Request is invalid."),
        hint,
        argument,
        details);
  }
}
