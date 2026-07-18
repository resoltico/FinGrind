package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for documentation indexes and routed reference symbols. */
class ProtocolDocumentationIndexLintTest extends ProtocolContractDocumentationSupport {
  private static final Pattern RELATIVE_DOC_LINK_PATTERN =
      Pattern.compile("\\]\\(\\./([^)]+\\.md)\\)");
  private static final Pattern VERSION_FRONTMATTER_PATTERN =
      Pattern.compile("^---\\Rafad: \"5\\.0\\.1\"\\Rversion: \"([^\"]+)\"", Pattern.MULTILINE);

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
    for (DocRoute route : apiSymbolRoutes()) {
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
        apiSymbolRoutes().stream()
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

  private List<DocRoute> apiSymbolRoutes() throws IOException {
    List<DocRoute> routes = new ArrayList<>();
    for (Path document : apiIndexDocuments()) {
      routes.addAll(symbolRoutes(document));
    }
    return List.copyOf(routes);
  }

  @Test
  void documentationIndexRoutesEveryCheckedInGuideAndSchemaPage() throws IOException {
    Set<String> indexedDocuments = linkedDocumentationFiles(rootDocumentIndex());
    Set<String> actualDocuments = actualDocumentationFiles();
    assertTrue(
        indexedDocuments.equals(actualDocuments),
        () ->
            "docs/README.md guide-index drift:\nexpected "
                + sorted(actualDocuments)
                + "\nactual "
                + sorted(indexedDocuments));
  }

  @Test
  void afadManagedDocumentsDeclareCurrentProtocolVersion() throws IOException {
    Set<String> violations = new LinkedHashSet<>();
    for (Path document : actualDocumentationPaths()) {
      String text = Files.readString(document);
      if (!text.startsWith("---\nafad: \"5.0.1\"")) {
        violations.add(relativeDocumentationPath(document) + " must declare afad: \"5.0.1\".");
      }
    }

    assertTrue(violations.isEmpty(), () -> "AFAD protocol metadata drift:\n" + sorted(violations));
  }

  @Test
  void afadManagedDocumentsDeclareCurrentProjectVersion() throws IOException {
    String expectedVersion = projectVersion();
    Set<String> violations = new LinkedHashSet<>();
    for (Path document : actualDocumentationPaths()) {
      String text = Files.readString(document);
      Matcher matcher = VERSION_FRONTMATTER_PATTERN.matcher(text);
      if (!matcher.find()) {
        violations.add(
            relativeDocumentationPath(document)
                + " must declare one AFAD frontmatter version matching gradle.properties.");
        continue;
      }
      String actualVersion = matcher.group(1);
      if (!expectedVersion.equals(actualVersion)) {
        violations.add(
            relativeDocumentationPath(document)
                + " declares version "
                + actualVersion
                + " but gradle.properties declares "
                + expectedVersion
                + ".");
      }
    }

    assertTrue(violations.isEmpty(), () -> "AFAD document version drift:\n" + sorted(violations));
  }

  private String projectVersion() throws IOException {
    return Files.readAllLines(repositoryRoot().resolve("gradle.properties")).stream()
        .filter(line -> line.startsWith("version="))
        .map(line -> line.substring("version=".length()).trim())
        .filter(version -> !version.isEmpty())
        .findFirst()
        .orElseThrow(() -> new IOException("Missing version in gradle.properties."));
  }

  private Set<String> linkedDocumentationFiles(Path document) throws IOException {
    String text = Files.readString(document);
    Set<String> linked = new LinkedHashSet<>();
    for (java.util.regex.MatchResult match :
        RELATIVE_DOC_LINK_PATTERN.matcher(text).results().toList()) {
      linked.add(match.group(1));
    }
    linked.remove("README.md");
    return Set.copyOf(linked);
  }

  private Set<String> actualDocumentationFiles() throws IOException {
    Set<String> paths = new LinkedHashSet<>();
    for (Path document : actualDocumentationPaths()) {
      paths.add(relativeDocumentationPath(document));
    }
    return Set.copyOf(paths);
  }

  private Set<Path> actualDocumentationPaths() throws IOException {
    Path docsRoot = repositoryRoot().resolve("docs");
    try (java.util.stream.Stream<Path> files = Files.walk(docsRoot)) {
      return files
          .filter(path -> path.toString().endsWith(".md"))
          .filter(path -> !path.equals(docsRoot.resolve("README.md")))
          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private String relativeDocumentationPath(Path document) {
    return repositoryRoot().resolve("docs").relativize(document).toString().replace('\\', '/');
  }
}
