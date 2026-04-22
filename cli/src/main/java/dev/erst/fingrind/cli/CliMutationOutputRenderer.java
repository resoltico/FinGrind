package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.List;

/** Shared human-readable rendering for administrative and write command successes. */
final class CliMutationOutputRenderer {
  private CliMutationOutputRenderer() {}

  static String renderGeneratedBookKeyFileHuman(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    return CliTextFormat.renderTitledBlock(
        "Book Key File Generated",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book key file", absolutePath(generatedKeyFile.bookKeyFilePath())),
                List.of("Encoding", generatedKeyFile.encoding()),
                List.of("Entropy bits", Integer.toString(generatedKeyFile.entropyBits())),
                List.of("Permissions", generatedKeyFile.permissions()))));
  }

  static String renderOpenBookHuman(Path bookFilePath, OpenBookResult.Opened opened) {
    return CliTextFormat.renderTitledBlock(
        "Book Initialized",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", absolutePath(bookFilePath)),
                List.of("Initialized at", opened.initializedAt().toString()))));
  }

  static String renderRekeyBookHuman(RekeyBookResult.Rekeyed rekeyed) {
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed",
        CliTextFormat.renderKeyValueBlock(
            List.of(List.of("Book file", absolutePath(rekeyed.bookFilePath())))));
  }

  static String renderDeclaredAccountHuman(DeclaredAccount account) {
    return CliTextFormat.renderTitledBlock(
        "Account Declared",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Account code", account.accountCode().value()),
                List.of("Account name", account.accountName().value()),
                List.of("Normal balance", account.normalBalance().wireValue()),
                List.of("Active", Boolean.toString(account.active())),
                List.of("Declared at", account.declaredAt().toString()))));
  }

  static String renderPreflightAcceptedHuman(PostEntryResult.PreflightAccepted accepted) {
    return CliTextFormat.renderTitledBlock(
        "Entry Preflight Accepted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Idempotency key", accepted.idempotencyKey().value()),
                List.of("Effective date", accepted.effectiveDate().toString()))));
  }

  static String renderCommittedHuman(PostEntryResult.Committed committed) {
    return CliTextFormat.renderTitledBlock(
        "Entry Committed",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Posting id", committed.postingId().value()),
                List.of("Idempotency key", committed.idempotencyKey().value()),
                List.of("Effective date", committed.effectiveDate().toString()),
                List.of("Recorded at", committed.recordedAt().toString()))));
  }

  private static String absolutePath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }
}
