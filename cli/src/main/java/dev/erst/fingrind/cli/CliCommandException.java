package dev.erst.fingrind.cli;

/** Sealed CLI trust-boundary failures that deterministically map to one failure envelope. */
sealed interface CliCommandException permits CliArgumentsException, CliRequestException {
  /** Returns the rendered deterministic CLI failure payload. */
  CliFailure failure();
}
