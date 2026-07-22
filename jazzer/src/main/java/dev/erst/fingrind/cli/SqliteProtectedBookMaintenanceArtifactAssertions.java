package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Verifies filesystem and readability invariants for protected-book maintenance fuzz scenarios. */
final class SqliteProtectedBookMaintenanceArtifactAssertions {
  private SqliteProtectedBookMaintenanceArtifactAssertions() {}

  static void requireUnchanged(Path path, byte[] expectedBytes, String message) throws IOException {
    if (!Arrays.equals(expectedBytes, Files.readAllBytes(path))) {
      throw new IllegalStateException(message);
    }
  }

  static void requireAbsent(Path path, String message) {
    if (Files.exists(path)) {
      throw new IllegalStateException(message);
    }
  }

  static void requireReadable(CliBookReadWorkflow readWorkflow, BookAccess access) {
    readWorkflow.inspectBook(access).requireAccepted();
  }

  static void requireUnreadable(CliBookReadWorkflow readWorkflow, BookAccess access) {
    readWorkflow.inspectBook(access).requireRejected();
  }
}
