package dev.erst.fingrind.cli;

import java.nio.file.Path;

/** Parsed CLI command model for one FinGrind process invocation. */
sealed interface CliCommand {
  /** Requests the FinGrind help payload. */
  record Help() implements CliCommand {}

  /** Requests the FinGrind version payload. */
  record Version() implements CliCommand {}

  /** Requests the current capability summary payload. */
  record Capabilities() implements CliCommand {}

  /** Requests a minimal valid posting request JSON document. */
  record PrintRequestTemplate() implements CliCommand {}

  /** Requests preflight validation for one book-backed posting request. */
  record PreflightEntry(Path bookFilePath, Path requestFile) implements CliCommand {}

  /** Requests commit execution for one book-backed posting request. */
  record PostEntry(Path bookFilePath, Path requestFile) implements CliCommand {}
}
