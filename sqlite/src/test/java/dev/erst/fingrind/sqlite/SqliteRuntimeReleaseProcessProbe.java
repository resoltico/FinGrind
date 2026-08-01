package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Child-process probe for the terminal lifecycle of the default SQLite runtime. */
public final class SqliteRuntimeReleaseProcessProbe {
  private SqliteRuntimeReleaseProcessProbe() {}

  public static void main(String[] arguments) throws IOException {
    Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
    Set<Path> snapshotsBefore = managedSnapshots(temporaryDirectory);
    if (arguments.length == 0) {
      SqliteRuntime.sqliteVersion();
    } else if (arguments.length == 1 && "probe".equals(arguments[0])) {
      SqliteRuntime.probe();
    } else if (arguments.length == 1 && "shutdown-hook".equals(arguments[0])) {
      SqliteRuntime.sqliteVersion();
      return;
    } else {
      throw new IllegalArgumentException("Expected no arguments, 'probe', or 'shutdown-hook'.");
    }
    SqliteRuntime.releaseProcessScopedRuntime();
    Set<Path> retainedSnapshots = managedSnapshots(temporaryDirectory);
    retainedSnapshots.removeAll(snapshotsBefore);
    if (!retainedSnapshots.isEmpty()) {
      throw new IllegalStateException(
          "The process retained managed SQLite snapshots after terminal cleanup: "
              + retainedSnapshots);
    }
  }

  private static Set<Path> managedSnapshots(Path temporaryDirectory) throws IOException {
    try (Stream<Path> paths = Files.list(temporaryDirectory)) {
      return paths
          .filter(path -> path.getFileName().toString().startsWith("fingrind-managed-sqlite-"))
          .collect(Collectors.toSet());
    }
  }
}
