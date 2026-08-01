package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Filesystem-level coverage for descriptor-bound nofollow external-input reads. */
class CliNofollowFileInputTest {
  @TempDir Path tempDirectory;

  @Test
  void open_readsTheSelectedRegularFileThroughTheBoundDescriptor() throws Exception {
    Path input = tempDirectory.resolve("input.json");
    Files.writeString(input, "payload", StandardCharsets.UTF_8);

    try (var opened = CliNofollowFileInput.open(input)) {
      assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), opened.readAllBytes());
    }
  }

  @Test
  void open_refusesAFinalSymbolicLink() throws Exception {
    Path target = tempDirectory.resolve("target.json");
    Files.writeString(target, "payload", StandardCharsets.UTF_8);
    Path alias = tempDirectory.resolve("alias.json");
    createSymbolicLinkOrSkip(alias, target.getFileName());

    assertThrows(IOException.class, () -> CliNofollowFileInput.open(alias));
    assertThrows(IOException.class, () -> CliNofollowFileInput.readBounded(alias, 1_024));
  }

  @Test
  void readBounded_rejectsOversizeAndInvalidBounds() throws Exception {
    Path input = tempDirectory.resolve("bounded.json");
    Files.writeString(input, "abc", StandardCharsets.UTF_8);

    assertArrayEquals(
        "abc".getBytes(StandardCharsets.UTF_8), CliNofollowFileInput.readBounded(input, 3));
    assertThrows(
        CliNofollowFileInput.FileTooLargeException.class,
        () -> CliNofollowFileInput.readBounded(input, 2));
    assertThrows(IllegalArgumentException.class, () -> CliNofollowFileInput.readBounded(input, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliNofollowFileInput.readBounded(input, Integer.MAX_VALUE));
  }

  @Test
  void readBounded_refusesNonRegularInputs() {
    assertThrows(IOException.class, () -> CliNofollowFileInput.readBounded(tempDirectory, 1_024));
  }

  @Test
  void readBounded_preservesMissingFileClassification() {
    assertThrows(
        NoSuchFileException.class,
        () -> CliNofollowFileInput.readBounded(tempDirectory.resolve("missing.json"), 1_024));
  }

  @Test
  void open_translatesUnavailableNofollowProviderBehavior() {
    assertNofollowProviderFailure(new UnsupportedOperationException("nofollow unavailable"));
    assertNofollowProviderFailure(new IllegalArgumentException("nofollow unavailable"));
  }

  private static void assertNofollowProviderFailure(RuntimeException expectedCause) {
    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                CliNofollowFileInput.open(
                    Path.of("unsupported.json"),
                    ignored -> {
                      throw expectedCause;
                    }));

    assertSame(expectedCause, exception.getCause());
  }

  private static void createSymbolicLinkOrSkip(Path alias, Path target) throws IOException {
    try {
      Files.createSymbolicLink(alias, target);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException unavailable) {
      assumeTrue(
          false, "The filesystem does not permit symbolic-link test fixtures: " + unavailable);
    }
  }
}
