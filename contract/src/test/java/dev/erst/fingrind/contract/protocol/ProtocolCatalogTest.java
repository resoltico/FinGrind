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
            "generate-attestation-key-file",
            "open-book",
            "rekey-book",
            "backup-book",
            "restore-book",
            "enroll-key",
            "rollover-key",
            "revoke-key",
            "alter-policy",
            "declare-account",
            "amend-account",
            "retire-account",
            "declare-tax-registration",
            "interim-result-sweep",
            "fiscal-year-close"),
        ProtocolCatalog.operationNames(OperationCategory.ADMINISTRATION));
    assertEquals(
        List.of(
            "inspect-attestation-key-file",
            "inspect-book",
            "verify-book",
            "attestation-review",
            "export-attestation-receipt",
            "verify-receipt",
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
            "inventory-valuation",
            "accrual-cutoff-schedule",
            "fixed-asset-register",
            "financing-register",
            "realized-foreign-exchange-register",
            "latvian-payroll-register",
            "income-statement",
            "cash-flow-statement",
            "changes-in-equity"),
        ProtocolCatalog.operationNames(OperationCategory.QUERY));
    assertEquals(
        List.of(
            "execute-plan",
            "preflight-entry",
            "record-sale-settled",
            "record-sale-on-credit",
            "record-purchase-settled",
            "record-purchase-on-credit",
            "record-inventory-capitalization-settled",
            "record-inventory-capitalization-on-credit",
            "record-inventory-write-down",
            "record-inventory-shrinkage",
            "record-inventory-count-increase",
            "record-prepayment",
            "record-deferred-revenue",
            "record-accrued-expense",
            "record-accrual-cutoff-recognition",
            "record-accrued-expense-settlement",
            "record-latvian-monthly-payroll",
            "record-latvian-payroll-net-wage-settlement",
            "record-latvian-payroll-state-remittance",
            "record-fixed-asset-capitalization",
            "record-fixed-asset-depreciation",
            "record-fixed-asset-disposal",
            "record-financing-borrowing",
            "record-financing-principal-repayment",
            "record-financing-interest-accrual",
            "record-financing-interest-payment",
            "record-foreign-currency-obligation",
            "record-realized-foreign-exchange-settlement",
            "record-expense-settled",
            "record-expense-on-credit",
            "record-receipt",
            "record-payment",
            "record-owner-contribution",
            "record-owner-withdrawal",
            "record-opening-position",
            "record-reversal",
            "post-entry"),
        ProtocolCatalog.operationNames(OperationCategory.WRITE));
  }

  @Test
  void payrollOperationalNoteNamesEveryCallerAuthoredWithholdingAdmissionFact() {
    String note =
        ProtocolCatalog.operation(OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL)
            .exampleSteps()
            .getLast()
            .text();

    assertTrue(note.contains("taxBookHeldAtEmployer"), note);
    assertTrue(note.contains("dependantCount"), note);
  }

  @Test
  void maintenanceOperationsPublishOneCanonicalTransactionRecoveryProcedure() {
    for (OperationId operationId :
        List.of(OperationId.REKEY_BOOK, OperationId.BACKUP_BOOK, OperationId.RESTORE_BOOK)) {
      String guidance =
          String.join(
              "\n",
              ProtocolCatalog.operation(operationId).exampleSteps().stream()
                  .map(ProtocolExampleStep::text)
                  .toList());

      assertTrue(guidance.contains("publication-transaction-incomplete"), guidance);
      assertTrue(guidance.contains("preserve its reported final candidate"), guidance);
      assertTrue(guidance.contains("exact same operation"), guidance);
      assertTrue(guidance.contains("admitted recovery inputs"), guidance);
      assertTrue(
          guidance.contains("Do not rename, overwrite, delete, recreate, or manually clean"),
          guidance);
      assertTrue(guidance.contains("either final member"), guidance);
      assertTrue(guidance.contains("do not start a fresh pair"), guidance);
      assertTrue(guidance.contains("Legacy sidecar evidence is blocked"), guidance);
    }
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
        () -> new ProtocolArtifactOutput(" ", "--pdf-out <path>", "Exports a PDF report."));
  }

  @Test
  void artifactOutputCatalog_publishesStableFormatsAndOptions() {
    assertEquals("pdf", ProtocolArtifactOutput.pdfFormat());
    assertEquals("book-file", ProtocolArtifactOutput.bookFileFormat());
    assertEquals("book-key-file", ProtocolArtifactOutput.bookKeyFileFormat());
    assertEquals("attestation-key-file", ProtocolArtifactOutput.attestationKeyFileFormat());
    assertEquals("attestation-receipt-v1", ProtocolArtifactOutput.attestationReceiptFormat());
    assertEquals("backup-file", ProtocolArtifactOutput.backupFileFormat());
    assertEquals("backup-key-file", ProtocolArtifactOutput.backupKeyFileFormat());
    assertEquals("--new-book-key-file <path>", ProtocolArtifactOutput.newBookKeyFile().option());
    assertEquals(
        "--new-attestation-key-file <path>",
        ProtocolArtifactOutput.generatedAttestationKeyFile().option());
    assertEquals("--receipt-file <path>", ProtocolArtifactOutput.attestationReceipt().option());
    assertEquals("--book-file <path>", ProtocolArtifactOutput.bookFile().option());
    assertEquals(
        "--new-backup-key-file <path>", ProtocolArtifactOutput.newBackupKeyFile().option());
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
    assertEquals("[--limit <1-200>]", ProtocolOptionSyntax.ReportQuery.optionalLimitSyntax());
    assertEquals("[--cursor <cursor>]", ProtocolOptionSyntax.ReportQuery.optionalCursorSyntax());
    assertEquals(
        "[--output <json|text|csv>]",
        ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
            List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV)));
    assertEquals("[--pdf-out <path>]", ProtocolOptionSyntax.Presentation.optionalPdfOutSyntax());
    assertEquals(
        "[--detail <minimal|compact|full>]",
        ProtocolOptionSyntax.Discovery.optionalDiscoveryDetailSyntax());
    assertEquals(
        "[--detail <minimal|compact|full> (json only)]",
        ProtocolOptionSyntax.Discovery.optionalJsonOnlyDiscoveryDetailSyntax());
    assertEquals(
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        ProtocolOptionSyntax.BookAccess.bookPassphraseOptions());
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
  void operationDefinitionLeavesOrdinaryUsageOptionsUnbracketed() {
    ProtocolOperation withOption =
        ProtocolOperationDefinitions.operation(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            "Help",
            List.of(),
            List.of("--example <value>"),
            ExecutionMode.JSON_ENVELOPE,
            "Test-only descriptor.",
            List.of());
    ProtocolOperation withoutOptions =
        ProtocolOperationDefinitions.operation(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            "Help",
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            "Test-only descriptor.",
            List.of());

    assertEquals("fingrind help --example <value>", withOption.usage());
    assertEquals("fingrind help", withoutOptions.usage());
  }

  @Test
  void operations_keepSelectableOutputAndArtifactOptionsInCanonicalOptionLists() {
    for (ProtocolOperation operation : ProtocolCatalog.operations()) {
      if (!operation.outputModes().isEmpty()) {
        assertTrue(
            operation
                .options()
                .contains(
                    ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                        operation.outputModes())),
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
}
