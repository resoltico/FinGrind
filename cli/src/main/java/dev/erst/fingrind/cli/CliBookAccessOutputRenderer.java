package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import java.nio.file.Path;
import java.util.List;

/** Renders operator-facing text for book access and key-management mutations. */
final class CliBookAccessOutputRenderer {
  private CliBookAccessOutputRenderer() {}

  static String renderGeneratedBookKeyFileText(GeneratedBookKeyFile generatedKeyFile) {
    return CliTextFormat.renderTitledBlock(
        "Book Key File Generated",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book key file", CliTextDisplay.path(generatedKeyFile.bookKeyFilePath())),
                List.of("Encoding", generatedKeyFile.encoding()),
                List.of("Entropy bits", Integer.toString(generatedKeyFile.entropyBits())),
                List.of("Permissions", generatedKeyFile.permissions()))));
  }

  static String renderOpenBookText(Path bookFilePath, OpenBookResult.Opened opened) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(bookFilePath)));
    rows.addAll(CliBookIdentityDisplay.rows(opened.bookIdentity()));
    rows.add(List.of("Initialized at", CliTextDisplay.instant(opened.initializedAt())));
    return CliTextFormat.renderTitledBlock(
        "Book Initialized", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRekeyBookText(
      RekeyBookResult.Rekeyed rekeyed, BookAccess.PassphraseSource replacementPassphraseSource) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(rekeyed.bookFilePath())));
    rows.add(
        List.of(
            "Replacement secret source", displayPassphraseSourceKind(replacementPassphraseSource)));
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      rows.add(List.of("Replacement key file", CliTextDisplay.path(keyFile.bookKeyFilePath())));
    }
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  private static String displayPassphraseSourceKind(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "Key file";
      case BookAccess.PassphraseSource.StandardInput _ -> "Standard input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "Interactive prompt";
    };
  }
}
