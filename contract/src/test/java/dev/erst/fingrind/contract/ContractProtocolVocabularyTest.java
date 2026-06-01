package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.ArtifactOutputDescriptor;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractDiscovery;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.DescriptorNamespaceSupport;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.BookProtectionMode;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
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
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for protocol vocabulary helpers and descriptor namespaces. */
class ContractProtocolVocabularyTest {
  private static final String DOCUMENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void protocolVocabularyHelpersParseWireValuesAndRejectUnknownValues() {
    assertEquals(
        LedgerStepKind.POST_ENTRY,
        LedgerStepKind.fromWireValue(LedgerStepKind.POST_ENTRY.wireValue()));
    assertEquals(
        LedgerAssertionKind.POSTING_EXISTS,
        LedgerAssertionKind.fromWireValue(LedgerAssertionKind.POSTING_EXISTS.wireValue()));
    assertEquals(OperationId.POST_ENTRY, OperationId.fromWireValue("post-entry"));
    assertEquals(
        List.of("help", "version", "capabilities"), OperationId.wireValues().subList(0, 3));
    assertEquals("post-entry", OperationId.POST_ENTRY.toString());
    assertEquals(1_179_079_236, BookFormatContract.APPLICATION_ID);
    assertEquals(22, BookFormatContract.FORMAT_VERSION);
    assertNotEquals(0, BookFormatContract.APPLICATION_ID);
    assertEquals(
        List.of(
            "assert-account-declared",
            "assert-account-active",
            "assert-posting-exists",
            "assert-account-balance"),
        LedgerAssertionKind.wireValues());
    assertEquals("open-book", LedgerStepKind.wireValues().getFirst());
    assertThrows(NullPointerException.class, () -> LedgerStepKind.fromWireValue(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> LedgerStepKind.fromWireValue("post_entry"));
    assertThrows(NullPointerException.class, () -> LedgerAssertionKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> LedgerAssertionKind.fromWireValue("assert-unknown"));
    assertThrows(IllegalArgumentException.class, () -> OperationId.fromWireValue("post_entry"));
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
        new ContractResponse.RejectionDescriptor("code", "description");
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
            EnvironmentDistributionDescriptor.class,
            EnvironmentStorageDescriptor.class,
            EnvironmentSqliteDescriptor.class,
            EnvironmentDescriptor.class),
        ContractDiscovery.descriptorTypes());
    assertEquals(
        List.of(
            ContractRequestShapes.RequestInputDescriptor.class,
            ContractRequestShapes.RequestShapesDescriptor.class,
            ContractRequestShapes.PostEntryRequestShapeDescriptor.class,
            ContractRequestShapes.DeclareAccountRequestShapeDescriptor.class,
            ContractRequestShapes.LedgerPlanRequestShapeDescriptor.class,
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
            ContractTemplates.PostingRequestTemplateDescriptor.class,
            ContractTemplates.JournalLineTemplateDescriptor.class,
            ContractTemplates.AccountingEvidenceTemplateDescriptor.class,
            ContractTemplates.SourceDocumentTemplateDescriptor.class,
            ContractTemplates.ApprovalTemplateDescriptor.class,
            ContractTemplates.ProvenanceTemplateDescriptor.class,
            ContractTemplates.ReversalTemplateDescriptor.class,
            ContractTemplates.LedgerPlanTemplateDescriptor.class,
            ContractTemplates.LedgerPlanStepTemplateDescriptor.class,
            ContractTemplates.OpenBookTemplateDescriptor.class,
            ContractTemplates.LedgerPlanQueryTemplateDescriptor.class,
            ContractTemplates.DeclareAccountTemplateDescriptor.class,
            ContractTemplates.LedgerAssertionTemplateDescriptor.class),
        ContractTemplates.descriptorTypes());
    assertEquals(
        List.of(), new ContractResponse.ErrorDescriptor("code", 4, "description").detailFields());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractResponse.ErrorDescriptor("code", -1, "description"));
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
  void templateDescriptorsRejectImpossibleShapesAndInvalidVocabularyDowngrades() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.PostingRequestTemplateDescriptor(
                BookkeepingEntryKind.CASH_REVENUE,
                "2026-04-25",
                "1000",
                "4000",
                null,
                null,
                new MonetaryAmount("EUR", "1000"),
                List.of(
                    new ContractTemplates.JournalLineTemplateDescriptor(
                        "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000"))),
                evidenceTemplate(),
                new ContractTemplates.ProvenanceTemplateDescriptor(
                    "actor-1", ActorType.PERSON, "command-1", "idem-1", "cause-1", null),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.PostingRequestTemplateDescriptor(
                BookkeepingEntryKind.REVERSAL_ADJUSTMENT,
                "2026-04-25",
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
                evidenceTemplate(),
                new ContractTemplates.ProvenanceTemplateDescriptor(
                    "actor-1", ActorType.PERSON, "command-1", "idem-1", "cause-1", null),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "open",
                LedgerStepKind.OPEN_BOOK,
                null,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    AccountRole.ORDINARY,
                    AccountNodeKind.POSTABLE,
                    null,
                    FinancialPositionLineClassification.CURRENT_ASSET,
                    null),
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerAssertionTemplateDescriptor(
                LedgerAssertionKind.POSTING_EXISTS, "1000", null, null, null, null, "posting-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractTemplates.LedgerPlanQueryTemplateDescriptor(null, null, null, 999, null));
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate() {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        List.of(
            new ContractTemplates.SourceDocumentTemplateDescriptor(
                "document-idem-1",
                "cash-receipt",
                "2026-04-25",
                "2026-04-25T10:15:30Z",
                "evidence://documents/document-idem-1.pdf",
                DOCUMENT_SHA256)),
        List.of());
  }
}
