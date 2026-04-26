package dev.erst.fingrind.cli;

/** Signals that bundle-internal staged launcher arguments could not be resolved safely. */
final class LauncherInvocationArgumentsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  LauncherInvocationArgumentsException(String message) {
    super(message);
  }

  LauncherInvocationArgumentsException(String message, Throwable cause) {
    super(message, cause);
  }
}
