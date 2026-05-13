package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Shared environment and cleanup seams for SQLite round-trip workflow coverage. */
final class SqliteRoundTripWorkflowResources {
  private SqliteRoundTripWorkflowResources() {}

  static SqliteCliBookWorkflow sqliteWorkflow() {
    return new SqliteCliBookWorkflow(CliFuzzFixtures.fixedClock(), passphraseResolver());
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
    return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
  }

  static void deleteRecursively(Path root) throws IOException {
    if (Files.notExists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
