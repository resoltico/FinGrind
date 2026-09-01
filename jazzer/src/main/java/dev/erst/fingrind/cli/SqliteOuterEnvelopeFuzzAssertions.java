package dev.erst.fingrind.cli;

import dev.erst.fingrind.sqlite.SqliteFuzzArtifactFixtures;
import dev.erst.fingrind.sqlite.SqliteFuzzBookAssertions;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import dev.erst.fingrind.sqlite.SqliteProtectedBookVerificationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Exercises fail-closed protected-book outer-envelope admission in every SQLite Jazzer replay. */
final class SqliteOuterEnvelopeFuzzAssertions {
  private SqliteOuterEnvelopeFuzzAssertions() {}

  static void exercise(Path validBookPath, Path scratchRoot) throws IOException {
    SqliteFuzzArtifactFixtures.createOwnerOnlyArtifactDirectory(scratchRoot);
    Path tailBook = scratchRoot.resolve("tail.sqlite");
    Files.copy(validBookPath, tailBook, StandardCopyOption.COPY_ATTRIBUTES);
    Files.write(tailBook, new byte[] {1, 2, 3, 4}, StandardOpenOption.APPEND);
    requireVerificationRefusal(tailBook);

    Path walBook = scratchRoot.resolve("wal.sqlite");
    Files.copy(validBookPath, walBook, StandardCopyOption.COPY_ATTRIBUTES);
    Files.write(
        walBook.resolveSibling(walBook.getFileName() + "-wal"),
        new byte[4096],
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    requireVerificationRefusal(walBook);
  }

  private static void requireVerificationRefusal(Path bookPath) {
    requireVerificationRefusal(bookPath, SqliteFuzzBookAssertions::openStore);
  }

  static void requireVerificationRefusal(Path bookPath, StoreOpener storeOpener) {
    try {
      Objects.requireNonNull(storeOpener, "storeOpener").open(bookPath).close();
    } catch (SqliteProtectedBookVerificationException expected) {
      return;
    } catch (Exception unexpected) {
      throw new IllegalStateException(
          "Tampered protected-book envelope did not return the typed verification refusal.",
          unexpected);
    }
    throw new IllegalStateException(
        "Tampered protected-book envelope unexpectedly opened: " + bookPath);
  }

  /** Opens one protected-book store through the specific boundary being asserted. */
  @FunctionalInterface
  interface StoreOpener {
    /** Opens one store for the supplied protected-book path. */
    SqlitePostingSession open(Path bookPath);
  }
}
