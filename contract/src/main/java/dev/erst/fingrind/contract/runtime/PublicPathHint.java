package dev.erst.fingrind.contract.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Redacted public path hint that preserves only the trailing path context. */
public record PublicPathHint(String value) {
  private static final int DEFAULT_VISIBLE_SEGMENT_COUNT = 3;

  /** Validates one redacted public path hint. */
  public PublicPathHint {
    Objects.requireNonNull(value, "value");
    if (!"<redacted>".equals(value) && !value.startsWith("<redacted>/")) {
      throw new IllegalArgumentException(
          "Public path hints must equal <redacted> or start with <redacted>/.");
    }
  }

  /** Returns the canonical redacted hint for one filesystem path. */
  public static PublicPathHint fromPath(Path path) {
    Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    return fromNormalizedPath(normalizedPath, DEFAULT_VISIBLE_SEGMENT_COUNT);
  }

  /** Returns canonical redacted hints with enough trailing context to distinguish one group. */
  public static List<PublicPathHint> disambiguate(List<Path> paths) {
    List<Path> normalizedPaths =
        Objects.requireNonNull(paths, "paths").stream()
            .map(path -> Objects.requireNonNull(path, "paths must not contain null entries"))
            .map(path -> path.toAbsolutePath().normalize())
            .toList();
    if (normalizedPaths.isEmpty()) {
      return List.of();
    }
    int minimumVisibleSegments =
        normalizedPaths.stream().mapToInt(Path::getNameCount).min().orElse(0) <= 0
            ? 0
            : Math.min(
                DEFAULT_VISIBLE_SEGMENT_COUNT,
                normalizedPaths.stream().mapToInt(Path::getNameCount).min().orElse(0));
    int requiredVisibleSegments = minimumVisibleSegments;
    for (int left = 0; left < normalizedPaths.size(); left++) {
      for (int right = left + 1; right < normalizedPaths.size(); right++) {
        Path leftPath = normalizedPaths.get(left);
        Path rightPath = normalizedPaths.get(right);
        if (!leftPath.equals(rightPath)) {
          requiredVisibleSegments =
              Math.max(requiredVisibleSegments, commonSuffixSegments(leftPath, rightPath) + 1);
        }
      }
    }
    int candidateVisibleSegments = requiredVisibleSegments;
    return normalizedPaths.stream()
        .map(
            path ->
                fromNormalizedPath(path, Math.min(candidateVisibleSegments, path.getNameCount())))
        .toList();
  }

  private static PublicPathHint fromNormalizedPath(Path normalizedPath, int visibleSegments) {
    int nameCount = normalizedPath.getNameCount();
    if (nameCount <= 0) {
      return new PublicPathHint("<redacted>");
    }
    int firstPreservedIndex = Math.max(0, nameCount - Math.max(1, visibleSegments));
    Path preservedPath = normalizedPath.subpath(firstPreservedIndex, nameCount);
    return new PublicPathHint("<redacted>/" + preservedPath.toString().replace('\\', '/'));
  }

  private static int commonSuffixSegments(Path leftPath, Path rightPath) {
    int leftIndex = leftPath.getNameCount() - 1;
    int rightIndex = rightPath.getNameCount() - 1;
    int commonSegments = 0;
    while (leftIndex >= 0
        && rightIndex >= 0
        && leftPath.getName(leftIndex).equals(rightPath.getName(rightIndex))) {
      commonSegments++;
      leftIndex--;
      rightIndex--;
    }
    return commonSegments;
  }
}
