package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists wrapper-observed Jazzer CLI exit status when the launcher requests it. */
final class JazzerCliWrapperExitStatus {
  private static final String WRAPPER_EXIT_STATUS_PROPERTY =
      "fingrind.jazzer.wrapper.exit-status-file";

  private JazzerCliWrapperExitStatus() {}

  static boolean managed() {
    String configuredPath = System.getProperty(WRAPPER_EXIT_STATUS_PROPERTY);
    return configuredPath != null && !configuredPath.isBlank();
  }

  static void write(int exitCode) throws IOException {
    if (!managed()) {
      return;
    }
    Files.writeString(
        Path.of(System.getProperty(WRAPPER_EXIT_STATUS_PROPERTY)), Integer.toString(exitCode));
  }
}
