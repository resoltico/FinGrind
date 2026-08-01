package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Shared repository and path helpers for split contract-lint tests. */
class ProtocolContractRepositorySupport {
  private static final String REPOSITORY_ROOT_PROPERTY = "fingrind.repository.root";

  protected final List<Path> productionJavaFiles() throws IOException {
    Path root = repositoryRoot();
    List<Path> files = new ArrayList<>();
    for (String sourceDirectory :
        List.of(
            "core/src/main/java",
            "contract/src/main/java",
            "executor/src/main/java",
            "cli/src/main/java",
            "report-pdf/src/main/java",
            "sqlite/src/main/java",
            "jazzer/src/main/java")) {
      try (Stream<Path> sources = Files.walk(root.resolve(sourceDirectory))) {
        sources
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !isOperationVocabularyOwner(root, path))
            .forEach(files::add);
      }
    }
    return files.stream().sorted(Comparator.naturalOrder()).toList();
  }

  /** Returns current user documentation whose operation references must match the live catalog. */
  protected final List<Path> currentDocumentationFiles() throws IOException {
    Path root = repositoryRoot();
    try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
      List<Path> userDocs =
          Stream.concat(Stream.of(root.resolve("README.md")), docs)
              .filter(path -> path.toString().endsWith(".md"))
              .toList();
      try (Stream<Path> bundleTemplates = Files.walk(root.resolve("cli/src/bundle/root"))) {
        return Stream.concat(
                userDocs.stream(),
                bundleTemplates.filter(
                    path -> path.toString().endsWith(".md") || path.toString().endsWith(".json")))
            .sorted(Comparator.naturalOrder())
            .toList();
      }
    }
  }

  protected final Path repositoryRoot() {
    String configuredRoot = System.getProperty(REPOSITORY_ROOT_PROPERTY);
    if (configuredRoot != null && !configuredRoot.isBlank()) {
      Path repositoryRoot = Path.of(configuredRoot).toAbsolutePath().normalize();
      if (!Files.isRegularFile(repositoryRoot.resolve("settings.gradle.kts"))) {
        throw new IllegalStateException(
            "Configured repository root does not contain settings.gradle.kts: " + repositoryRoot);
      }
      return repositoryRoot;
    }
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (directory != null && !Files.isRegularFile(directory.resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    if (directory == null) {
      throw new IllegalStateException("Unable to locate repository root from user.dir.");
    }
    return directory;
  }

  protected final boolean looksLikeCommandInvocation(String line) {
    String trimmed = line.stripLeading();
    return trimmed.startsWith("fingrind ")
        || trimmed.startsWith("./")
        || trimmed.startsWith("java ")
        || trimmed.startsWith("docker ")
        || trimmed.contains("| fingrind ");
  }

  protected final Path rootDocumentIndex() {
    return repositoryRoot().resolve("docs/README.md");
  }

  protected final Path apiIndexDocument() {
    return repositoryRoot().resolve("docs/DOC_00_Index.md");
  }

  protected final List<Path> apiIndexDocuments() throws IOException {
    try (Stream<Path> files = Files.list(repositoryRoot().resolve("docs"))) {
      return files
          .filter(path -> path.getFileName().toString().startsWith("DOC_00_"))
          .filter(path -> path.getFileName().toString().endsWith(".md"))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  protected final Set<String> actualReferenceFiles() throws IOException {
    try (Stream<Path> files = Files.list(repositoryRoot().resolve("docs"))) {
      return files
          .filter(path -> path.getFileName().toString().matches("DOC_\\d+_.+\\.md"))
          .map(path -> path.getFileName().toString())
          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
  }

  protected final Set<String> referenceAtomsExcludingApiIndex() throws IOException {
    Set<String> files = new LinkedHashSet<>(actualReferenceFiles());
    files.remove("DOC_00_Index.md");
    return Set.copyOf(files);
  }

  protected final String relative(Path path) {
    return repositoryRoot()
        .relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), "/");
  }

  protected final boolean isContractProtocolSource(Path root, Path path) {
    return root.relativize(path)
        .startsWith(
            Path.of(
                "contract",
                "src",
                "main",
                "java",
                "dev",
                "erst",
                "fingrind",
                "contract",
                "protocol"));
  }

  protected final boolean isOperationVocabularyOwner(Path root, Path path) {
    Path relativePath = root.relativize(path);
    return isContractProtocolSource(root, path)
        || relativePath.equals(
            Path.of(
                "contract",
                "src",
                "main",
                "java",
                "dev",
                "erst",
                "fingrind",
                "contract",
                "workflow",
                "LedgerJournalKind.java"));
  }

  protected final String sorted(Set<String> values) {
    return values.stream().sorted().collect(java.util.stream.Collectors.joining("\n"));
  }
}
