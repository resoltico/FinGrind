package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Supplies a lexical-real temporary root to tests that exercise key-file publication. */
class AttestationKeyFileTestFixture {
  @TempDir Path temporaryDirectory;

  @BeforeEach
  final void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory =
        AttestationKeyFileTestSupport.canonicalTemporaryDirectory(temporaryDirectory);
  }
}
