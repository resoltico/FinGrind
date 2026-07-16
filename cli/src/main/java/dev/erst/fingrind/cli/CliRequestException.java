package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Signals one invalid request document or request-shape failure. */
final class CliRequestException extends IllegalArgumentException implements CliCommandException {
  private static final long serialVersionUID = 1L;

  private final String code;
  private final String hint;
  private final @Nullable String argument;
  private final CliErrorJsonModels.@Nullable ErrorDetails details;
  private final @Nullable Path path;

  CliRequestException(String code, String message, String hint, @Nullable Throwable cause) {
    this(code, message, hint, cause, null, null, null);
  }

  CliRequestException(
      String code,
      String message,
      String hint,
      @Nullable Throwable cause,
      @Nullable String argument) {
    this(code, message, hint, cause, argument, null, null);
  }

  CliRequestException(
      String code,
      String message,
      String hint,
      @Nullable Throwable cause,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    this(code, message, hint, cause, argument, details, null);
  }

  CliRequestException(
      String code,
      String message,
      String hint,
      @Nullable Throwable cause,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details,
      @Nullable Path path) {
    super(message, cause);
    this.code = code;
    this.hint = hint;
    this.argument = argument;
    this.details = details;
    this.path = path;
  }

  @Override
  public CliFailure failure() {
    return new CliFailure(
        code,
        Objects.requireNonNullElse(getMessage(), "Request is invalid."),
        hint,
        argument,
        details,
        path,
        List.of());
  }
}
