package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Keeps one thread's temporary native-access bridge from a journal-owned stage to its final target.
 *
 * <p>This is deliberately not durable recovery evidence and grants neither lookup nor deletion
 * authority. The authenticated publication journal remains the only durable stage owner. The bridge
 * exists solely while the workflow retains the exact final-target maintenance lease, so SQLite can
 * open a reserved stage without mistaking its own workflow lease for a competing operation.
 */
final class SqliteJournaledStageAccess {
  private static final ThreadLocal<Map<String, Path>> FINAL_TARGETS =
      ThreadLocal.withInitial(HashMap::new);

  private SqliteJournaledStageAccess() {}

  static void retain(Path stagePath, Path finalTargetPath) {
    Path stage = normalized(stagePath, "stagePath");
    Path target = normalized(finalTargetPath, "finalTargetPath");
    Map<String, Path> targets = FINAL_TARGETS.get();
    String key = key(stage);
    Path previous = targets.putIfAbsent(key, target);
    if (previous != null && !previous.equals(target)) {
      throw new IllegalStateException(
          "One journal-owned publication stage cannot authorize multiple final targets.");
    }
  }

  static void release(Path stagePath) {
    Map<String, Path> targets = FINAL_TARGETS.get();
    targets.remove(key(normalized(stagePath, "stagePath")));
    if (targets.isEmpty()) {
      FINAL_TARGETS.remove();
    }
  }

  static @Nullable Path finalTargetForCurrentThread(Path stagePath) {
    return FINAL_TARGETS.get().get(key(normalized(stagePath, "stagePath")));
  }

  private static String key(Path path) {
    return SqliteProtectedBookPathIdentity.normalizedSpelling(path);
  }

  private static Path normalized(Path path, String name) {
    Path normalized = Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    if (normalized.getFileName() == null) {
      throw new IllegalArgumentException(name + " must name a regular-file path.");
    }
    return normalized;
  }
}
