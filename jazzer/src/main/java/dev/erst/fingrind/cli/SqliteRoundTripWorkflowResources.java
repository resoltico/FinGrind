package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

/** Shared environment seams for SQLite round-trip workflow coverage. */
final class SqliteRoundTripWorkflowResources {
  private SqliteRoundTripWorkflowResources() {}

  static CliBookLifecycleWorkflow sqliteLifecycleWorkflow() {
    return new SqliteCliLifecycleWorkflow(CliFuzzFixtures.fixedClock(), passphraseResolver());
  }

  static CliBookMutationWorkflow sqliteMutationWorkflow() {
    return new SqliteCliMutationWorkflow(CliFuzzFixtures.fixedClock(), passphraseResolver());
  }

  static CliBookReadWorkflow sqliteReadWorkflow() {
    return new SqliteCliReadWorkflow(passphraseResolver());
  }

  static CliBookPassphraseResolver passphraseResolver() {
    return new CliBookPassphraseResolver(
        new ByteArrayInputStream(new byte[0]),
        prompt -> {
          throw new IllegalStateException(
              "Interactive passphrase prompting must not occur during Jazzer workflow coverage: "
                  + prompt);
        });
  }

  static BookAccess keyFileBookAccess(Path bookPath, Path keyPath) {
    return new BookAccess(
        bookPath,
        new BookAccess.PassphraseSource.KeyFile(keyPath),
        CliFuzzWorkflowFixtures.attestationCredentialSources());
  }
}
