package dev.erst.fingrind.cli;

/** Signals one invalid request document or request-shape failure. */
final class CliRequestException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final String code;
  private final String hint;

  CliRequestException(String code, String message, String hint, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.hint = hint;
  }

  CliFailure failure() {
    return new CliFailure(code, getMessage(), hint, null);
  }
}
