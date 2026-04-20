package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ContractErrors;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract-lint tests that keep user-facing operation references registered in contract. */
class ProtocolContractLintTest {
  private static final Pattern FINGRIND_COMMAND_PATTERN =
      Pattern.compile("\\bfingrind\\s+([a-z][a-z0-9-]*)");
  private static final Pattern STRING_LITERAL_PATTERN =
      Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"", Pattern.DOTALL);
  private static final Pattern BACKTICKED_HYPHEN_ID_PATTERN =
      Pattern.compile("`([a-z][a-z0-9]*(?:-[a-z0-9]+)+)`");
  private static final Pattern DOC_REFERENCE_LINK_PATTERN =
      Pattern.compile("\\[(DOC_\\d+_.+\\.md)]\\(\\./\\1\\)");
  private static final Pattern MODULE_EXPORT_PATTERN =
      Pattern.compile("^\\s*exports\\s+([\\w\\.]+);", Pattern.MULTILINE);
  private static final Pattern TOP_LEVEL_PUBLIC_TYPE_PATTERN =
      Pattern.compile(
          "^public\\s+(?:sealed\\s+|non-sealed\\s+|final\\s+|abstract\\s+)?(?:record|class|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)",
          Pattern.MULTILINE);
  private static final Pattern NESTED_PUBLIC_TYPE_PATTERN =
      Pattern.compile(
          "^\\s{2,}public\\s+(?:static\\s+)?(?:sealed\\s+|non-sealed\\s+|final\\s+|abstract\\s+)?(?:record|class|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)",
          Pattern.MULTILINE);
  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("^package\\s+([\\w\\.]+);", Pattern.MULTILINE);
  private static final Set<String> NON_OPERATION_BACKTICK_IDS = nonOperationBacktickIds();

  @Test
  void productionJavaDoesNotEmbedHyphenatedOperationIdsInStringLiteralsOutsideContractProtocol()
      throws IOException {
    Set<String> registeredHyphenatedIds = registeredHyphenatedOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path sourceFile : productionJavaFiles()) {
      String source = Files.readString(sourceFile);
      STRING_LITERAL_PATTERN
          .matcher(source)
          .results()
          .map(match -> match.group().substring(1, match.group().length() - 1))
          .forEach(
              literal ->
                  registeredHyphenatedIds.stream()
                      .filter(operationId -> containsToken(literal, operationId))
                      .map(
                          operationId ->
                              relative(sourceFile)
                                  + " embeds `"
                                  + operationId
                                  + "` inside a string literal")
                      .forEach(violations::add));
    }

    assertTrue(
        violations.isEmpty(),
        () -> "Operation id string-literal authorship drift:\n" + sorted(violations));
  }

  @Test
  void documentationFingrindInvocationsReferenceRegisteredOperations() throws IOException {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path document : documentationFiles()) {
      Files.readAllLines(document).stream()
          .filter(ProtocolContractLintTest::looksLikeCommandInvocation)
          .forEach(
              line ->
                  FINGRIND_COMMAND_PATTERN
                      .matcher(line)
                      .results()
                      .map(match -> match.group(1))
                      .filter(command -> !registeredIds.contains(command))
                      .map(
                          command ->
                              relative(document)
                                  + " invokes unregistered operation `"
                                  + command
                                  + "`")
                      .forEach(violations::add));
    }

    assertTrue(
        violations.isEmpty(),
        () -> "Unregistered fingrind command references:\n" + sorted(violations));
  }

  @Test
  void documentationBacktickedHyphenIdsAreRegisteredOperationsOrKnownNonOperationIds()
      throws IOException {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path document : documentationFiles()) {
      String text = Files.readString(document);
      BACKTICKED_HYPHEN_ID_PATTERN
          .matcher(text)
          .results()
          .map(match -> match.group(1))
          .filter(token -> !registeredIds.contains(token))
          .filter(token -> !NON_OPERATION_BACKTICK_IDS.contains(token))
          .map(token -> relative(document) + " mentions unregistered hyphen id `" + token + "`")
          .forEach(violations::add);
    }

    assertTrue(
        violations.isEmpty(), () -> "Unregistered user-facing hyphen ids:\n" + sorted(violations));
  }

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

  @Test
  void catalogUsageAndExamplesReferenceOnlyRegisteredOperations() {
    Set<String> registeredIds = registeredOperationIds();
    Set<String> violations = new HashSet<>();
    ProtocolCatalog.operations()
        .forEach(
            operation ->
                Stream.concat(Stream.of(operation.usage()), operation.examples().stream())
                    .forEach(
                        text ->
                            FINGRIND_COMMAND_PATTERN
                                .matcher(text)
                                .results()
                                .map(match -> match.group(1))
                                .filter(command -> !registeredIds.contains(command))
                                .map(
                                    command ->
                                        operation.id().wireName()
                                            + " references unregistered operation `"
                                            + command
                                            + "`")
                                .forEach(violations::add)));

    assertTrue(
        violations.isEmpty(), () -> "Catalog operation reference drift:\n" + sorted(violations));
  }

  @Test
  void catalogOperationIdsAndTokensAreUnique() {
    Set<OperationId> ids = EnumSet.noneOf(OperationId.class);
    Set<String> duplicateIds = new HashSet<>();
    Set<String> tokens = new HashSet<>();
    Set<String> duplicateTokens = new HashSet<>();

    ProtocolCatalog.operations()
        .forEach(
            operation -> {
              if (!ids.add(operation.id())) {
                duplicateIds.add(operation.id().wireName());
              }
              if (!tokens.add(operation.id().wireName())) {
                duplicateTokens.add(operation.id().wireName());
              }
              operation.aliases().stream()
                  .filter(alias -> !tokens.add(alias))
                  .forEach(duplicateTokens::add);
            });

    assertTrue(
        duplicateIds.isEmpty(), () -> "Duplicate catalog operation ids:\n" + sorted(duplicateIds));
    assertTrue(
        duplicateTokens.isEmpty(),
        () -> "Duplicate catalog operation tokens:\n" + sorted(duplicateTokens));
  }

  private static Set<String> registeredOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<String> registeredHyphenatedOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .filter(operationId -> operationId.contains("-"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static boolean containsToken(String text, String token) {
    int searchFrom = 0;
    while (searchFrom <= text.length() - token.length()) {
      int index = text.indexOf(token, searchFrom);
      if (index < 0) {
        return false;
      }
      boolean hasLeadingTokenChar = index > 0 && isOperationTokenChar(text.charAt(index - 1));
      int trailingIndex = index + token.length();
      boolean hasTrailingTokenChar =
          trailingIndex < text.length() && isOperationTokenChar(text.charAt(trailingIndex));
      if (!hasLeadingTokenChar && !hasTrailingTokenChar) {
        return true;
      }
      searchFrom = index + 1;
    }
    return false;
  }

  private static boolean isOperationTokenChar(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9')
        || character == '-'
        || character == '_';
  }

  private static Set<String> nonOperationBacktickIds() {
    Set<String> ids =
        new HashSet<>(
            Set.of(
                "account-normal-balance-conflict",
                "account-state-violations",
                "administration-book-not-initialized",
                "assertion-failed",
                "book-already-initialized",
                "book-contains-schema",
                "build-logic",
                "class-complete",
                "class-start",
                "cli-request",
                "desktop-linux",
                "docker-buildx",
                "duplicate-idempotency-key",
                "inactive-account",
                "invalid-request",
                "json-envelope",
                "ledger-plan-request",
                "owner-only-acl",
                "posting-not-found",
                "posting-book-not-initialized",
                "query-book-not-initialized",
                "posting-workflow",
                "regression-input",
                "raw-json",
                "reversal-already-exists",
                "reversal-does-not-negate-target",
                "reversal-target-not-found",
                "runtime-failure",
                "sqlite-book-roundtrip",
                "sqlite-jdbc",
                "sequential-in-place",
                "test-complete",
                "test-progress",
                "unknown-account",
                "unknown-command",
                "report-pdf"));
    ids.addAll(BookInspection.Status.wireValues());
    ids.addAll(
        ContractErrors.descriptors().stream()
            .map(dev.erst.fingrind.contract.ContractResponse.ErrorDescriptor::code)
            .toList());
    ids.addAll(LedgerAssertionKind.wireValues());
    ids.addAll(ProtocolCatalog.successStatuses());
    ids.addAll(ProtocolCatalog.rejectionStatuses());
    ids.addAll(ProtocolCatalog.supportedPublicCliBundleTargets());
    ids.addAll(ProtocolCatalog.unsupportedPublicCliOperatingSystems());
    return Set.copyOf(ids);
  }

  private static List<Path> productionJavaFiles() throws IOException {
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
            .filter(path -> !isContractProtocolSource(root, path))
            .forEach(files::add);
      }
    }
    return files.stream().sorted(Comparator.naturalOrder()).toList();
  }

  private static List<Path> documentationFiles() throws IOException {
    Path root = repositoryRoot();
    try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
      List<Path> userDocs =
          Stream.concat(Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")), docs)
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

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(directory.resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    return directory;
  }

  private static boolean looksLikeCommandInvocation(String line) {
    String trimmed = line.stripLeading();
    return trimmed.startsWith("fingrind ")
        || trimmed.startsWith("./")
        || trimmed.startsWith("java ")
        || trimmed.startsWith("docker ")
        || trimmed.contains("| fingrind ");
  }

  private static Path rootDocumentIndex() {
    return repositoryRoot().resolve("docs/README.md");
  }

  private static Path apiIndexDocument() {
    return repositoryRoot().resolve("docs/DOC_00_Index.md");
  }

  private static Set<String> actualReferenceFiles() throws IOException {
    try (Stream<Path> files = Files.list(repositoryRoot().resolve("docs"))) {
      return files
          .filter(path -> path.getFileName().toString().matches("DOC_\\d+_.+\\.md"))
          .map(path -> path.getFileName().toString())
          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private static Set<String> exportedPublicReferenceSymbols() throws IOException {
    Path root = repositoryRoot();
    Set<String> exportedPackages = exportedPackages(root);
    try {
      return List.of("core", "contract", "executor", "sqlite", "report-pdf").stream()
          .flatMap(
              module ->
                  publicSymbols(root.resolve(module).resolve("src/main/java"), exportedPackages))
          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    } catch (UncheckedIOException exception) {
      throw new IOException("Failed to read exported public symbols.", exception);
    }
  }

  private static Set<String> exportedPackages(Path root) throws IOException {
    Set<String> packages = new LinkedHashSet<>();
    for (String module : List.of("core", "contract", "executor", "sqlite", "report-pdf")) {
      String moduleInfo =
          Files.readString(root.resolve(module).resolve("src/main/java/module-info.java"));
      for (java.util.regex.MatchResult match :
          MODULE_EXPORT_PATTERN.matcher(moduleInfo).results().toList()) {
        packages.add(match.group(1));
      }
    }
    return Set.copyOf(packages);
  }

  private static java.util.stream.Stream<String> publicSymbols(
      Path sourceRoot, Set<String> exportedPackages) {
    try (Stream<Path> sources = Files.walk(sourceRoot)) {
      return sources
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !"module-info.java".equals(path.getFileName().toString()))
          .flatMap(path -> publicSymbolsInFileUnchecked(path, exportedPackages))
          .toList()
          .stream();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static java.util.stream.Stream<String> publicSymbolsInFileUnchecked(
      Path sourceFile, Set<String> exportedPackages) {
    try {
      return publicSymbolsInFile(sourceFile, exportedPackages);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static java.util.stream.Stream<String> publicSymbolsInFile(
      Path sourceFile, Set<String> exportedPackages) throws IOException {
    String source = Files.readString(sourceFile);
    java.util.regex.MatchResult packageMatch =
        PACKAGE_PATTERN.matcher(source).results().findFirst().orElse(null);
    if (packageMatch == null || !exportedPackages.contains(packageMatch.group(1))) {
      return java.util.stream.Stream.empty();
    }
    java.util.regex.MatchResult topLevelMatch =
        TOP_LEVEL_PUBLIC_TYPE_PATTERN.matcher(source).results().findFirst().orElse(null);
    if (topLevelMatch == null) {
      return java.util.stream.Stream.empty();
    }
    String topLevelName = topLevelMatch.group(1);
    List<String> symbols = new ArrayList<>();
    symbols.add(topLevelName);
    NESTED_PUBLIC_TYPE_PATTERN
        .matcher(source)
        .results()
        .map(match -> topLevelName + "." + match.group(1))
        .forEach(symbols::add);
    return symbols.stream();
  }

  private static Set<String> referenceAtomsExcludingApiIndex() throws IOException {
    Set<String> files = new LinkedHashSet<>(actualReferenceFiles());
    files.remove("DOC_00_Index.md");
    return Set.copyOf(files);
  }

  private static Map<String, Set<String>> headingsByReferenceFile() throws IOException {
    Path root = repositoryRoot();
    try {
      return referenceAtomsExcludingApiIndex().stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  fileName -> fileName,
                  fileName -> headingsUnchecked(root.resolve("docs").resolve(fileName))));
    } catch (UncheckedIOException exception) {
      throw new IOException("Failed to read reference document headings.", exception);
    }
  }

  private static Set<String> headingsUnchecked(Path document) {
    try {
      return headings(document);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static Set<String> referencedDocFiles(Path document) throws IOException {
    Set<String> files = new LinkedHashSet<>();
    String text = Files.readString(document);
    for (java.util.regex.MatchResult match :
        DOC_REFERENCE_LINK_PATTERN.matcher(text).results().toList()) {
      files.add(match.group(1));
    }
    return files;
  }

  private static Set<String> indexedDocFiles(Path document) throws IOException {
    Set<String> files = new LinkedHashSet<>();
    for (String line : Files.readAllLines(document)) {
      List<String> cells = tableCells(line);
      if (cells.size() >= 2) {
        String candidate = stripBackticks(cells.get(0));
        if (candidate.matches("DOC_\\d+_.+\\.md")) {
          files.add(candidate);
        }
      }
    }
    return files;
  }

  private static List<DocRoute> symbolRoutes(Path document) throws IOException {
    List<DocRoute> routes = new ArrayList<>();
    for (String line : Files.readAllLines(document)) {
      List<String> cells = tableCells(line);
      if (cells.size() < 3) {
        continue;
      }
      String symbol = stripBackticks(cells.get(0));
      String fileName = stripBackticks(cells.get(1));
      String section = cells.get(2);
      if ("Symbol".equals(symbol)
          || !fileName.matches("DOC_\\d+_.+\\.md")
          || "Section".equals(section)) {
        continue;
      }
      routes.add(new DocRoute(symbol, fileName, section));
    }
    return routes;
  }

  private static Set<String> headings(Path document) throws IOException {
    return Files.readAllLines(document).stream()
        .filter(line -> line.startsWith("## "))
        .map(line -> line.substring(3).strip())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private static List<String> tableCells(String line) {
    String trimmed = line.strip();
    if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
      return List.of();
    }
    String[] rawCells = trimmed.substring(1, trimmed.length() - 1).split("\\|", -1);
    return java.util.Arrays.stream(rawCells).map(String::strip).toList();
  }

  private static String stripBackticks(String text) {
    String trimmed = text.strip();
    if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String relative(Path path) {
    return repositoryRoot()
        .relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), "/");
  }

  private static boolean isContractProtocolSource(Path root, Path path) {
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

  private static String sorted(Set<String> values) {
    return values.stream().sorted().collect(java.util.stream.Collectors.joining("\n"));
  }

  private record DocRoute(String symbol, String fileName, String section) {}
}
