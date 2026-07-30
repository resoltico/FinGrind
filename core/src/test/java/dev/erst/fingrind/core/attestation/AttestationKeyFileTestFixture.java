package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Supplies a lexical-real, owner-only temporary root to tests that exercise key-file publication.
 */
class AttestationKeyFileTestFixture {
  @TempDir Path temporaryDirectory;

  @BeforeEach
  final void canonicalizeTemporaryDirectory() throws IOException {
    Path canonicalTemporaryDirectory =
        AttestationKeyFileTestSupport.canonicalTemporaryDirectory(temporaryDirectory);
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
      Path privateFixtureRoot = canonicalTemporaryDirectory.resolve("attestation-private-root");
      PrivateOutputDirectory.createNewOwnerOnlyDirectories(privateFixtureRoot);
      temporaryDirectory = privateFixtureRoot.toRealPath();
    } else {
      temporaryDirectory = canonicalTemporaryDirectory;
    }
  }
}
