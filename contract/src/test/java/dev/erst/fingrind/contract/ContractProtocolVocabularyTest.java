package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for protocol vocabulary helpers and descriptor namespaces. */
@NullUnmarked
class ContractProtocolVocabularyTest {
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
    assertEquals(1, BookFormatContract.FORMAT_VERSION);
    assertNotEquals(0, BookFormatContract.APPLICATION_ID);
    assertEquals(
        List.of(
            "assert-account-declared",
            "assert-account-active",
            "assert-posting-exists",
            "assert-account-balance"),
        LedgerAssertionKind.wireValues());
    assertEquals("open-book", LedgerStepKind.wireValues().getFirst());

    assertThrows(NullPointerException.class, () -> LedgerStepKind.fromWireValue(null));
    assertThrows(IllegalArgumentException.class, () -> LedgerStepKind.fromWireValue("post_entry"));
    assertThrows(NullPointerException.class, () -> LedgerAssertionKind.fromWireValue(null));
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
        SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
        SqliteRuntimeTrustBasis.fromWireValue("publisher-authenticated"));
    assertEquals("operator-trusted", SqliteRuntimeTrustBasis.OPERATOR_TRUSTED.toString());
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
            ContractTemplates.ProvenanceTemplateDescriptor.class,
            ContractTemplates.ReversalTemplateDescriptor.class,
            ContractTemplates.LedgerPlanTemplateDescriptor.class,
            ContractTemplates.LedgerPlanStepTemplateDescriptor.class,
            ContractTemplates.LedgerPlanQueryTemplateDescriptor.class,
            ContractTemplates.DeclareAccountTemplateDescriptor.class,
            ContractTemplates.LedgerAssertionTemplateDescriptor.class),
        ContractTemplates.descriptorTypes());
    assertEquals(
        List.of(), new ContractResponse.ErrorDescriptor("code", "description").detailFields());
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
                "2026-04-25",
                List.of(
                    new ContractTemplates.JournalLineTemplateDescriptor(
                        "1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00")),
                new ContractTemplates.ProvenanceTemplateDescriptor(
                    "actor-1", ActorType.HUMAN, "command-1", "idem-1", "cause-1", null),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "open",
                LedgerStepKind.OPEN_BOOK,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "1000", "Cash", NormalBalance.DEBIT),
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerAssertionTemplateDescriptor(
                LedgerAssertionKind.POSTING_EXISTS,
                "1000",
                null,
                null,
                null,
                null,
                null,
                "posting-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractTemplates.LedgerPlanQueryTemplateDescriptor(null, null, null, 999, null));
  }
}
