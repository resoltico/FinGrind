package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.InteractionLimits;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        IllegalStateException.class, () -> ProtocolCatalog.indexById(List.of(help, duplicateHelp)));
    assertThrows(
        IllegalStateException.class, () -> ProtocolCatalog.indexByToken(List.of(help, version)));
  }

  @Test
  void requireOperation_reportsMissingCanonicalRegistration() {
    IllegalStateException missingOperation =
        assertThrows(
            IllegalStateException.class,
            () ->
                ProtocolCatalog.requireOperation(
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
            "close-period"),
        ProtocolCatalog.operationNames(OperationCategory.ADMINISTRATION));
    assertEquals(
        List.of(
            "inspect-book",
            "list-accounts",
            "get-posting",
            "list-postings",
            "account-balance",
            "trial-balance",
            "account-ledger",
            "period-summary",
            "financial-position",
            "income-statement",
            "changes-in-equity"),
        ProtocolCatalog.operationNames(OperationCategory.QUERY));
    assertEquals(
        List.of("execute-plan", "preflight-entry", "post-entry"),
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
  void bookkeepingKernelFacts_requireBuiltInStatementsToMatchImplementedCapabilities() {
    BookkeepingKernelFacts kernel = ProtocolCatalog.bookkeepingKernel();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookkeepingKernelFacts(
                kernel.scope(),
                List.of("statement-of-cash-flows"),
                kernel.reportCapabilities(),
                kernel.policyProfile(),
                kernel.description()));
  }

  @Test
  void bookkeepingKernelFacts_publishCurrentExecutableKernelInventory() {
    BookkeepingKernelFacts kernel = ProtocolCatalog.bookkeepingKernel();
    assertEquals("cash-single-entity-internal-management-kernel", kernel.scope());
    assertEquals(
        List.of("financial-position", "income-statement", "changes-in-equity"),
        kernel.builtInStatements());
    assertEquals(
        List.of("financial-position", "income-statement", "changes-in-equity"),
        kernel.reportCapabilities().stream().map(ReportCapabilityFacts::statementId).toList());
    assertTrue(
        kernel.reportCapabilities().stream()
            .allMatch(reportCapability -> reportCapability.comparativeSupported()));
    assertEquals("internal-management-single-entity-v1", kernel.policyProfile().profileId());
    assertTrue(kernel.policyProfile().description().contains("cash-oriented"));
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
        "[--output <json|human|csv>]",
        ProtocolOptions.optionalOutputSyntax(
            List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV)));
    assertEquals("[--pdf-out <path>]", ProtocolOptions.optionalPdfOutSyntax());
    assertEquals("[--detail <compact|full>]", ProtocolOptions.optionalDiscoveryDetailSyntax());
    assertEquals(
        "[--detail <compact|full> (json only)]",
        ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        ProtocolOptions.bookPassphraseOptions());
    assertEquals(50, InteractionLimits.DEFAULT_PAGE_LIMIT);
    assertEquals(200, InteractionLimits.PAGE_LIMIT_MAX);
    assertEquals(100, InteractionLimits.LEDGER_PLAN_STEP_MAX);
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
        List.of(OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV), trialBalance.outputModes());
    assertTrue(trialBalance.options().contains("[--output <json|human|csv>]"));
    assertTrue(trialBalance.options().contains("[--pdf-out <path>]"));
    assertEquals(1, trialBalance.artifactOutputs().size());
    assertEquals("pdf", trialBalance.artifactOutputs().getFirst().format());
    assertEquals("--pdf-out <path>", trialBalance.artifactOutputs().getFirst().option());
    assertEquals(List.of(), printRequestTemplate.outputModes());
    assertEquals(List.of(), executePlan.outputModes());
    assertFalse(printRequestTemplate.options().contains("[--output <json>]"));
    assertFalse(executePlan.options().contains("[--output <json>]"));
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
        assertTrue(
            operation.options().contains(ProtocolOptions.optionalPdfOutSyntax()),
            () ->
                "Missing canonical --pdf-out syntax for "
                    + operation.id().wireName()
                    + ": "
                    + operation.options());
      }
    }
  }

  @Test
  void globalFacts_publishTheCurrentBookModelAndRuntimeContract() {
    assertEquals(List.of(StorageEngine.SQLITE), ProtocolCatalog.storageEngines());
    assertEquals(
        RuntimeDistribution.DIRECT_JAVA_INVOCATION,
        ProtocolCatalog.directJavaRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE,
        ProtocolCatalog.sourceCheckoutRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.CONTAINER_IMAGE, ProtocolCatalog.containerRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE, ProtocolCatalog.bundleRuntimeDistribution());
    assertEquals(
        PublicCliDistribution.SELF_CONTAINED_BUNDLE, ProtocolCatalog.publicCliDistribution());
    assertEquals(StorageDriver.SQLITE_FFM_SQLITE3MC, ProtocolCatalog.storageDriver());
    assertEquals(StorageEngine.SQLITE, ProtocolCatalog.storageEngine());
    assertEquals(BookProtectionMode.REQUIRED, ProtocolCatalog.bookProtectionMode());
    assertEquals(BookCipher.CHACHA20, ProtocolCatalog.protectedBookFormat().cipher());
    assertFalse(ProtocolCatalog.protectedBookFormat().legacyMode());
    assertEquals(4096, ProtocolCatalog.protectedBookFormat().pageSize());
    assertEquals(32, ProtocolCatalog.protectedBookFormat().reservedBytes());
    assertEquals(4096, ProtocolCatalog.protectedBookFormat().legacyPageSize());
    assertEquals(64007, ProtocolCatalog.protectedBookFormat().kdfIter());
    assertEquals(0, ProtocolCatalog.protectedBookFormat().plaintextHeaderSize());
    assertEquals(BookCipher.CHACHA20, ProtocolCatalog.defaultBookCipher());
    assertEquals(SqliteLibraryMode.MANAGED_ONLY, ProtocolCatalog.sqliteLibraryMode());
    assertEquals("fingrind.bundle.home", ProtocolCatalog.sqliteBundleHomeSystemProperty());
    assertEquals("3.53.1", ProtocolCatalog.requiredMinimumSqliteVersion());
    assertEquals("2.3.4", ProtocolCatalog.requiredSqlite3mcVersion());
    assertEquals(
        "2026-05-05 10:34:17 c88b22011a54b4f6fbd149e9f8e4de77658ce58143a1af0e3785e4e6475127e9",
        ProtocolCatalog.requiredSqliteSourceId());
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        ProtocolCatalog.requiredSqliteCompileOptions());
    assertEquals(List.of(ProtocolSuccessStatus.OK), ProtocolCatalog.successStatuses());
    assertEquals(List.of(ProtocolRejectionStatus.REJECTED), ProtocolCatalog.rejectionStatuses());
    assertEquals(
        "single-functional-currency-per-book", ProtocolCatalog.bookModel().currencyScope());
    assertEquals("not-supported", ProtocolCatalog.currency().multiCurrencyStatus());
    assertEquals("advisory", ProtocolCatalog.preflight().semantics());
    assertFalse(ProtocolCatalog.preflight().commitGuarantee());
    assertEquals(PlanTransactionMode.ATOMIC, ProtocolCatalog.planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE, ProtocolCatalog.planExecution().failurePolicy());
    assertTrue(ProtocolCatalog.planExecution().journal().contains("per-step journal"));
    assertTrue(
        ProtocolCatalog.planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("open-book")));
    assertTrue(
        ProtocolCatalog.planExecution().hardLimitations().stream()
            .anyMatch(limitation -> limitation.contains("100 steps")));
    assertEquals(
        PublicDistributionContracts.current().supportedPublicCliBundleTargets(),
        ProtocolCatalog.supportedPublicCliBundleTargets());
    assertEquals(
        PublicDistributionContracts.current().unsupportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliBundleTargets());
    assertEquals(
        BundleLayoutContracts.current().bundleTargets().keySet(),
        java.util.stream.Stream.concat(
                ProtocolCatalog.supportedPublicCliBundleTargets().stream(),
                ProtocolCatalog.unsupportedPublicCliBundleTargets().stream())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    assertEquals(
        "./bin/fingrind",
        ProtocolCatalog.bundleLauncherCommand(PublicCliBundleTarget.MACOS_AARCH64));
    assertEquals(
        "bin/fingrind.ps1",
        ProtocolCatalog.bundleLauncherPath(PublicCliBundleTarget.WINDOWS_X86_64));
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
            "openBook",
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
    assertEquals("openBook", ProtocolLedgerPlanFields.Step.OPEN_BOOK);
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
            "lines",
            "evidence",
            "provenance",
            "reversal"),
        ProtocolPostEntryFields.topLevelFields());
    assertEquals(
        List.of("accountCode", "side", "amount"), ProtocolPostEntryFields.journalLineFields());
    assertEquals(List.of("sourceDocuments", "approvals"), ProtocolPostEntryFields.evidenceFields());
    assertEquals(
        List.of(
            "sourceDocumentId",
            "sourceDocumentType",
            "documentDate",
            "capturedAt",
            "storageLocator",
            "contentSha256"),
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
  void publicDistributionContractLoaderValidatesResourceAndNormalizationEdges() {
    PublicDistributionContract loaded =
        PublicDistributionContracts.loadFromResource(
            new ByteArrayInputStream(
                """
                {
                  "supportedPublicCliBundleTargets": [
                    "macos-aarch64",
                    "linux-x86_64"
                  ],
                  "unsupportedPublicCliBundleTargets": [
                    "windows-aarch64"
                  ]
                }
                """
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "test-resource");
    assertEquals(
        List.of(PublicCliBundleTarget.MACOS_AARCH64, PublicCliBundleTarget.LINUX_X86_64),
        loaded.supportedPublicCliBundleTargets());
    assertEquals(
        List.of(PublicCliBundleTarget.WINDOWS_AARCH64), loaded.unsupportedPublicCliBundleTargets());
    assertEquals(
        "supportedPublicCliBundleTargets must be a JSON array of strings.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    PublicDistributionContracts.loadFromResource(
                        new ByteArrayInputStream(
                            "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "blank-resource"))
            .getMessage());
    assertThrows(
        IllegalStateException.class,
        () -> PublicDistributionContracts.loadFromResource(nullOf(), "missing-resource"));
    assertThrows(
        UncheckedIOException.class,
        () -> PublicDistributionContracts.loadFromResource(failingInputStream(), "bad-resource"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PublicDistributionContracts.loadFromResource(
                new ByteArrayInputStream(
                    """
                    {"supportedPublicCliBundleTargets":[1]}
                    """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "invalid-resource"));
    assertEquals(
        "supportedPublicCliBundleTargets must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> PublicDistributionContract.fromWireValues(nullOf(), List.of()))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicDistributionContract.fromWireValues(List.of("macos-aarch64", " "), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PublicDistributionContract.fromWireValues(
                List.of("linux-x86_64", "linux-x86_64"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PublicDistributionContract.fromWireValues(
                List.of("linux-x86_64"), List.of("linux-x86_64", "windows-aarch64")));
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
