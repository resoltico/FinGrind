package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for documentation indexes and routed reference symbols. */
class ProtocolDocumentationIndexLintTest extends ProtocolContractLintSupport {
  @Test
  void documentationIndexListsAllReferenceAtoms() throws IOException {
    Set<String> documentedReferenceFiles = referencedDocFiles(rootDocumentIndex());
    Set<String> actualReferenceFiles = actualReferenceFiles();
    assertTrue(
        documentedReferenceFiles.equals(actualReferenceFiles),
        () ->
            "docs/README.md reference atom drift:\nexpected "
                + sorted(actualReferenceFiles)
                + "\nactual "
                + sorted(documentedReferenceFiles));
  }

  @Test
  void apiIndexFileIndexListsAllReferenceAtoms() throws IOException {
    Set<String> indexedReferenceFiles = indexedDocFiles(apiIndexDocument());
    Set<String> actualReferenceFiles = referenceAtomsExcludingApiIndex();
    assertTrue(
        indexedReferenceFiles.equals(actualReferenceFiles),
        () ->
            "docs/DOC_00_Index.md file-index drift:\nexpected "
                + sorted(actualReferenceFiles)
                + "\nactual "
                + sorted(indexedReferenceFiles));
  }

  @Test
  void apiIndexSymbolRoutesPointToExistingFilesAndSections() throws IOException {
    Map<String, Set<String>> headingsByFile = headingsByReferenceFile();
    Set<String> violations = new LinkedHashSet<>();
    for (DocRoute route : symbolRoutes(apiIndexDocument())) {
      Set<String> headings = headingsByFile.get(route.fileName());
      if (headings == null) {
        violations.add(
            route.symbol() + " routes to undocumented reference file `" + route.fileName() + "`");
        continue;
      }
      if (!headings.contains(route.section())) {
        violations.add(
            route.symbol()
                + " routes to missing section `"
                + route.section()
                + "` in `"
                + route.fileName()
                + "`");
      }
    }

    assertTrue(
        violations.isEmpty(),
        () -> "docs/DOC_00_Index.md symbol-route drift:\n" + sorted(violations));
  }

  @Test
  void apiIndexRoutesAllPublicReferenceSymbols() throws IOException {
    Set<String> documentedSymbols =
        symbolRoutes(apiIndexDocument()).stream()
            .map(DocRoute::symbol)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<String> expectedSymbols = new LinkedHashSet<>(exportedPublicReferenceSymbols());
    expectedSymbols.add("App");
    assertTrue(
        documentedSymbols.equals(expectedSymbols),
        () ->
            "docs/DOC_00_Index.md exported-symbol coverage drift:\nexpected "
                + sorted(expectedSymbols)
                + "\nactual "
                + sorted(documentedSymbols));
  }
}
