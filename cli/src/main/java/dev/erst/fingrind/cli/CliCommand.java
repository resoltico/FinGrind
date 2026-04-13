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

  /** Explicitly initializes one book file with the canonical schema. */
  record OpenBook(Path bookFilePath) implements CliCommand {}

  /** Declares or reactivates one account in the selected book. */
  record DeclareAccount(Path bookFilePath, Path requestFile) implements CliCommand {}

  /** Lists the account registry for the selected book. */
  record ListAccounts(Path bookFilePath) implements CliCommand {}

  /** Requests preflight validation for one book-backed posting request. */
  record PreflightEntry(Path bookFilePath, Path requestFile) implements CliCommand {}

  /** Requests commit execution for one book-backed posting request. */
  record PostEntry(Path bookFilePath, Path requestFile) implements CliCommand {}
}
