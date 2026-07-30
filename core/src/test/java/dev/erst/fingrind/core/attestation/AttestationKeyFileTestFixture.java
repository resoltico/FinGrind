package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Supplies a lexical-real, owner-only temporary root to tests that exercise key-file publication.
 */
class AttestationKeyFileTestFixture {
  @TempDir private Path junitTemporaryDirectory;

  Path temporaryDirectory;
  private @Nullable Path windowsPrivateFixtureRoot;

  @BeforeEach
  final void canonicalizeTemporaryDirectory() throws IOException {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
      Path canonicalUserHome = Path.of(System.getProperty("user.home")).toRealPath();
      windowsPrivateFixtureRoot =
          PrivateOutputDirectory.createNewOwnerOnlyChild(
              canonicalUserHome, ".fingrind-attestation-test-");
      temporaryDirectory = windowsPrivateFixtureRoot.toRealPath();
    } else {
      temporaryDirectory =
          AttestationKeyFileTestSupport.canonicalTemporaryDirectory(junitTemporaryDirectory);
    }
  }

  @AfterEach
  final void deleteWindowsPrivateFixtureRoot() throws IOException {
    if (windowsPrivateFixtureRoot == null) {
      return;
    }
    Files.walkFileTree(
        windowsPrivateFixtureRoot,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException failure)
              throws IOException {
            if (failure != null) {
              throw failure;
            }
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
    windowsPrivateFixtureRoot = null;
  }
}
