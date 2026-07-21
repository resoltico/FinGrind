package dev.erst.fingrind.cli;

import java.util.Objects;

/** SQLite-backed composition root for independently owned book-read capabilities. */
final class SqliteCliReadWorkflow
    implements CliBookReadWorkflow,
        SqliteCliInspectionReadOperations,
        SqliteCliAttestationInspectionOperations,
        SqliteCliCatalogReadOperations,
        SqliteCliPostingReadOperations,
        SqliteCliReportReadOperations,
        SqliteCliTaxReadOperations {
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliReadWorkflow(CliBookPassphraseResolver passphraseResolver) {
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public CliBookPassphraseResolver passphraseResolver() {
    return passphraseResolver;
  }
}
