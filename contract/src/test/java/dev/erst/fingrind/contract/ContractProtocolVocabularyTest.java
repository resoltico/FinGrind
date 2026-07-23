package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.ArtifactOutputDescriptor;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates;
import dev.erst.fingrind.contract.discovery.ContractAttestationReviewTemplates;
import dev.erst.fingrind.contract.discovery.ContractDiscovery;
import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.DescriptorNamespaceSupport;
import dev.erst.fingrind.contract.discovery.ForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.InventoryReliefTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.QuotedExchangeRateTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.discovery.TemplateDescriptorType;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.BookProtectionMode;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.PublicCliDistribution;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.protocol.SqliteLibraryMode;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.protocol.StorageDriver;
import dev.erst.fingrind.contract.protocol.StorageEngine;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Unit tests for protocol vocabulary helpers and descriptor namespaces. */
class ContractProtocolVocabularyTest {
  private static final ApplicationIdentity IDENTITY =
      new ApplicationIdentity("FinGrind", "0.57.0", "Protected bookkeeping kernel");
  private static final Pattern ORNAMENTAL_ONE_PATTERN = Pattern.compile("\\bone\\b");
  private static final List<String> ALLOWED_ONE_PHRASES =
      List.of(
          "exactly one active EQUITY account classified as RESULT_HOLDING",
          "exactly one active and postable EQUITY account classified as RESULT_HOLDING",
          "exactly one active EQUITY account for each required close target");
  private static final List<String> FORBIDDEN_COUNTRY_SPECIFIC_TAX_EXAMPLE_TOKENS =
      List.of("vat-lv", "Latvia VAT", "LV40001234567");

  @Test
  void protocolVocabularyHelpersParseWireValuesAndRejectUnknownValues() {
    assertEquals(
        LedgerStepKind.POST_ENTRY,
        LedgerStepKind.fromWireValue(LedgerStepKind.POST_ENTRY.wireValue()));
    assertEquals(
        LedgerAssertionKind.POSTING_EXISTS,
        LedgerAssertionKind.fromWireValue(LedgerAssertionKind.POSTING_EXISTS.wireValue()));
    assertEquals(
        OperationId.POST_ENTRY,
        WireValue.fromWireValue(OperationId.class, "post-entry", "Unsupported operation id"));
    assertEquals(
        List.of("help", "version", "capabilities"),
        WireValue.wireValues(OperationId.class).subList(0, 3));
    assertEquals("post-entry", OperationId.POST_ENTRY.toString());
    assertEquals(
        List.of(
            "DIRECT_JOURNAL",
            "SALE_SETTLED",
            "SALE_ON_CREDIT",
            "PURCHASE_SETTLED",
            "PURCHASE_ON_CREDIT",
            "INVENTORY_CAPITALIZATION_SETTLED",
            "INVENTORY_CAPITALIZATION_ON_CREDIT",
            "INVENTORY_WRITE_DOWN",
            "INVENTORY_SHRINKAGE",
            "INVENTORY_COUNT_INCREASE",
            "PREPAYMENT",
            "DEFERRED_REVENUE",
            "ACCRUED_EXPENSE",
            "ACCRUAL_CUTOFF_RECOGNITION",
            "ACCRUED_EXPENSE_SETTLEMENT",
            "LATVIAN_MONTHLY_PAYROLL",
            "LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT",
            "LATVIAN_PAYROLL_STATE_REMITTANCE",
            "FIXED_ASSET_CAPITALIZATION",
            "FIXED_ASSET_DEPRECIATION",
            "FIXED_ASSET_DISPOSAL",
            "FINANCING_BORROWING",
            "FINANCING_PRINCIPAL_REPAYMENT",
            "FINANCING_INTEREST_ACCRUAL",
            "FINANCING_INTEREST_PAYMENT",
            "FOREIGN_CURRENCY_OBLIGATION",
            "REALIZED_FOREIGN_EXCHANGE_SETTLEMENT",
            "EXPENSE_SETTLED",
            "EXPENSE_ON_CREDIT",
            "RECEIPT",
            "PAYMENT",
            "OWNER_CONTRIBUTION",
            "OWNER_WITHDRAWAL",
            "OPENING_POSITION",
            "REVERSAL"),
        BookkeepingEntryKind.wireValues());
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED, BookkeepingEntryKind.fromWireValue("SALE_SETTLED"));
    assertEquals(1_179_079_236, BookFormatContract.APPLICATION_ID);
    assertEquals(52, BookFormatContract.FORMAT_VERSION);
    assertNotEquals(0, BookFormatContract.APPLICATION_ID);
    assertEquals(
        List.of(
            "assert-account-declared",
            "assert-account-active",
            "assert-posting-exists",
            "assert-account-balance"),
        LedgerAssertionKind.wireValues());
    assertEquals(List.of("text", "flag", "count", "money", "group"), LedgerFactKind.wireValues());
    assertEquals("declare-account", LedgerStepKind.wireValues().getFirst());
    assertFalse(LedgerStepKind.wireValues().contains("ensure-book"));
    assertThrows(NullPointerException.class, () -> LedgerStepKind.fromWireValue(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> LedgerStepKind.fromWireValue("post_entry"));
    assertThrows(NullPointerException.class, () -> LedgerAssertionKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> LedgerAssertionKind.fromWireValue("assert-unknown"));
    assertEquals(LedgerFactKind.GROUP, LedgerFactKind.fromWireValue("group"));
    assertThrows(NullPointerException.class, () -> LedgerFactKind.fromWireValue(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> LedgerFactKind.fromWireValue("decimal"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WireValue.fromWireValue(OperationId.class, "post_entry", "Unsupported operation id"));
    assertThrows(IllegalArgumentException.class, () -> BookkeepingEntryKind.fromWireValue("sale"));
  }

  @Test
  void runtimeSurfaceVocabulariesParseStableWireValues() {
    assertEquals(
        RequestFieldPresence.CONDITIONAL, RequestFieldPresence.fromWireValue("conditional"));
    assertEquals("conditional", RequestFieldPresence.CONDITIONAL.toString());
    assertEquals(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE,
        RuntimeDistribution.fromWireValue("source-checkout-gradle"));
    assertEquals("source-checkout-gradle", RuntimeDistribution.SOURCE_CHECKOUT_GRADLE.toString());
    assertEquals(
        PublicCliDistribution.SELF_CONTAINED_BUNDLE,
        PublicCliDistribution.fromWireValue("self-contained-bundle"));
    assertEquals("self-contained-bundle", PublicCliDistribution.SELF_CONTAINED_BUNDLE.toString());
    assertEquals(
        StorageDriver.SQLITE_FFM_SQLITE3MC, StorageDriver.fromWireValue("sqlite-ffm-sqlite3mc"));
    assertEquals("sqlite-ffm-sqlite3mc", StorageDriver.SQLITE_FFM_SQLITE3MC.toString());
    assertEquals(StorageEngine.SQLITE, StorageEngine.fromWireValue("sqlite"));
    assertEquals("sqlite", StorageEngine.SQLITE.toString());
    assertEquals(BookProtectionMode.REQUIRED, BookProtectionMode.fromWireValue("required"));
    assertEquals("required", BookProtectionMode.REQUIRED.toString());
    assertEquals(BookCipher.CHACHA20, BookCipher.fromWireValue("chacha20"));
    assertEquals("chacha20", BookCipher.CHACHA20.toString());
    assertEquals(SqliteLibraryMode.MANAGED_ONLY, SqliteLibraryMode.fromWireValue("managed-only"));
    assertEquals("managed-only", SqliteLibraryMode.MANAGED_ONLY.toString());
    assertEquals(
        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
        SqliteRuntimeProvenance.fromWireValue("source-checkout-managed"));
    assertEquals(
        "source-checkout-managed", SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED.toString());
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
        SqliteRuntimeTrustBasis.fromWireValue("bundle-sidecar-consistency"));
    assertEquals(SqliteRuntimeStatus.READY, SqliteRuntimeStatus.fromWireValue("ready"));
    assertEquals("ready", SqliteRuntimeStatus.READY.toString());
    assertEquals(SqliteRuntimeStatus.FAILED, SqliteRuntimeStatus.fromWireValue("failed"));
    assertEquals("failed", SqliteRuntimeStatus.FAILED.toString());
    assertEquals(
        List.of("required", "conditional", "optional", "forbidden"),
        RequestFieldPresence.wireValues());
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeDistribution.fromWireValue("source-checkout"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteRuntimeProvenance.fromWireValue("managed-source-checkout"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteRuntimeTrustBasis.fromWireValue("operator-trust"));
    assertThrows(IllegalArgumentException.class, () -> SqliteRuntimeStatus.fromWireValue("loaded"));
  }

  @Test
  void descriptorNamespacesPublishTheirRecordInventories() {
    ContractResponse.RejectionDescriptor leafRejection =
        new ContractResponse.RejectionDescriptor(
            "code", ContractResponse.FailureCategory.DOMAIN_SEMANTIC, "description");
    assertEquals(
        List.of(
            ApplicationIdentity.class,
            HelpDescriptor.class,
            CapabilitiesDescriptor.class,
            StorageSurfaceDescriptor.class,
            CommandCatalogDescriptor.class,
            VersionDescriptor.class,
            ArtifactOutputDescriptor.class,
            CommandDescriptor.class,
            SelectableOutputDefaultsDescriptor.class,
            ExitCodeDescriptor.class,
            EnvironmentRuntimeDescriptor.class,
            EnvironmentPublicationDescriptor.class,
            EnvironmentStorageDescriptor.class,
            EnvironmentSqliteDescriptor.class,
            EnvironmentDescriptor.class),
        ContractDiscovery.descriptorTypes());
    assertEquals(
        List.of(
            ContractRequestShapes.RequestInputDescriptor.class,
            ContractRequestShapes.RequestShapesDescriptor.class,
            ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor.class,
            ContractRequestShapes.DeclareAccountRequestShapeDescriptor.class,
            ContractRequestShapes.RetireAccountRequestShapeDescriptor.class,
            ContractRequestShapes.DeclareTaxRegistrationRequestShapeDescriptor.class,
            ContractRequestShapes.LedgerPlanRequestShapeDescriptor.class,
            ContractRequestShapes.EntryKindSemanticsDescriptor.class,
            ContractRequestShapes.ReachabilityCellDescriptor.class,
            ContractRequestShapes.EvidenceRequirementDescriptor.class,
            ContractRequestShapes.RequestFieldDescriptor.class,
            ContractRequestShapes.EnumVocabularyDescriptor.class),
        ContractRequestShapes.descriptorTypes());
    assertEquals(
        List.of(
            ContractResponse.BookModelDescriptor.class,
            ContractResponse.BookkeepingKernelDescriptor.class,
            ContractResponse.FieldDescriptor.class,
            ContractResponse.ErrorDescriptor.class,
            ContractResponse.ResponseModelDescriptor.class,
            ContractResponse.PlanExecutionDescriptor.class,
            ContractResponse.RejectionDescriptor.class,
            ContractResponse.AuditDescriptor.class,
            ContractResponse.AccountRegistryDescriptor.class,
            ContractResponse.ReversalDescriptor.class,
            ContractResponse.PreflightDescriptor.class,
            ContractResponse.CurrencyDescriptor.class),
        ContractResponse.descriptorTypes());
    assertEquals(
        List.of(
            ContractPostingRequestTemplates.PostingRequestTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.RolloverKeyTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.RevokeKeyTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.AlterPolicyTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.PolicyRuleTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.CapabilityGrantTemplateDescriptor.class,
            ContractAttestationRegistryTemplates.SystemWorkflowPolicyTemplateDescriptor.class,
            ContractAttestationReviewTemplates.AttestationReviewFileTemplateDescriptor.class,
            ContractAttestationReviewTemplates.CompromiseReviewTemplateDescriptor.class,
            ContractSettlementTemplates.TaxSelectionTemplateDescriptor.class,
            ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor.class,
            InventoryReliefTemplateDescriptor.class,
            ForeignExchangeTemplateDescriptor.class,
            QuotedExchangeRateTemplateDescriptor.class,
            ContractTemplates.JournalLineTemplateDescriptor.class,
            ContractTemplates.OpeningBalanceTemplateDescriptor.class,
            ContractTemplates.AccountingEvidenceTemplateDescriptor.class,
            ContractTemplates.SourceDocumentTemplateDescriptor.class,
            ContractTemplates.ApprovalTemplateDescriptor.class,
            ContractTemplates.ProvenanceTemplateDescriptor.class,
            ContractReversalTemplates.ReversalTemplateDescriptor.class,
            ContractTemplates.DeclareTaxRegistrationTemplateDescriptor.class,
            ContractTemplates.DeclareTaxCodeTemplateDescriptor.class,
            ContractPlanTemplates.LedgerPlanTemplateDescriptor.class,
            ContractPlanTemplates.LedgerPlanStepTemplateDescriptor.class,
            ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor.class,
            ContractTemplates.DeclareAccountTemplateDescriptor.class,
            ContractTemplates.RetireAccountTemplateDescriptor.class,
            ContractPlanTemplates.LedgerAssertionTemplateDescriptor.class,
            ContractFixedAssetTemplates.FixedAssetTemplateDescriptor.class,
            ContractFixedAssetTemplates.FixedAssetDepreciationScheduleTemplateDescriptor.class,
            ContractFinancingTemplates.FinancingTemplateDescriptor.class,
            ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor
                .class),
        TemplateDescriptorType.descriptorTypes());
    assertEquals(
        List.of(),
        new ContractResponse.ErrorDescriptor(
                "code", ContractResponse.FailureCategory.PRECONDITION, 4, "description")
            .detailFields());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractResponse.ErrorDescriptor(
                "code", ContractResponse.FailureCategory.PRECONDITION, -1, "description"));
    assertEquals(List.of(), leafRejection.detailFields());
    assertEquals(List.of(), leafRejection.detailRejections());
  }

  @Test
  void descriptorNamespaceSupport_rejectsNonSealedRoots() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> DescriptorNamespaceSupport.descriptorTypes(String.class));
    assertEquals(
        "Descriptor namespace root must be sealed: java.lang.String", exception.getMessage());
  }

  @Test
  void requestInputDescriptor_requiresPositiveByteLimits() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractRequestShapes.RequestInputDescriptor(
                "--book",
                List.of("--passphrase-stdin"),
                "--request-file",
                List.of("execute-plan"),
                List.of("open-book"),
                "--output",
                List.of("plain"),
                "-",
                "book-file semantics",
                0,
                List.of("book-passphrase semantics"),
                1024,
                List.of("request-document semantics")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractRequestShapes.RequestInputDescriptor(
                "--book",
                List.of("--passphrase-stdin"),
                "--request-file",
                List.of("execute-plan"),
                List.of("open-book"),
                "--output",
                List.of("plain"),
                "-",
                "book-file semantics",
                4096,
                List.of("book-passphrase semantics"),
                0,
                List.of("request-document semantics")));
  }

  @Test
  void templateDescriptorsRejectImpossibleShapesAndInvalidVocabularyDowngrades() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
                BookkeepingEntryKind.SALE_SETTLED,
                "2026-04-25",
                "1000",
                null,
                null,
                "4000",
                null,
                null,
                null,
                null,
                null,
                null,
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    new ContractTemplates.JournalLineTemplateDescriptor(
                        "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000"))),
                null,
                evidenceTemplate(),
                new ContractTemplates.ProvenanceTemplateDescriptor(
                    "68b235c4-3e83-35cb-b580-361467f844e5", "idem-1", "cause-1", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
                BookkeepingEntryKind.REVERSAL,
                "2026-04-25",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    new ContractTemplates.JournalLineTemplateDescriptor(
                        "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
                    new ContractTemplates.JournalLineTemplateDescriptor(
                        "2000", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000"))),
                null,
                evidenceTemplate(),
                new ContractTemplates.ProvenanceTemplateDescriptor(
                    "68b235c4-3e83-35cb-b580-361467f844e5", "idem-1", "cause-1", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
                LedgerAssertionKind.POSTING_EXISTS, "1000", null, null, null, null, "posting-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
                null, null, null, 999, null));
  }

  @Test
  void publicOperationDescriptionsAndNotes_avoidOrnamentalOneLanguage() {
    for (var operation : ProtocolCatalog.operations()) {
      assertNoOrnamentalOne(operation.analysisSummary(), operation.id() + " description");
      for (ProtocolExampleStep exampleStep : operation.exampleSteps()) {
        if (exampleStep instanceof ProtocolExampleStep.Note note) {
          assertNoOrnamentalOne(note.text(), operation.id() + " note");
        }
      }
    }
  }

  @Test
  void publicOperationExamples_avoidCountrySpecificTaxScaffoldLiterals() {
    for (var operation : ProtocolCatalog.operations()) {
      for (ProtocolExampleStep exampleStep : operation.exampleSteps()) {
        if (exampleStep instanceof ProtocolExampleStep.Command command) {
          assertNoCountrySpecificTaxLiteral(command.text(), operation.id() + " command");
        }
        if (exampleStep instanceof ProtocolExampleStep.Note note) {
          assertNoCountrySpecificTaxLiteral(note.text(), operation.id() + " note");
        }
      }
    }
  }

  @Test
  void publicArtifactOutputDescriptions_avoidOrnamentalOneLanguage() {
    List.of(
            ProtocolArtifactOutput.pdf(),
            ProtocolArtifactOutput.bookFile(),
            ProtocolArtifactOutput.generatedBookKeyFile(),
            ProtocolArtifactOutput.newBookKeyFile(),
            ProtocolArtifactOutput.backupFile(),
            ProtocolArtifactOutput.newBackupKeyFile(),
            ProtocolArtifactOutput.rollbackBookFile(),
            ProtocolArtifactOutput.discoveredRollbackBookFile())
        .forEach(
            artifactOutput ->
                assertNoOrnamentalOne(
                    artifactOutput.description(),
                    artifactOutput.format() + " artifact description"));
  }

  @Test
  void publicDiscoveryNarrativeSurfaces_avoidOrnamentalOneLanguage() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);

    assertNoOrnamentalOne(
        capabilities.bookkeepingKernel().description(), "bookkeeping-kernel description");
    capabilities
        .bookkeepingKernel()
        .reportCapabilities()
        .forEach(
            reportCapability ->
                assertNoOrnamentalOne(
                    reportCapability.description(),
                    reportCapability.statementId() + " capability description"));

    WorkflowStepDescriptor.Note introNote =
        org.junit.jupiter.api.Assertions.assertInstanceOf(
            WorkflowStepDescriptor.Note.class,
            MachineContract.quickStart(WorkflowSurface.CONTAINER_DOCKER).steps().getFirst());
    assertNoOrnamentalOne(introNote.text(), "container quick-start intro note");
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate() {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        List.of(
            new ContractTemplates.SourceDocumentTemplateDescriptor(
                "document-idem-1", "cash-receipt", "2026-04-25")),
        List.of());
  }

  private static void assertNoOrnamentalOne(String text, String label) {
    if (!ORNAMENTAL_ONE_PATTERN.matcher(text).find()) {
      return;
    }
    org.junit.jupiter.api.Assertions.assertTrue(
        ALLOWED_ONE_PHRASES.stream().anyMatch(text::contains),
        () -> "Unexpected ornamental 'one' in " + label + ": " + text);
  }

  private static void assertNoCountrySpecificTaxLiteral(String text, String label) {
    org.junit.jupiter.api.Assertions.assertTrue(
        FORBIDDEN_COUNTRY_SPECIFIC_TAX_EXAMPLE_TOKENS.stream().noneMatch(text::contains),
        () -> "Unexpected country-specific tax scaffold literal in " + label + ": " + text);
  }
}
