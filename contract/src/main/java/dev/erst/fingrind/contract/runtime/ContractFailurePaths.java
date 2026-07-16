package dev.erst.fingrind.contract.runtime;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Typed filesystem locations carried by one deterministic contract failure. */
public record ContractFailurePaths(Path path, List<Path> relatedPaths) {
  /** Normalizes all machine locations before a transport boundary can project them. */
  public ContractFailurePaths {
    path = canonical(path);
    relatedPaths =
        List.copyOf(
            Objects.requireNonNull(relatedPaths, "relatedPaths").stream()
                .map(ContractFailurePaths::canonical)
                .toList());
    if (relatedPaths.contains(path)) {
      throw new IllegalArgumentException("relatedPaths must not repeat the primary path.");
    }
    if (new LinkedHashSet<>(relatedPaths).size() != relatedPaths.size()) {
      throw new IllegalArgumentException("relatedPaths must not contain duplicates.");
    }
  }

  /** Creates one failure location with no companion paths. */
  public static ContractFailurePaths primary(Path path) {
    return new ContractFailurePaths(path, List.of());
  }

  private static Path canonical(Path value) {
    return Objects.requireNonNull(value, "path").toAbsolutePath().normalize();
  }
}
