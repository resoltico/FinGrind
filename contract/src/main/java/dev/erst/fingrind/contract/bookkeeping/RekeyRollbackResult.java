package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result family for the split rekey-rollback maintenance commands. */
public sealed interface RekeyRollbackResult
    permits RekeyRollbackResult.Inspected,
        RekeyRollbackResult.Restored,
        RekeyRollbackResult.Deleted,
        RekeyRollbackResult.Rejected {

  /** Successful inspection outcome listing every matching sibling rollback artifact. */
  record Inspected(Path bookFilePath, List<Path> rollbackArtifactPaths)
      implements RekeyRollbackResult {
    public Inspected {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      rollbackArtifactPaths = normalizedPaths(rollbackArtifactPaths, "rollbackArtifactPaths");
    }
  }

  /** Successful rollback-copy restore outcome. */
  record Restored(Path bookFilePath, Path rollbackArtifactPath) implements RekeyRollbackResult {
    public Restored {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      rollbackArtifactPath = normalizedPath(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Successful rollback-copy deletion outcome. */
  record Deleted(Path bookFilePath, Path rollbackArtifactPath) implements RekeyRollbackResult {
    public Deleted {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      rollbackArtifactPath = normalizedPath(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Deterministic refusal for one rekey-rollback maintenance command. */
  record Rejected(BookMaintenanceRejection rejection) implements RekeyRollbackResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  private static Path normalizedPath(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }

  private static List<Path> normalizedPaths(List<Path> paths, String name) {
    return List.copyOf(Objects.requireNonNull(paths, name)).stream()
        .map(path -> normalizedPath(path, name + " entry"))
        .toList();
  }
}
