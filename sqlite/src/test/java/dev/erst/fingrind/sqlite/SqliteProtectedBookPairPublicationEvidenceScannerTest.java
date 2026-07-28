package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
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
}
