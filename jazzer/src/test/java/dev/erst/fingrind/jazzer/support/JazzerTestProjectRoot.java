package dev.erst.fingrind.jazzer.support;

import java.nio.file.Path;

/** Resolves the canonical nested Jazzer project directory for deterministic tests. */
public final class JazzerTestProjectRoot {
  private static final String PROJECT_ROOT_PROPERTY = "fingrind.jazzer.test-project-root";

  private JazzerTestProjectRoot() {}

  /** Returns the normalized Jazzer project directory injected by the nested Gradle test task. */
  public static Path projectDirectory() {
    String projectRoot = System.getProperty(PROJECT_ROOT_PROPERTY);
    if (projectRoot == null || projectRoot.isBlank()) {
      throw new IllegalStateException(
          "Missing required system property " + PROJECT_ROOT_PROPERTY + ".");
    }
    return Path.of(projectRoot).toAbsolutePath().normalize();
  }
}
