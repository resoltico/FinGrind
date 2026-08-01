package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared hardened filesystem fixture and retained-evidence helpers for publication tests. */
class SqlitePublicationCapabilityWitnessTestFixture {
  @TempDir Path tempDirectory;

  @BeforeEach
  final void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  final Path publicationCapabilityState(String suffix) throws IOException {
    return publicationCapabilityState(tempDirectory, suffix);
  }

  static SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator guardedLinkCreator(
      SqlitePublicationCapabilityWitness.Set witnesses) {
    return (finalPath, stagedPath) -> {
      witnesses.requireCurrent(
          finalPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Files.createLink(finalPath, stagedPath);
    };
  }

  static void establishWitness(
      Path targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind)
      throws IOException {
    try (SqlitePublicationCapabilityWitness.Set ignored =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                new SqlitePublicationCapabilityWitness.Requirement(targetPath, primitiveKind)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      // The subsequent acquisition validates the retained immutable facts.
    }
  }

  static Path publicationCapabilityState(Path parent, String suffix) throws IOException {
    try (var entries = Files.list(parent)) {
      return entries
          .filter(
              candidate ->
                  candidate
                          .getFileName()
                          .toString()
                          .startsWith(".fingrind-publication-capability-v2-")
                      && candidate.getFileName().toString().endsWith(suffix))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Missing publication capability state " + suffix));
    }
  }
}
