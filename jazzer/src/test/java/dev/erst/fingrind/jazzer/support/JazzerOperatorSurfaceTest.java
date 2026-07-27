package dev.erst.fingrind.jazzer.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies the documented local Jazzer wrapper surface exists in the checkout. */
class JazzerOperatorSurfaceTest {
  private static final Path PROJECT_DIRECTORY = JazzerTestProjectRoot.projectDirectory();
  private static final Path BIN_DIRECTORY = PROJECT_DIRECTORY.resolve("bin");
  private static final Path README_PATH = PROJECT_DIRECTORY.resolve("README.md");

  @Test
  void documentedWrapperSurface_existsAndIsExecutable() {
    assertTrue(
        Files.isDirectory(BIN_DIRECTORY), "Missing Jazzer wrapper directory: " + BIN_DIRECTORY);

    Set<String> expectedScripts = new LinkedHashSet<>();
    expectedScripts.add("common.sh");
    expectedScripts.add("test");
    expectedScripts.add("check");
    expectedScripts.add("regression");
    expectedScripts.add("fuzz-all");
    expectedScripts.add("replay");
    expectedScripts.add("list-findings");
    expectedScripts.add("promote-seed");
    expectedScripts.add("seed-audit");
    for (JazzerRunTarget target : JazzerRunTarget.values()) {
      if (target.activeFuzzing()) {
        expectedScripts.add("fuzz-" + target.key());
      }
    }

    for (String scriptName : expectedScripts) {
      Path scriptPath = BIN_DIRECTORY.resolve(scriptName);
      assertTrue(Files.isRegularFile(scriptPath), "Missing Jazzer wrapper script: " + scriptPath);
      assertTrue(
          Files.isExecutable(scriptPath), "Jazzer wrapper must be executable: " + scriptPath);
    }
  }

  @Test
  void readme_routesOperatorsToWrapperSurface() throws Exception {
    String readme = Files.readString(README_PATH, UTF_8);

    assertTrue(readme.contains("jazzer/bin/test"));
    assertTrue(readme.contains("jazzer/bin/regression"));
    assertTrue(readme.contains("jazzer/bin/check"));
    assertTrue(readme.contains("jazzer/bin/promote-seed"));
    assertTrue(readme.contains("jazzer/bin/seed-audit"));
    assertTrue(readme.contains("repo-wide verification lock"));
    assertFalse(readme.contains("./gradlew -p jazzer test"));
    assertFalse(readme.contains("./gradlew -p jazzer jazzerRegression"));
    assertFalse(readme.contains("./gradlew -p jazzer check"));
    assertFalse(readme.contains("jazzer/.local/run-lock"));
  }
}
