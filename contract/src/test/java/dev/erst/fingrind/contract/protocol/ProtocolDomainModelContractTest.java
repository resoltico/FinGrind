package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the canonical bounded-context and vocabulary theory against drift. */
class ProtocolDomainModelContractTest extends ProtocolContractLintSupport {
  @Test
  void developerDomainModelReference_coversCanonicalContextMap() throws IOException {
    String document = Files.readString(repositoryRoot().resolve("docs/DEVELOPER_DOMAIN_MODEL.md"));
    Set<String> requiredFragments =
        new LinkedHashSet<>(
            List.of(
                "accounting entity",
                "Bounded Contexts",
                "Context Map",
                "published language",
                "anti-corruption layer",
                "executor.bookkeeping",
                "executor.workflow",
                "execute-plan",
                "SQLite adapter"));

    Set<String> violations = new LinkedHashSet<>();
    for (String fragment : requiredFragments) {
      if (!document.contains(fragment)) {
        violations.add("docs/DEVELOPER_DOMAIN_MODEL.md is missing `" + fragment + "`");
      }
    }

    assertTrue(
        violations.isEmpty(), () -> "Domain-model documentation drift:\n" + sorted(violations));
  }

  @Test
  void canonicalBookOwnerTerminology_usesAccountingEntityAcrossPrimaryDescriptions()
      throws IOException {
    Set<String> requiredFiles =
        Set.of(
            "README.md",
            "gradle.properties",
            "docs/DEVELOPER.md",
            "docs/DEVELOPER_SQLITE.md",
            "contract/src/main/java/dev/erst/fingrind/contract/protocol/ProtocolCatalogFacts.java");
    Set<String> forbiddenFragments =
        Set.of(
            "per business",
            "Each business gets one encrypted SQLite file",
            "one book belongs to one entity",
            "one book for one entity",
            "per entity book");
    Set<String> violations = new LinkedHashSet<>();

    for (String relativePath : requiredFiles) {
      Path file = repositoryRoot().resolve(relativePath);
      String text = Files.readString(file);
      if (!text.contains("accounting entity")) {
        violations.add(relativePath + " must use the canonical `accounting entity` term.");
      }
      for (String forbiddenFragment : forbiddenFragments) {
        if (text.contains(forbiddenFragment)) {
          violations.add(
              relativePath + " contains retired book-owner wording `" + forbiddenFragment + "`.");
        }
      }
    }

    assertTrue(violations.isEmpty(), () -> "Book-owner vocabulary drift:\n" + sorted(violations));
  }

  @Test
  void executorInternalBoundedContexts_keepPublishedLanguageAtTranslatorEdges() throws IOException {
    Set<String> violations = new LinkedHashSet<>();
    assertOnlyTranslatorImportsPublishedLanguage(
        repositoryRoot().resolve("executor/src/main/java/dev/erst/fingrind/executor/bookkeeping"),
        "BookkeepingPublishedLanguageTranslator.java",
        Set.of(
            "import dev.erst.fingrind.contract.DeclareAccountCommand;",
            "import dev.erst.fingrind.contract.DeclareAccountResult;",
            "import dev.erst.fingrind.contract.DeclaredAccount;",
            "import dev.erst.fingrind.contract.OpenBookResult;",
            "import dev.erst.fingrind.contract.PostEntryCommand;",
            "import dev.erst.fingrind.contract.PostingFact;",
            "import dev.erst.fingrind.contract.PostingLineage;"),
        violations);
    assertOnlyTranslatorImportsPublishedLanguage(
        repositoryRoot().resolve("executor/src/main/java/dev/erst/fingrind/executor/workflow"),
        "BookWorkflowPublishedLanguageTranslator.java",
        Set.of(
            "import dev.erst.fingrind.contract.LedgerAssertion;",
            "import dev.erst.fingrind.contract.LedgerPlan;",
            "import dev.erst.fingrind.contract.LedgerPlanId;",
            "import dev.erst.fingrind.contract.LedgerStep;",
            "import dev.erst.fingrind.contract.LedgerStepId;"),
        violations);

    assertTrue(
        violations.isEmpty(),
        () -> "Internal bounded-context contract leakage:\n" + sorted(violations));
  }

  @Test
  void sqliteWritePath_avoidsPublicWriteAndWorkflowDtos() throws IOException {
    Set<String> forbiddenImports =
        Set.of(
            "import dev.erst.fingrind.contract.DeclareAccountCommand;",
            "import dev.erst.fingrind.contract.PostEntryCommand;",
            "import dev.erst.fingrind.contract.LedgerPlan;",
            "import dev.erst.fingrind.contract.LedgerStep;",
            "import dev.erst.fingrind.contract.LedgerAssertion;");
    Set<String> violations = new LinkedHashSet<>();

    try (Stream<Path> files = Files.walk(repositoryRoot().resolve("sqlite/src/main/java"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        for (String forbiddenImport : forbiddenImports) {
          if (source.contains(forbiddenImport)) {
            violations.add(
                relative(file) + " imports published write/workflow DTO " + forbiddenImport);
          }
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () -> "SQLite write-path published-language leakage:\n" + sorted(violations));
  }

  @Test
  void executorModule_exportsLocalContextsWithoutQualifiedTargetDrift() throws IOException {
    String moduleInfo =
        Files.readString(repositoryRoot().resolve("executor/src/main/java/module-info.java"));

    Set<String> violations = new LinkedHashSet<>();
    if (!moduleInfo.contains("exports dev.erst.fingrind.executor.bookkeeping;")) {
      violations.add("executor module must export the bookkeeping context package.");
    }
    if (!moduleInfo.contains("exports dev.erst.fingrind.executor.workflow;")) {
      violations.add("executor module must export the workflow context package.");
    }
    if (moduleInfo.contains("exports dev.erst.fingrind.executor.bookkeeping to")) {
      violations.add("executor bookkeeping export may not use qualified JPMS targets.");
    }
    if (moduleInfo.contains("exports dev.erst.fingrind.executor.workflow to")) {
      violations.add("executor workflow export may not use qualified JPMS targets.");
    }

    assertTrue(violations.isEmpty(), () -> "Executor module export drift:\n" + sorted(violations));
  }

  private void assertOnlyTranslatorImportsPublishedLanguage(
      Path sourceRoot,
      String translatorFileName,
      Set<String> forbiddenImports,
      Set<String> violations)
      throws IOException {
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String fileName = file.getFileName().toString();
        if ("package-info.java".equals(fileName) || translatorFileName.equals(fileName)) {
          continue;
        }
        String source = Files.readString(file);
        for (String forbiddenImport : forbiddenImports) {
          if (source.contains(forbiddenImport)) {
            violations.add(
                relative(file)
                    + " imports published-language type outside the translator edge: "
                    + forbiddenImport);
          }
        }
      }
    }
  }
}
