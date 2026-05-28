package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the production SQLite bridge seam against raw-handle sprawl. */
class SqliteNativeBridgeSeamContractTest {
  @Test
  void productionBridgeLimitsDirectDatabaseHandleAndApiAccessToNativeStatementOwner()
      throws IOException {
    Path sqliteMainSourceDirectory = sqliteMainSourceDirectory();
    List<String> directAccessOwners;
    try (var sourceFiles = Files.walk(sqliteMainSourceDirectory)) {
      directAccessOwners =
          sourceFiles
              .filter(path -> path.toString().endsWith(".java"))
              .filter(SqliteNativeBridgeSeamContractTest::containsDirectDatabaseHandleOrApiAccess)
              .map(path -> path.getFileName().toString())
              .sorted()
              .toList();
    }

    assertEquals(
        List.of(
            "SqliteNativeDatabaseConfiguration.java",
            "SqliteNativeDatabaseDiagnostics.java",
            "SqliteNativeProtectedBookRuntime.java",
            "SqliteNativeStatement.java"),
        directAccessOwners);
  }

  private static boolean containsDirectDatabaseHandleOrApiAccess(Path sourceFile) {
    try {
      String source = Files.readString(sourceFile);
      return source.contains(".handle()") || source.contains(".sqliteApi()");
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read SQLite bridge source contract file: " + sourceFile, exception);
    }
  }

  private static Path sqliteMainSourceDirectory() {
    Path moduleRelative = Path.of("src", "main", "java", "dev", "erst", "fingrind", "sqlite");
    if (Files.isDirectory(moduleRelative)) {
      return moduleRelative;
    }
    Path rootRelative =
        Path.of("sqlite", "src", "main", "java", "dev", "erst", "fingrind", "sqlite");
    if (Files.isDirectory(rootRelative)) {
      return rootRelative;
    }
    throw new IllegalStateException(
        "Could not locate the SQLite main-source directory from the active test working directory.");
  }
}
