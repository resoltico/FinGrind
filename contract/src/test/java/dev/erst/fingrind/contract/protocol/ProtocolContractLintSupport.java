package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Shared repository scanning helpers for contract-lint tests. */
class ProtocolContractLintSupport {
  protected static final Pattern FINGRIND_COMMAND_PATTERN =
      Pattern.compile("\\bfingrind\\s+([a-z][a-z0-9-]*)");
  protected static final Pattern STRING_LITERAL_PATTERN =
      Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"", Pattern.DOTALL);
  protected static final Pattern BACKTICKED_HYPHEN_ID_PATTERN =
      Pattern.compile("`([a-z][a-z0-9]*(?:-[a-z0-9]+)+)`");
  protected static final Pattern DOC_REFERENCE_LINK_PATTERN =
      Pattern.compile("\\[(DOC_\\d+_.+\\.md)]\\(\\./\\1\\)");
  protected static final Pattern MODULE_EXPORT_PATTERN =
      Pattern.compile("^\\s*exports\\s+([\\w\\.]+);", Pattern.MULTILINE);
  protected static final Pattern TOP_LEVEL_PUBLIC_TYPE_PATTERN =
      Pattern.compile(
          "^public\\s+(?:sealed\\s+|non-sealed\\s+|final\\s+|abstract\\s+)?(?:record|class|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)",
          Pattern.MULTILINE);
  protected static final Pattern NESTED_PUBLIC_TYPE_PATTERN =
      Pattern.compile(
          "^\\s{2,}public\\s+(?:static\\s+)?(?:sealed\\s+|non-sealed\\s+|final\\s+|abstract\\s+)?(?:record|class|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)",
          Pattern.MULTILINE);
  protected static final Pattern PACKAGE_PATTERN =
      Pattern.compile("^package\\s+([\\w\\.]+);", Pattern.MULTILINE);
  protected static final Set<String> NON_OPERATION_BACKTICK_IDS = nonOperationBacktickIds();

  protected ProtocolContractLintSupport() {}

  protected final Set<String> registeredOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  protected final Set<String> registeredHyphenatedOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .filter(operationId -> operationId.contains("-"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  protected final boolean containsToken(String text, String token) {
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

  protected final List<Path> documentationFiles() throws IOException {
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

  protected final Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(
        Objects.requireNonNull(directory, "directory").resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
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

  protected final Set<String> actualReferenceFiles() throws IOException {
    try (Stream<Path> files = Files.list(repositoryRoot().resolve("docs"))) {
      return files
          .filter(path -> path.getFileName().toString().matches("DOC_\\d+_.+\\.md"))
          .map(path -> path.getFileName().toString())
          .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
  }

  protected final Set<String> exportedPublicReferenceSymbols() throws IOException {
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

  protected final Set<String> exportedPackages(Path root) throws IOException {
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

  protected final java.util.stream.Stream<String> publicSymbols(
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

  protected final java.util.stream.Stream<String> publicSymbolsInFileUnchecked(
      Path sourceFile, Set<String> exportedPackages) {
    try {
      return publicSymbolsInFile(sourceFile, exportedPackages);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  protected final java.util.stream.Stream<String> publicSymbolsInFile(
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

  protected final Set<String> referenceAtomsExcludingApiIndex() throws IOException {
    Set<String> files = new LinkedHashSet<>(actualReferenceFiles());
    files.remove("DOC_00_Index.md");
    return Set.copyOf(files);
  }

  protected final Map<String, Set<String>> headingsByReferenceFile() throws IOException {
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

  protected final Set<String> headingsUnchecked(Path document) {
    try {
      return headings(document);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  protected final Set<String> referencedDocFiles(Path document) throws IOException {
    Set<String> files = new LinkedHashSet<>();
    String text = Files.readString(document);
    for (java.util.regex.MatchResult match :
        DOC_REFERENCE_LINK_PATTERN.matcher(text).results().toList()) {
      files.add(match.group(1));
    }
    return files;
  }

  protected final Set<String> indexedDocFiles(Path document) throws IOException {
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

  protected final List<DocRoute> symbolRoutes(Path document) throws IOException {
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

  protected final Set<String> headings(Path document) throws IOException {
    return Files.readAllLines(document).stream()
        .filter(line -> line.startsWith("## "))
        .map(line -> line.substring(3).strip())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  protected final List<String> tableCells(String line) {
    String trimmed = line.strip();
    if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
      return List.of();
    }
    String[] rawCells = trimmed.substring(1, trimmed.length() - 1).split("\\|", -1);
    return java.util.Arrays.stream(rawCells).map(String::strip).toList();
  }

  protected final String stripBackticks(String text) {
    String trimmed = text.strip();
    if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
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

  protected record DocRoute(String symbol, String fileName, String section) {}

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
                "account-role-conflict",
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
                "devcontainer-changes",
                "docker-buildx",
                "docker-smoke",
                "duplicate-idempotency-key",
                "expected-invalid",
                "export-ignore",
                "fuzz-all",
                "fuzz-cli-request",
                "fuzz-ledger-plan-request",
                "fuzz-posting-workflow",
                "fuzz-sqlite-book-roundtrip",
                "inactive-account",
                "invalid-request",
                "json-envelope",
                "ledger-plan-request",
                "list-findings",
                "owner-only-acl",
                "posting-not-found",
                "posting-book-not-initialized",
                "query-book-not-initialized",
                "posting-workflow",
                "promote-seed",
                "replace-before-commit-effective-date",
                "regression-input",
                "semver-major",
                "raw-json",
                "replay-clean",
                "reversal-already-exists",
                "reversal-does-not-negate-target",
                "reversal-target-not-found",
                "runtime-failure",
                "seed-audit",
                "sqlite-book-roundtrip",
                "sqlite-jdbc",
                "test-complete",
                "test-progress",
                "timeout-minutes",
                "unknown-account",
                "unknown-command",
                "macos-15",
                "macos-latest",
                "ubuntu-latest",
                "unexpected-failure",
                "windows-bundle-smoke",
                "windows-2022",
                "windows-latest",
                "report-pdf"));
    ids.addAll(BookInspection.Status.wireValues());
    ids.addAll(BookMigrationPolicyMode.wireValues());
    ids.addAll(
        ContractErrors.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.ErrorDescriptor::code)
            .toList());
    ids.addAll(
        BookAdministrationRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        BookQueryRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        BookMaintenanceRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        PostingRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(LedgerAssertionKind.wireValues());
    ids.addAll(LedgerBoundaryPhase.wireValues());
    ids.addAll(LedgerJournalKind.wireValues());
    ids.addAll(RequestFieldPresence.wireValues());
    ids.addAll(RuntimeDistribution.wireValues());
    ids.addAll(PublicCliDistribution.wireValues());
    ids.addAll(StorageDriver.wireValues());
    ids.addAll(StorageEngine.wireValues());
    ids.addAll(BookProtectionMode.wireValues());
    ids.addAll(BookCipher.wireValues());
    ids.addAll(SqliteLibraryMode.wireValues());
    ids.addAll(SqliteRuntimeProvenance.wireValues());
    ids.addAll(SqliteRuntimeStatus.wireValues());
    ids.addAll(SqliteRuntimeTrustBasis.wireValues());
    ids.addAll(SqliteCompileOptionsVerificationStatus.wireValues());
    ids.addAll(WorkflowSurface.wireValues());
    ids.addAll(ProtocolSuccessStatus.wireValues());
    ids.addAll(ProtocolRejectionStatus.wireValues());
    ids.addAll(ProtocolFailureStatus.wireValues());
    ids.addAll(ProtocolDiagnosticCode.wireValues());
    ids.addAll(PublicCliBundleTarget.wireValues());
    ids.addAll(PlanTransactionMode.wireValues());
    ids.addAll(PlanFailurePolicy.wireValues());
    ids.addAll(PlanResultDetail.wireValues());
    return Set.copyOf(ids);
  }
}
