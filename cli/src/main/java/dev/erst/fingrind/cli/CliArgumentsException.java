package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Signals one invalid or unsupported CLI argument combination. */
final class CliArgumentsException extends IllegalArgumentException implements CliCommandException {
  private static final long serialVersionUID = 1L;

  private final String code;
  private final String argument;
  private final String hint;
  private final @Nullable Path path;

  CliArgumentsException(String code, String argument, String message, String hint) {
    this(code, argument, message, hint, null, null);
  }

  CliArgumentsException(
      String code, String argument, String message, String hint, Throwable cause) {
    this(code, argument, message, hint, null, cause);
  }

  CliArgumentsException(
      String code, String argument, String message, String hint, @Nullable Path path) {
    this(code, argument, message, hint, path, null);
  }

  private CliArgumentsException(
      String code,
      String argument,
      String message,
      String hint,
      @Nullable Path path,
      @Nullable Throwable cause) {
    super(message, cause);
    this.code = code;
    this.argument = argument;
    this.hint = hint;
    this.path = path;
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

  CliArgumentsException withHint(String replacementHint) {
    String message = Objects.requireNonNullElse(getMessage(), "CLI arguments are invalid.");
    return getCause() == null
        ? new CliArgumentsException(code, argument, message, replacementHint, path)
        : new CliArgumentsException(code, argument, message, replacementHint, path, getCause());
  }

  @Override
  public CliFailure failure() {
    return new CliFailure(
        code,
        Objects.requireNonNullElse(getMessage(), "CLI arguments are invalid."),
        hint,
        argument,
        null,
        path,
        java.util.List.<Path>of(),
        null);
  }
}
