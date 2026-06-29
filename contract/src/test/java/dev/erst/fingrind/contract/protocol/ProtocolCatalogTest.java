package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the core-owned protocol catalog. */
class ProtocolCatalogTest {
  @Test
  void operations_followCanonicalPublicOrder() {
    List<String> operationNames =
        ProtocolCatalog.operations().stream().map(operation -> operation.id().wireName()).toList();
    assertEquals(
        Arrays.stream(OperationId.values()).map(OperationId::wireName).toList(), operationNames);
    assertEquals("open-book", ProtocolCatalog.operationName(OperationId.OPEN_BOOK));
    assertEquals("Help", ProtocolCatalog.operation(OperationId.HELP).displayLabel());
    assertEquals(
        "raw-json",
        ProtocolCatalog.operation(OperationId.PRINT_REQUEST_TEMPLATE).executionMode().wireValue());
  }

  @Test
  void operationLookup_acceptsCanonicalNamesAndAliases() {
    assertSame(
        ProtocolCatalog.operation(OperationId.HELP),
        ProtocolCatalog.findByToken(OperationId.HELP.wireName()).orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.HELP),
        ProtocolCatalog.findByToken("--help").orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.VERSION),
        ProtocolCatalog.findByToken("--version").orElseThrow());
    assertSame(
        ProtocolCatalog.operation(OperationId.PRINT_REQUEST_TEMPLATE),
        ProtocolCatalog.findByToken("--print-request-template").orElseThrow());
    assertTrue(ProtocolCatalog.findByToken("unknown").isEmpty());
  }

  @Test
  void catalogIndexesRejectDuplicateIdsAndTokens() {
    ProtocolOperation help = operation(OperationId.HELP, List.of("--same"));
    ProtocolOperation duplicateHelp = operation(OperationId.HELP, List.of("--other"));
    ProtocolOperation version = operation(OperationId.VERSION, List.of("--same"));
    assertThrows(
        IllegalStateException.class,
        () -> ProtocolCatalogIndexSupport.indexById(List.of(help, duplicateHelp)));
    assertThrows(
        IllegalStateException.class,
        () -> ProtocolCatalogIndexSupport.indexByToken(List.of(help, version)));
  }

  @Test
  void requireOperation_reportsMissingCanonicalRegistration() {
    IllegalStateException missingOperation =
        assertThrows(
            IllegalStateException.class,
            () ->
                ProtocolCatalogIndexSupport.requireOperation(
                    Map.of(OperationId.HELP, ProtocolCatalog.operation(OperationId.HELP)),
                    OperationId.VERSION));

    assertEquals(
        "No protocol catalog entry is registered for operationId VERSION.",
        missingOperation.getMessage());
  }

  @Test
  void operationGroups_areDerivedFromCatalogCategories() {
    assertEquals(
        List.of(
            "help",
            "version",
            "capabilities",
            "environment",
            "print-request-template",
            "print-plan-template"),
        ProtocolCatalog.operationNames(OperationCategory.DISCOVERY));
    assertEquals(
        List.of(
            "generate-book-key-file",
            "open-book",
            "rekey-book",
            "backup-book",
            "restore-book",
            "inspect-rekey-rollback",
            "delete-rekey-rollback",
            "restore-rekey-rollback",
            "declare-account",
            "declare-tax-registration",
            "interim-result-sweep",
            "fiscal-year-close"),
        ProtocolCatalog.operationNames(OperationCategory.ADMINISTRATION));
    assertEquals(
        List.of(
            "inspect-book",
            "list-accounts",
            "list-tax-registrations",
            "tax-obligation",
            "get-posting",
            "list-postings",
            "account-balance",
            "trial-balance",
            "account-ledger",
            "period-summary",
            "financial-position",
            "income-statement",
            "cash-flow-statement",
            "changes-in-equity"),
        ProtocolCatalog.operationNames(OperationCategory.QUERY));
    assertEquals(
        List.of(
            "execute-plan",
            "preflight-entry",
            "record-sale",
            "record-expense",
            "record-owner-contribution",
            "record-owner-withdrawal",
            "record-opening-position",
            "record-reversal",
            "post-entry"),
        ProtocolCatalog.operationNames(OperationCategory.WRITE));
  }

  @Test
  void protocolDescriptorRecords_rejectBlankTextAndNullCollectionsWithContext() {
    ProtocolOperation operation =
        new ProtocolOperation(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            new ProtocolCommandSignature("Help", List.of(), List.of(), "fingrind help"),
            new ProtocolOperationOutputs(ExecutionMode.JSON_ENVELOPE, List.of(), List.of()),
            new ProtocolOperationDocumentation("summary", List.of()));
    ProtocolOperationDefinitions.OperationDefinition definition =
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            "Help",
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "summary",
            List.of());
    assertEquals(List.of(), operation.aliases());
    assertEquals(List.of(), operation.options());
    assertEquals(List.of(), operation.outputModes());
    assertEquals(List.of(), operation.artifactOutputs());
    assertEquals(List.of(), operation.exampleSteps());
    assertEquals(List.of(), definition.aliases());
    assertEquals(List.of(), definition.options());
    assertEquals(List.of(), definition.outputModes());
    assertEquals(List.of(), definition.artifactOutputs());
    assertEquals(List.of(), definition.exampleSteps());
    assertEquals(
        "aliases must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new ProtocolCommandSignature("Help", nullOf(), List.of(), "fingrind help"))
            .getMessage());
    assertEquals(
        "outputModes must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new ProtocolOperationOutputs(ExecutionMode.JSON_ENVELOPE, nullOf(), List.of()))
            .getMessage());
    assertEquals(
        "exampleSteps must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> new ProtocolOperationDocumentation("summary", nullOf()))
            .getMessage());
    assertEquals(
        "artifactOutputs must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new ProtocolOperationDefinitions.OperationDefinition(
                        OperationId.HELP,
                        OperationCategory.DISCOVERY,
                        "Help",
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(),
                        nullOf(),
                        "summary",
                        List.of()))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class, () -> new ProtocolOperationDocumentation(" ", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtocolOperation(
                OperationId.HELP,
                OperationCategory.DISCOVERY,
                new ProtocolCommandSignature(" ", List.of(), List.of(), "fingrind help"),
                new ProtocolOperationOutputs(ExecutionMode.JSON_ENVELOPE, List.of(), List.of()),
                new ProtocolOperationDocumentation("summary", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtocolOperationDefinitions.OperationDefinition(
                OperationId.HELP,
                OperationCategory.DISCOVERY,
                " ",
                List.of(),
                List.of(),
                ExecutionMode.JSON_ENVELOPE,
                List.of(),
                List.of(),
                "summary",
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtocolArtifactOutput(" ", "--pdf-out <path>", "Exports one PDF report."));
  }

  @Test
  void artifactOutputCatalog_publishesStableFormatsAndOptions() {
    assertEquals("pdf", ProtocolArtifactOutput.pdfFormat());
    assertEquals("book-key-file", ProtocolArtifactOutput.bookKeyFileFormat());
    assertEquals("backup-book-file", ProtocolArtifactOutput.backupBookFileFormat());
    assertEquals("backup-book-key-file", ProtocolArtifactOutput.backupBookKeyFileFormat());
    assertEquals("rollback-book-file", ProtocolArtifactOutput.rollbackBookFileFormat());
    assertEquals(
        "--new-book-key-file <existing-path>",
        ProtocolArtifactOutput.replacementBookKeyFile().option());
    assertEquals(
        "--backup-book-file <path>", ProtocolArtifactOutput.selectedBackupBookFile().option());
    assertEquals(
        "--backup-book-key-file <path>",
        ProtocolArtifactOutput.selectedBackupBookKeyFile().option());
    assertEquals("--rollback-book-file <path>", ProtocolArtifactOutput.rollbackBookFile().option());
    assertEquals(
        "--book-file <path>", ProtocolArtifactOutput.discoveredRollbackBookFile().option());
  }

  @Test
  void bookkeepingKernelFacts_requireBuiltInStatementsToMatchImplementedCapabilities() {
    BookkeepingKernelFacts kernel = ProtocolCatalog.domain().bookkeepingKernel();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookkeepingKernelFacts(
                kernel.scope(),
                List.of("statement-of-cash-flows"),
                kernel.reportCapabilities(),
                kernel.description()));
  }

  @Test
  void bookkeepingKernelFacts_publishCurrentExecutableKernelInventory() {
    BookkeepingKernelFacts kernel = ProtocolCatalog.domain().bookkeepingKernel();
    assertEquals(
        dev.erst.fingrind.core.AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL
            .value(),
        kernel.scope());
    assertEquals(
        List.of(
            "financial-position", "income-statement", "cash-flow-statement", "changes-in-equity"),
        kernel.builtInStatements());
    assertEquals(
        List.of(
            "financial-position", "income-statement", "cash-flow-statement", "changes-in-equity"),
        kernel.reportCapabilities().stream().map(ReportCapabilityFacts::statementId).toList());
    assertTrue(
        kernel.reportCapabilities().stream()
            .allMatch(
                reportCapability ->
                    reportCapability
                        .comparativeModes()
                        .equals(List.of("none", "prior-period", "range"))));
    assertTrue(
        kernel.reportCapabilities().stream()
            .allMatch(reportCapability -> "none".equals(reportCapability.comparativeDefault())));
    assertTrue(kernel.description().contains("internal-management"));
  }

  @Test
  void reportCapabilityFacts_rejectEmptyComparativeModesAndMissingDefaults() {
    IllegalArgumentException emptyModesFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ReportCapabilityFacts(
                    "financial-position", List.of(), "none", "Statement of financial position."));
    IllegalArgumentException missingDefaultFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ReportCapabilityFacts(
                    "financial-position",
                    List.of("none", "prior-period"),
                    "range",
                    "Statement of financial position."));

    assertEquals(
        "comparativeModes must contain at least one mode.", emptyModesFailure.getMessage());
    assertEquals(
        "comparativeDefault must be present in comparativeModes.",
        missingDefaultFailure.getMessage());
  }

  @Test
  void asOfReportDescriptions_publishResolvedLatestDateLanguage() {
    ProtocolOperation trialBalance = ProtocolCatalog.operation(OperationId.TRIAL_BALANCE);
    ProtocolOperation financialPosition = ProtocolCatalog.operation(OperationId.FINANCIAL_POSITION);

    assertTrue(
        trialBalance.analysisSummary().contains("latest effective date in the selected book"));
    assertTrue(
        financialPosition.analysisSummary().contains("latest effective date in the selected book"));
  }

  @Test
  void operationIdContract_loadsAndRejectsMissingMappings() {
    OperationIdContract contract =
        OperationIdContract.loadFromResource(
            new ByteArrayInputStream(
                """
                {"HELP":"help","VERSION":"version"}
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "memory");
    assertEquals("help", contract.wireName("HELP"));
    assertThrows(IllegalStateException.class, () -> contract.wireName("UNKNOWN"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OperationIdContract.loadFromResource(
                new ByteArrayInputStream(
                    """
                    {"HELP":"   "}
                    """
                        .getBytes(StandardCharsets.UTF_8)),
                "blank"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OperationIdContract.loadFromResource(
                new ByteArrayInputStream(
                    """
                    ["HELP"]
                    """
                        .getBytes(StandardCharsets.UTF_8)),
                "array.json"));
    assertThrows(
        IllegalStateException.class,
        () -> OperationIdContract.loadFromResource(nullOf(), "missing.json"));
    assertThrows(
        UncheckedIOException.class,
        () ->
            OperationIdContract.loadFromResource(
                new InputStream() {
                  @Override
                  public int read() throws IOException {
                    throw new IOException("boom");
                  }

                  @Override
                  public int read(byte[] buffer, int offset, int length) throws IOException {
                    throw new IOException("boom");
                  }
                },
                "broken.json"));
  }

  private static ProtocolOperation operation(OperationId id, List<String> aliases) {
    return new ProtocolOperation(
        id,
        OperationCategory.DISCOVERY,
        new ProtocolCommandSignature(
            id.wireName(), aliases, List.of(), "fingrind " + id.wireName()),
        new ProtocolOperationOutputs(
            ExecutionMode.JSON_ENVELOPE, List.of(OutputMode.JSON), List.of()),
        new ProtocolOperationDocumentation("summary", List.of()));
  }

  @Test
  void operationDescriptors_renderLimitsOptionsUsageAndExamplesFromProtocolFacts() {
    ProtocolOperation listAccounts = ProtocolCatalog.operation(OperationId.LIST_ACCOUNTS);
    ProtocolOperation trialBalance = ProtocolCatalog.operation(OperationId.TRIAL_BALANCE);
    ProtocolOperation printRequestTemplate =
        ProtocolCatalog.operation(OperationId.PRINT_REQUEST_TEMPLATE);
    ProtocolOperation executePlan = ProtocolCatalog.operation(OperationId.EXECUTE_PLAN);
    assertEquals("[--limit <1-200>]", ProtocolOptions.optionalLimitSyntax());
    assertEquals("[--cursor <cursor>]", ProtocolOptions.optionalCursorSyntax());
    assertEquals(
        "[--output <json|text|csv>]",
        ProtocolOptions.optionalOutputSyntax(
            List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV)));
    assertEquals("[--pdf-out <path>]", ProtocolOptions.optionalPdfOutSyntax());
    assertEquals(
        "[--detail <minimal|compact|full>]", ProtocolOptions.optionalDiscoveryDetailSyntax());
    assertEquals(
        "[--detail <minimal|compact|full> (json only)]",
        ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        ProtocolOptions.bookPassphraseOptions());
    assertEquals(50, ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT);
    assertEquals(200, ProtocolInteractionLimits.PAGE_LIMIT_MAX);
    assertEquals(100, ProtocolInteractionLimits.LEDGER_PLAN_STEP_MAX);
    assertTrue(listAccounts.options().contains("[--limit <1-200>]"));
    assertTrue(listAccounts.usage().contains("[--book-key-file <path> | --book-passphrase-stdin"));
    assertEquals(
        new ProtocolExampleStep.Command(
            "fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --limit 50"),
        listAccounts.exampleSteps().getFirst());
    assertTrue(
        printRequestTemplate.exampleSteps().stream()
            .anyMatch(ProtocolExampleStep.Note.class::isInstance));
    assertTrue(
        executePlan.exampleSteps().stream().anyMatch(ProtocolExampleStep.Note.class::isInstance));
    assertEquals(
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV), trialBalance.outputModes());
    assertTrue(trialBalance.options().contains("[--output <json|text|csv>]"));
    assertTrue(trialBalance.options().contains("[--pdf-out <path>]"));
    assertEquals(1, trialBalance.artifactOutputs().size());
    assertEquals("pdf", trialBalance.artifactOutputs().getFirst().format());
    assertEquals("--pdf-out <path>", trialBalance.artifactOutputs().getFirst().option());
    assertEquals(List.of(), printRequestTemplate.outputModes());
    assertEquals(List.of(OutputMode.JSON, OutputMode.TEXT), executePlan.outputModes());
    assertFalse(printRequestTemplate.options().contains("[--output <json>]"));
    assertTrue(executePlan.options().contains("[--output <json|text>]"));
  }

  @Test
  void operations_keepSelectableOutputAndArtifactOptionsInCanonicalOptionLists() {
    for (ProtocolOperation operation : ProtocolCatalog.operations()) {
      if (!operation.outputModes().isEmpty()) {
        assertTrue(
            operation
                .options()
                .contains(ProtocolOptions.optionalOutputSyntax(operation.outputModes())),
            () ->
                "Missing canonical --output syntax for "
                    + operation.id().wireName()
                    + ": "
                    + operation.options());
      }
      if (!operation.artifactOutputs().isEmpty()) {
        for (ProtocolArtifactOutput artifactOutput : operation.artifactOutputs()) {
          assertTrue(
              operation.options().stream()
                  .anyMatch(option -> option.contains(artifactOutput.option())),
              () ->
                  "Missing canonical artifact option syntax for "
                      + operation.id().wireName()
                      + " artifact "
                      + artifactOutput.format()
                      + ": "
                      + operation.options());
        }
      }
    }
  }

  @Test
  void globalFacts_publishTheCurrentBookModelAndRuntimeContract() {
    ManagedSqliteContract managedSqliteContract = ManagedSqliteContracts.current();

    assertEquals(List.of(StorageEngine.SQLITE), ProtocolCatalog.runtime().storageEngines());
    assertEquals(
        RuntimeDistribution.DIRECT_JAVA_INVOCATION,
        ProtocolCatalog.distribution().directJavaRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE,
        ProtocolCatalog.distribution().sourceCheckoutRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.CONTAINER_IMAGE,
        ProtocolCatalog.distribution().containerRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE,
        ProtocolCatalog.distribution().bundleRuntimeDistribution());
    assertEquals(
        PublicCliDistribution.SELF_CONTAINED_BUNDLE,
        ProtocolCatalog.distribution().publicCliDistribution());
    assertEquals(StorageDriver.SQLITE_FFM_SQLITE3MC, ProtocolCatalog.runtime().storageDriver());
    assertEquals(StorageEngine.SQLITE, ProtocolCatalog.runtime().storageEngine());
    assertEquals(BookProtectionMode.REQUIRED, ProtocolCatalog.runtime().bookProtectionMode());
    assertEquals(BookCipher.CHACHA20, ProtocolCatalog.runtime().protectedBookFormat().cipher());
    assertFalse(ProtocolCatalog.runtime().protectedBookFormat().legacyMode());
    assertEquals(4096, ProtocolCatalog.runtime().protectedBookFormat().pageSize());
    assertEquals(32, ProtocolCatalog.runtime().protectedBookFormat().reservedBytes());
    assertEquals(4096, ProtocolCatalog.runtime().protectedBookFormat().legacyPageSize());
    assertEquals(64007, ProtocolCatalog.runtime().protectedBookFormat().kdfIter());
    assertEquals(0, ProtocolCatalog.runtime().protectedBookFormat().plaintextHeaderSize());
    assertEquals(BookCipher.CHACHA20, ProtocolCatalog.runtime().defaultBookCipher());
    assertEquals(SqliteLibraryMode.MANAGED_ONLY, ProtocolCatalog.runtime().sqliteLibraryMode());
    assertEquals(
        "fingrind.bundle.home", ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty());
    assertEquals(
        managedSqliteContract.requiredMinimumSqliteVersion(),
        ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion());
    assertEquals(
        managedSqliteContract.requiredSqlite3mcVersion(),
        ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion());
    assertEquals(
        managedSqliteContract.requiredSqliteSourceId(),
        ProtocolCatalog.managedSqlite().requiredSqliteSourceId());
    assertEquals(
        managedSqliteContract.requiredCompileOptions(),
        ProtocolCatalog.managedSqlite().requiredCompileOptions());
    assertEquals(
        List.of(
            ProtocolEnvelopeStatus.OK,
            ProtocolEnvelopeStatus.REJECTED,
            ProtocolEnvelopeStatus.ERROR),
        ProtocolCatalog.envelopes().statuses());
    assertEquals(ProtocolEnvelopeStatus.OK, ProtocolCatalog.envelopes().successStatus());
    assertEquals(ProtocolEnvelopeStatus.REJECTED, ProtocolCatalog.envelopes().rejectionStatus());
    assertEquals(ProtocolEnvelopeStatus.ERROR, ProtocolCatalog.envelopes().errorStatus());
    assertEquals(
        "single-functional-currency-per-book",
        ProtocolCatalog.domain().bookModel().currencyScope());
    assertEquals(
        "owned-foreign-exchange-only", ProtocolCatalog.domain().currency().multiCurrencyStatus());
    assertEquals("advisory", ProtocolCatalog.domain().preflight().semantics());
    assertFalse(ProtocolCatalog.domain().preflight().commitGuarantee());
    assertEquals(
        PlanTransactionMode.ATOMIC, ProtocolCatalog.domain().planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
        ProtocolCatalog.domain().planExecution().failurePolicy());
    assertTrue(ProtocolCatalog.domain().planExecution().journal().contains("per-step journal"));
    assertTrue(
        ProtocolCatalog.domain().planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("open-book")));
    assertTrue(
        ProtocolCatalog.domain().planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("100 steps")));
    assertEquals(
        BundleLayoutContracts.current().supportedPublicCliBundleTargets(),
        ProtocolCatalog.distribution().supportedPublicCliBundleTargets());
    assertEquals(
        BundleLayoutContracts.current().unsupportedPublicCliBundleTargets(),
        ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets());
    assertEquals(
        BundleLayoutContracts.current().bundleTargets().keySet(),
        java.util.stream.Stream.concat(
                ProtocolCatalog.distribution().supportedPublicCliBundleTargets().stream(),
                ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets().stream())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    assertEquals(
        "./bin/fingrind",
        ProtocolCatalog.distribution().bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64));
    assertEquals(
        "bin/fingrind.ps1",
        ProtocolCatalog.distribution().bundleLauncherPath(PublicCliBundleTarget.WINDOWS_X86_64));
  }

  @Test
  void planExecutionFactsAndPlanFieldConstantsValidateTheirShape() {
    PlanExecutionFacts facts =
        new PlanExecutionFacts(
            PlanTransactionMode.ATOMIC,
            PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
            "complete journal",
            List.of("limit"));
    assertEquals(List.of("limit"), facts.hardLimitations());
    assertEquals(List.of("planId", "steps"), ProtocolLedgerPlanFields.planFields());
    assertEquals(
        List.of(
            "stepId",
            "kind",
            "ensureBook",
            "posting",
            "declareAccount",
            "query",
            "assertion",
            "postingId"),
        ProtocolLedgerPlanFields.stepFields());
    assertEquals(
        List.of(
            "accountCode",
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingCoverage",
            "limit",
            "cursor"),
        ProtocolLedgerPlanFields.queryFields());
    assertEquals(
        List.of(
            "kind",
            "accountCode",
            "postingId",
            "effectiveDateFrom",
            "effectiveDateTo",
            "netAmount",
            "balanceSide"),
        ProtocolLedgerPlanFields.assertionFields());
    assertEquals("planId", ProtocolLedgerPlanFields.Plan.PLAN_ID);
    assertEquals("steps", ProtocolLedgerPlanFields.Plan.STEPS);
    assertEquals("stepId", ProtocolLedgerPlanFields.Step.STEP_ID);
    assertEquals("kind", ProtocolLedgerPlanFields.Step.KIND);
    assertEquals("ensureBook", ProtocolLedgerPlanFields.Step.ENSURE_BOOK);
    assertEquals("posting", ProtocolLedgerPlanFields.Step.POSTING);
    assertEquals("declareAccount", ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT);
    assertEquals("query", ProtocolLedgerPlanFields.Step.QUERY);
    assertEquals("assertion", ProtocolLedgerPlanFields.Step.ASSERTION);
    assertEquals("postingId", ProtocolLedgerPlanFields.Step.POSTING_ID);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Query.ACCOUNT_CODE);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
    assertEquals("limit", ProtocolLedgerPlanFields.Query.LIMIT);
    assertEquals("cursor", ProtocolLedgerPlanFields.Query.CURSOR);
    assertEquals("kind", ProtocolLedgerPlanFields.Assertion.KIND);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
    assertEquals("postingId", ProtocolLedgerPlanFields.Assertion.POSTING_ID);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO);
    assertEquals("netAmount", ProtocolLedgerPlanFields.Assertion.NET_AMOUNT);
    assertEquals("balanceSide", ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE);
    assertEquals(
        List.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "revenueAccountCode",
            "expenseAccountCode",
            "equityAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "lines",
            "openingBalances",
            "evidence",
            "provenance",
            "reversal"),
        ProtocolPostEntryFields.topLevelFields());
    assertEquals(
        List.of("accountCode", "side", "amount"), ProtocolPostEntryFields.journalLineFields());
    assertEquals(
        List.of("accountCode", "side", "amount"), ProtocolPostEntryFields.openingBalanceFields());
    assertEquals(
        List.of("transactionAmount", "functionalAmount", "quotedRate", "treatmentKind"),
        ProtocolPostEntryFields.foreignExchangeFields());
    assertEquals(
        List.of("transactionCurrencyAmount", "functionalCurrencyAmount", "quotedOn", "quoteSource"),
        ProtocolPostEntryFields.quotedRateFields());
    assertEquals(List.of("sourceDocuments", "approvals"), ProtocolPostEntryFields.evidenceFields());
    assertEquals(
        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
        ProtocolPostEntryFields.sourceDocumentFields());
    assertEquals(
        List.of(
            "approvalId", "approvalType", "approverId", "approverType", "decision", "approvedAt"),
        ProtocolPostEntryFields.approvalFields());
    assertEquals(
        List.of(
            "actorId", "actorType", "commandId", "idempotencyKey", "causationId", "correlationId"),
        ProtocolPostEntryFields.provenanceFields());
    assertEquals(List.of("priorPostingId", "reason"), ProtocolPostEntryFields.reversalFields());
    assertEquals("transactionAmount", ProtocolPostEntryFields.ForeignExchange.TRANSACTION_AMOUNT);
    assertEquals("quotedRate", ProtocolPostEntryFields.ForeignExchange.QUOTED_RATE);
    assertEquals(
        "transactionCurrencyAmount",
        ProtocolPostEntryFields.QuotedRate.TRANSACTION_CURRENCY_AMOUNT);
    assertEquals("quoteSource", ProtocolPostEntryFields.QuotedRate.QUOTE_SOURCE);
    assertEquals("accountCode", ProtocolSharedRequestFields.ACCOUNT_CODE);
    assertEquals("currencyCode", ProtocolSharedRequestFields.CURRENCY_CODE);
    assertEquals("effectiveDateFrom", ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolSharedRequestFields.EFFECTIVE_DATE_TO);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolDeclareAccountFields.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM,
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_TO,
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM,
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_TO,
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO);
    assertEquals(List.of("currencyCode", "minorUnits"), ProtocolMoneyFields.fields());
    assertEquals(ProtocolSharedRequestFields.CURRENCY_CODE, ProtocolMoneyFields.CURRENCY_CODE);
    assertEquals("minorUnits", ProtocolMoneyFields.MINOR_UNITS);
    assertThrows(
        NullPointerException.class,
        () ->
            new PlanExecutionFacts(
                nullOf(), PlanFailurePolicy.HALT_ON_FIRST_FAILURE, "journal", List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new PlanExecutionFacts(PlanTransactionMode.ATOMIC, nullOf(), "journal", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PlanExecutionFacts(
                PlanTransactionMode.ATOMIC,
                PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
                " ",
                List.of()));
  }

  @Test
  void protocolRequestFieldSets_followCanonicalRequestFieldOwners() {
    assertEquals(
        Set.of(
            "accountCode",
            "accountName",
            "accountType",
            "accountNodeKind",
            "parentAccountCode",
            "financialPositionLineClassification",
            "profitAndLossLineClassification",
            "cashFlowAssetClassification"),
        ProtocolBookRequestFieldSets.declareAccountFields());
    assertEquals(
        Set.of(
            "taxRegistrationId",
            "taxRegistrationName",
            "jurisdiction",
            "registrationNumber",
            "payableAccountCode",
            "recoverableAccountCode",
            "obligationFrequency",
            "dueDaysAfterPeriodEnd",
            "taxCodes"),
        ProtocolBookRequestFieldSets.declareTaxRegistrationFields());
    assertEquals(
        Set.of("entityName", "functionalCurrency", "fiscalYearStart"),
        ProtocolBookRequestFieldSets.openBookFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.topLevelFields()),
        ProtocolPostingRequestFieldSets.postEntryTopLevelFields());
    assertEquals(
        Set.of("entryKind", "effectiveDate", "lines", "foreignExchange", "evidence", "provenance"),
        ProtocolPostingRequestFieldSets.journalDirectFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "revenueAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.saleFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "expenseAccountCode",
            "cashAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.expenseFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "equityAccountCode",
            "amount",
            "foreignExchange",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.ownerContributionFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "equityAccountCode",
            "cashAccountCode",
            "amount",
            "foreignExchange",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.ownerWithdrawalFields());
    assertEquals(
        Set.of("entryKind", "effectiveDate", "openingBalances", "evidence", "provenance"),
        ProtocolPostingRequestFieldSets.openingPositionFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "lines",
            "foreignExchange",
            "evidence",
            "provenance",
            "reversal"),
        ProtocolPostingRequestFieldSets.reversalEntryFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.evidenceFields()),
        ProtocolPostingNestedFieldSets.evidenceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.sourceDocumentFields()),
        ProtocolPostingNestedFieldSets.sourceDocumentFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.approvalFields()),
        ProtocolPostingNestedFieldSets.approvalFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.provenanceFields()),
        ProtocolPostingNestedFieldSets.provenanceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.journalLineFields()),
        ProtocolPostingNestedFieldSets.journalLineFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.openingBalanceFields()),
        ProtocolPostingNestedFieldSets.openingBalanceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.reversalFields()),
        ProtocolPostingNestedFieldSets.reversalFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.taxFields()),
        ProtocolPostingNestedFieldSets.taxFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.foreignExchangeFields()),
        ProtocolPostingNestedFieldSets.foreignExchangeFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.quotedRateFields()),
        ProtocolPostingNestedFieldSets.quotedRateFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.planFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerPlanFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.stepFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerStepFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.queryFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields());
    assertEquals(
        Set.of(ProtocolLedgerPlanFields.Query.LIMIT, ProtocolLedgerPlanFields.Query.CURSOR),
        ProtocolLedgerPlanRequestFieldSets.listAccountsQueryFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            ProtocolLedgerPlanFields.Query.LIMIT,
            ProtocolLedgerPlanFields.Query.CURSOR),
        ProtocolLedgerPlanRequestFieldSets.listPostingsQueryFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO),
        ProtocolLedgerPlanRequestFieldSets.accountBalanceQueryFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.assertionFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_DECLARED));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_ACTIVE));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND, ProtocolLedgerPlanFields.Assertion.POSTING_ID),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.POSTING_EXISTS));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
            ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
            ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
  }
}
