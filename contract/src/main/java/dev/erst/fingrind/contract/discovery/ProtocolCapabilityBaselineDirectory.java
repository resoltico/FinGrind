package dev.erst.fingrind.contract.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Synchronizes rendered capability-baseline documents without granting paths outside their root.
 */
final class ProtocolCapabilityBaselineDirectory {
  private ProtocolCapabilityBaselineDirectory() {}

  /**
   * Synchronizes the complete generated directory and removes only stale JSON fragments within it.
   */
  static void sync(Path targetDirectory, Map<Path, String> documents) throws IOException {
    Path checkedDirectory = Objects.requireNonNull(targetDirectory, "targetDirectory");
    Map<Path, String> checkedDocuments = Map.copyOf(Objects.requireNonNull(documents, "documents"));
    Files.createDirectories(checkedDirectory);
    removeStaleFragments(checkedDirectory, checkedDocuments.keySet());
    for (Map.Entry<Path, String> document : checkedDocuments.entrySet()) {
      Path target = targetPath(checkedDirectory, document.getKey());
      Path parent = Objects.requireNonNull(target.getParent());
      Files.createDirectories(parent);
      if (!Files.isRegularFile(target) || !document.getValue().equals(Files.readString(target))) {
        Files.writeString(target, document.getValue());
      }
    }
  }

  /** Resolves one rendered document only when it remains contained by the generated directory. */
  static Path targetPath(Path targetDirectory, Path relativeDocumentPath) {
    Path checkedDirectory = Objects.requireNonNull(targetDirectory, "targetDirectory");
    Path target =
        checkedDirectory
            .resolve(Objects.requireNonNull(relativeDocumentPath, "relativeDocumentPath"))
            .normalize();
    if (!target.startsWith(checkedDirectory)) {
      throw new IllegalArgumentException(
          "Capability baseline document path escapes its directory.");
    }
    return target;
  }

  private static void removeStaleFragments(Path targetDirectory, Set<Path> expected)
      throws IOException {
    try (Stream<Path> paths = Files.walk(targetDirectory)) {
      List<Path> staleFragments =
          paths
              .filter(Files::isRegularFile)
              .map(targetDirectory::relativize)
              .filter(path -> path.toString().endsWith(".json"))
              .filter(path -> !expected.contains(path))
              .toList();
      for (Path staleFragment : staleFragments) {
        Files.delete(targetDirectory.resolve(staleFragment));
      }
    }
  }
}
