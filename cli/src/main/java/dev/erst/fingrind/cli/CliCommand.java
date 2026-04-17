package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.core.PostingId;
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

  /** Requests a minimal valid AI-agent ledger-plan JSON document. */
  record PrintPlanTemplate() implements CliCommand {}

  /** Creates one new owner-only generated book key file. */
  record GenerateBookKeyFile(Path bookKeyFilePath) implements CliCommand {}

  /** Explicitly initializes one book file with the canonical schema. */
  record OpenBook(BookAccess bookAccess) implements CliCommand {}

  /** Rotates the passphrase that protects one existing book file. */
  record RekeyBook(BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource)
      implements CliCommand {}

  /** Declares or reactivates one account in the selected book. */
  record DeclareAccount(BookAccess bookAccess, Path requestFile) implements CliCommand {}

  /** Inspects the selected book file for lifecycle and compatibility state. */
  record InspectBook(BookAccess bookAccess) implements CliCommand {}

  /** Lists the account registry for the selected book. */
  record ListAccounts(BookAccess bookAccess, ListAccountsQuery query) implements CliCommand {}

  /** Returns one committed posting by its durable identity. */
  record GetPosting(BookAccess bookAccess, PostingId postingId) implements CliCommand {}

  /** Lists one filtered page of committed postings. */
  record ListPostings(BookAccess bookAccess, ListPostingsQuery query) implements CliCommand {}

  /** Computes per-currency balances for one declared account. */
  record AccountBalance(BookAccess bookAccess, AccountBalanceQuery query) implements CliCommand {}

  /** Executes one ordered AI-agent ledger plan transaction. */
  record ExecutePlan(BookAccess bookAccess, Path requestFile) implements CliCommand {}

  /** Requests preflight validation for one book-backed posting request. */
  record PreflightEntry(BookAccess bookAccess, Path requestFile) implements CliCommand {}

  /** Requests commit execution for one book-backed posting request. */
  record PostEntry(BookAccess bookAccess, Path requestFile) implements CliCommand {}
}
