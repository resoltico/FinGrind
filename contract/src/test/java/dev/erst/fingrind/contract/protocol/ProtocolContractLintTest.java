package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract-lint tests that keep user-facing operation references registered in contract. */
class ProtocolContractLintTest {
  private static final Pattern FINGRIND_COMMAND_PATTERN =
      Pattern.compile("\\bfingrind\\s+([a-z][a-z0-9-]*)");
  private static final Pattern BACKTICKED_HYPHEN_ID_PATTERN =
      Pattern.compile("`([a-z][a-z0-9]*(?:-[a-z0-9]+)+)`");
  private static final Set<String> NON_OPERATION_BACKTICK_IDS =
      Stream.concat(
              Set.of(
                  "account-normal-balance-conflict",
                  "account-state-violations",
                  "assertion-failed",
                  "book-already-initialized",
                  "book-contains-schema",
                  "book-not-initialized",
                  "build-logic",
                  "class-complete",
                  "class-start",
                  "cli-request",
                  "desktop-linux",
                  "docker-buildx",
                  "duplicate-idempotency-key",
                  "invalid-request",
                  "json-envelope",
                  "linux-aarch64",
                  "macos-aarch64",
                  "owner-only-acl",
                  "windows-x86_64",
                  "posting-not-found",
                  "posting-workflow",
                  "preflight-accepted",
                  "regression-input",
                  "raw-json",
                  "reversal-already-exists",
                  "reversal-does-not-negate-target",
                  "reversal-reason-forbidden",
                  "reversal-reason-required",
                  "reversal-target-not-found",
                  "runtime-failure",
                  "sqlite-book-roundtrip",
                  "sqlite-jdbc",
                  "test-complete",
                  "test-progress",
                  "unknown-account",
                  "unknown-command")
                  .stream(),
              ProtocolPlanKinds.all().stream())
          .collect(java.util.stream.Collectors.toUnmodifiableSet());

  @Test
  void productionJavaDoesNotReauthorHyphenatedOperationIdsOutsideContractProtocol()
      throws IOException {
    Set<String> registeredHyphenatedIds = registeredHyphenatedOperationIds();
    Set<String> violations = new HashSet<>();
    for (Path sourceFile : productionJavaFiles()) {
      String source = Files.readString(sourceFile);
      registeredHyphenatedIds.stream()
          .filter(operationId -> source.contains("\"" + operationId + "\""))
          .map(operationId -> relative(sourceFile) + " reauthors `" + operationId + "`")
          .forEach(violations::add);
    }

    assertTrue(violations.isEmpty(), () -> "Operation id authorship drift:\n" + sorted(violations));
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

  private static List<Path> productionJavaFiles() throws IOException {
    Path root = repositoryRoot();
    List<Path> files = new ArrayList<>();
    for (String sourceDirectory :
        List.of(
            "contract/src/main/java",
            "executor/src/main/java",
            "cli/src/main/java",
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
}
