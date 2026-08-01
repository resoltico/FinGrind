package dev.erst.fingrind.jazzer.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves test-owned directories to their physical spelling before production path admission. */
public final class JazzerTestFixturePaths {
  private JazzerTestFixturePaths() {}

  /**
   * Resolves one pre-created Jazzer fixture directory without weakening caller-path alias refusal.
   *
   * <p>JUnit may allocate {@code @TempDir} through an operating-system alias such as macOS {@code
   * /var}. FinGrind deliberately rejects aliases in caller-selected protected-book paths, so
   * deterministic fixtures must supply their physical directory rather than reinterpret that
   * product contract.
   */
  public static Path canonicalExistingDirectory(Path directory) throws IOException {
    return Objects.requireNonNull(directory, "directory").toRealPath();
  }
}
