package dev.erst.fingrind.jazzer.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies the documented local Jazzer wrapper surface exists in the checkout. */
class JazzerOperatorSurfaceTest {
  private static final Path PROJECT_DIRECTORY = Path.of("").toAbsolutePath().normalize();
  private static final Path BIN_DIRECTORY = PROJECT_DIRECTORY.resolve("bin");

  @Test
  void documentedWrapperSurface_existsAndIsExecutable() {
    assertTrue(Files.isDirectory(BIN_DIRECTORY), "Missing Jazzer wrapper directory: " + BIN_DIRECTORY);

    Set<String> expectedScripts = new LinkedHashSet<>();
    expectedScripts.add("common.sh");
    expectedScripts.add("regression");
    expectedScripts.add("fuzz-all");
    expectedScripts.add("clean-local-findings");
    expectedScripts.add("clean-local-corpus");
    for (JazzerRunTarget target : JazzerRunTarget.values()) {
      if (target.activeFuzzing()) {
        expectedScripts.add("fuzz-" + target.key());
      }
    }

    for (String scriptName : expectedScripts) {
      Path scriptPath = BIN_DIRECTORY.resolve(scriptName);
      assertTrue(Files.isRegularFile(scriptPath), "Missing Jazzer wrapper script: " + scriptPath);
      assertTrue(Files.isExecutable(scriptPath), "Jazzer wrapper must be executable: " + scriptPath);
    }
  }
}
