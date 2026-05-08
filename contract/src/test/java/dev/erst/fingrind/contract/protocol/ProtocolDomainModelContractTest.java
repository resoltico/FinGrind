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
                "Public Bookkeeping Protocol Context",
                "Public Workflow Protocol Context",
                "Runtime And Discovery Contract Context",
                "shared kernel",
                "CurrencyBalance",
                "EffectiveDateRange",
                "anti-corruption layer",
                "executor.bookkeeping",
                "executor.workflow",
                "BookWorkflowJournalEntry",
                "BookWorkflowExecutionJournal",
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
        Set.of(
            "BookkeepingPublishedLanguageTranslator.java",
            "BookkeepingReadPublishedLanguageTranslator.java"),
        Set.of(
            "import dev.erst.fingrind.contract.AccountBalanceQuery;",
            "import dev.erst.fingrind.contract.AccountBalanceSnapshot;",
            "import dev.erst.fingrind.contract.AccountLedgerEntry;",
            "import dev.erst.fingrind.contract.AccountLedgerQuery;",
            "import dev.erst.fingrind.contract.AccountLedgerReport;",
            "import dev.erst.fingrind.contract.AccountPage;",
            "import dev.erst.fingrind.contract.AccountPageCursor;",
            "import dev.erst.fingrind.contract.DeclareAccountCommand;",
            "import dev.erst.fingrind.contract.DeclareAccountResult;",
            "import dev.erst.fingrind.contract.DeclaredAccount;",
            "import dev.erst.fingrind.contract.ListAccountsQuery;",
            "import dev.erst.fingrind.contract.ListPostingsQuery;",
            "import dev.erst.fingrind.contract.OpenBookResult;",
            "import dev.erst.fingrind.contract.PeriodAccountActivityRow;",
            "import dev.erst.fingrind.contract.PeriodCurrencySummary;",
            "import dev.erst.fingrind.contract.PeriodSummaryQuery;",
            "import dev.erst.fingrind.contract.PeriodSummaryReport;",
            "import dev.erst.fingrind.contract.PostEntryCommand;",
            "import dev.erst.fingrind.contract.PostingFact;",
            "import dev.erst.fingrind.contract.PostingLineage;",
            "import dev.erst.fingrind.contract.PostingPage;",
            "import dev.erst.fingrind.contract.PostingPageCursor;",
            "import dev.erst.fingrind.contract.TrialBalanceQuery;",
            "import dev.erst.fingrind.contract.TrialBalanceReport;",
            "import dev.erst.fingrind.contract.TrialBalanceRow;"),
        violations);
    assertOnlyTranslatorImportsPublishedLanguage(
        repositoryRoot().resolve("executor/src/main/java/dev/erst/fingrind/executor/workflow"),
        Set.of("BookWorkflowPublishedLanguageTranslator.java"),
        Set.of(
            "import dev.erst.fingrind.contract.AccountBalanceQuery;",
            "import dev.erst.fingrind.contract.LedgerBoundaryPhase;",
            "import dev.erst.fingrind.contract.LedgerAssertion;",
            "import dev.erst.fingrind.contract.LedgerExecutionJournal;",
            "import dev.erst.fingrind.contract.LedgerJournalEntry;",
            "import dev.erst.fingrind.contract.LedgerJournalStep;",
            "import dev.erst.fingrind.contract.LedgerPlan;",
            "import dev.erst.fingrind.contract.LedgerPlanId;",
            "import dev.erst.fingrind.contract.LedgerPlanResult;",
            "import dev.erst.fingrind.contract.LedgerPlanStatus;",
            "import dev.erst.fingrind.contract.LedgerStepFailure;",
            "import dev.erst.fingrind.contract.LedgerStep;",
            "import dev.erst.fingrind.contract.LedgerStepId;",
            "import dev.erst.fingrind.contract.ListAccountsQuery;",
            "import dev.erst.fingrind.contract.ListPostingsQuery;"),
        violations);

    assertTrue(
        violations.isEmpty(),
        () -> "Internal bounded-context contract leakage:\n" + sorted(violations));
  }

  @Test
  void sqliteAdapter_avoidsPublishedBookkeepingAndWorkflowDtos() throws IOException {
    Set<String> forbiddenImports =
        Set.of(
            "import dev.erst.fingrind.contract.AccountBalanceQuery;",
            "import dev.erst.fingrind.contract.AccountBalanceSnapshot;",
            "import dev.erst.fingrind.contract.AccountLedgerEntry;",
            "import dev.erst.fingrind.contract.AccountLedgerQuery;",
            "import dev.erst.fingrind.contract.AccountLedgerReport;",
            "import dev.erst.fingrind.contract.AccountPage;",
            "import dev.erst.fingrind.contract.AccountPageCursor;",
            "import dev.erst.fingrind.contract.DeclareAccountCommand;",
            "import dev.erst.fingrind.contract.PostEntryCommand;",
            "import dev.erst.fingrind.contract.DeclaredAccount;",
            "import dev.erst.fingrind.contract.LedgerPlan;",
            "import dev.erst.fingrind.contract.LedgerAssertion;",
            "import dev.erst.fingrind.contract.LedgerStep;",
            "import dev.erst.fingrind.contract.ListAccountsQuery;",
            "import dev.erst.fingrind.contract.ListPostingsQuery;",
            "import dev.erst.fingrind.contract.PeriodAccountActivityRow;",
            "import dev.erst.fingrind.contract.PeriodCurrencySummary;",
            "import dev.erst.fingrind.contract.PeriodSummaryQuery;",
            "import dev.erst.fingrind.contract.PeriodSummaryReport;",
            "import dev.erst.fingrind.contract.PostingFact;",
            "import dev.erst.fingrind.contract.PostingPage;",
            "import dev.erst.fingrind.contract.PostingPageCursor;",
            "import dev.erst.fingrind.contract.TrialBalanceQuery;",
            "import dev.erst.fingrind.contract.TrialBalanceReport;",
            "import dev.erst.fingrind.contract.TrialBalanceRow;");
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
        () -> "SQLite adapter published-language leakage:\n" + sorted(violations));
  }

  @Test
  void executorModule_exportsOnlyTheIntendedPublicAndAdapterBridgePackages() throws IOException {
    String moduleInfo =
        Files.readString(repositoryRoot().resolve("executor/src/main/java/module-info.java"));

    Set<String> violations = new LinkedHashSet<>();
    if (!moduleInfo.contains("exports dev.erst.fingrind.executor;")) {
      violations.add("executor module must export the public application-service package.");
    }
    if (!moduleInfo.contains("exports dev.erst.fingrind.executor.bookkeeping;")) {
      violations.add("executor module must export the bookkeeping bridge vocabulary unqualified.");
    }
    if (!moduleInfo.contains("exports dev.erst.fingrind.executor.spi;")) {
      violations.add("executor module must export the explicit store seam vocabulary unqualified.");
    }
    if (moduleInfo.contains("exports dev.erst.fingrind.executor.workflow;")) {
      violations.add("executor workflow context must remain internal to the module.");
    }

    assertTrue(violations.isEmpty(), () -> "Executor module export drift:\n" + sorted(violations));
  }

  private void assertOnlyTranslatorImportsPublishedLanguage(
      Path sourceRoot,
      Set<String> translatorFileNames,
      Set<String> forbiddenImports,
      Set<String> violations)
      throws IOException {
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String fileName = file.getFileName().toString();
        if ("package-info.java".equals(fileName) || translatorFileNames.contains(fileName)) {
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
