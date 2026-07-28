package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavioral coverage for fail-closed discovery of protected-book pair evidence. */
class SqliteProtectedBookPairPublicationEvidenceScannerTest {
  @TempDir Path tempDirectory;

  @Test
  void absentSharedParentContributesNoEvidenceInsteadOfCreatingOrInspectingIt() {
    Path absentParent = tempDirectory.resolve("absent-evidence-parent");

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(
            absentParent.resolve("book.sqlite"), absentParent.resolve("book.key")));
  }

  @Test
  void scannerReportsCandidateEnumerationFailureAfterCheckingForUnsafeOwnerResidue() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\evidence");
      parent.exists = true;
      parent.regularFile = false;
      IOException injected = new IOException("candidate evidence enumeration failure");
      parent.failNewDirectoryStreamAfterSuccessfulCallsWith(1, injected);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookPairPublicationEvidenceScanner.scan(
                      fileSystem.path("\\evidence\\book.sqlite"),
                      fileSystem.path("\\evidence\\book.key")));

      assertEquals(
          "Failed to inspect protected-book pair recovery evidence beside \\evidence.",
          failure.getMessage());
      assertSame(injected, failure.getCause());
    }
  }
}
