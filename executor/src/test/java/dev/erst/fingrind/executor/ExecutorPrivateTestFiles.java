package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/** Creates test-only secret files through the production owner-only file capability. */
public final class ExecutorPrivateTestFiles {
  private ExecutorPrivateTestFiles() {}

  /** Writes one new owner-only UTF-8 secret file. */
  public static void writeOwnerOnlyText(Path path, String text) throws IOException {
    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.createNew(
            Objects.requireNonNull(path, "path").toAbsolutePath().normalize())) {
      ByteBuffer pending =
          ByteBuffer.wrap(Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8));
      while (pending.hasRemaining()) {
        if (opened.write(pending) <= 0) {
          throw new IOException("The private test fixture could not write its complete content.");
        }
      }
      opened.force();
    }
  }
}
