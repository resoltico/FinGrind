package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for newly typed protocol wire vocabularies. */
class ProtocolWireVocabularyTest {
  @Test
  void responseAndPlanEnums_publishStableWireValues() {
    assertEquals(List.of("ok", "rejected", "error"), ProtocolEnvelopeStatus.wireValues());
    assertEquals("ok", ProtocolEnvelopeStatus.OK.toString());
    assertEquals("rejected", ProtocolEnvelopeStatus.REJECTED.toString());
    assertEquals("error", ProtocolEnvelopeStatus.ERROR.toString());
    assertEquals(
        List.of("discovery", "administration", "query", "write"), OperationCategory.wireValues());
    assertEquals("discovery", OperationCategory.DISCOVERY.toString());
    assertEquals("administration", OperationCategory.ADMINISTRATION.toString());
    assertEquals("query", OperationCategory.QUERY.toString());
    assertEquals("write", OperationCategory.WRITE.toString());
    assertEquals(List.of("atomic"), PlanTransactionMode.wireValues());
    assertEquals("atomic", PlanTransactionMode.ATOMIC.toString());
    assertEquals(List.of("halt-on-first-failure"), PlanFailurePolicy.wireValues());
    assertEquals("halt-on-first-failure", PlanFailurePolicy.HALT_ON_FIRST_FAILURE.toString());
    assertEquals(List.of("summary", "full"), PlanResultDetail.wireValues());
    assertEquals("summary", PlanResultDetail.SUMMARY.toString());
    assertEquals("full", PlanResultDetail.FULL.toString());
    assertEquals(List.of("minimal", "compact", "full"), DiscoveryDetail.wireValues());
    assertEquals("minimal", DiscoveryDetail.MINIMAL.toString());
    assertEquals("compact", DiscoveryDetail.COMPACT.toString());
    assertEquals("full", DiscoveryDetail.FULL.toString());
    assertEquals(List.of("implemented", "partial", "excluded"), CapabilityStatus.wireValues());
    assertEquals("implemented", CapabilityStatus.IMPLEMENTED.toString());
    assertEquals("partial", CapabilityStatus.PARTIAL.toString());
    assertEquals("excluded", CapabilityStatus.EXCLUDED.toString());
    assertEquals(
        List.of(
            "overview",
            "commands",
            "storage",
            "request-input",
            "currency-model",
            "bookkeeping-kernel",
            "capability-catalog",
            "response-contract"),
        DiscoveryFocus.wireValues());
    assertEquals("overview", DiscoveryFocus.OVERVIEW.toString());
    assertEquals("commands", DiscoveryFocus.COMMANDS.toString());
    assertEquals(
        List.of("bundle-managed", "source-checkout-managed"), SqliteRuntimeProvenance.wireValues());
    assertEquals("bundle-managed", SqliteRuntimeProvenance.BUNDLE_MANAGED.wireValue());
    assertEquals(
        "source-checkout-managed", SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED.toString());
    assertEquals(
        List.of("bundle-sidecar-consistency", "source-checkout-sidecar-consistency"),
        SqliteRuntimeTrustBasis.wireValues());
    assertEquals(
        "bundle-sidecar-consistency",
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY.wireValue());
    assertEquals(
        "source-checkout-sidecar-consistency",
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY.toString());
    assertEquals(
        List.of(
            "macos-aarch64",
            "macos-x86_64",
            "linux-x86_64",
            "linux-aarch64",
            "windows-x86_64",
            "windows-aarch64"),
        PublicCliBundleTarget.wireValues());
  }

  @Test
  void responseAndPlanEnums_parseKnownValuesAndRejectUnknownOnes() {
    assertEquals(ProtocolEnvelopeStatus.OK, ProtocolEnvelopeStatus.fromWireValue("ok"));
    assertEquals(ProtocolEnvelopeStatus.REJECTED, ProtocolEnvelopeStatus.fromWireValue("rejected"));
    assertEquals(ProtocolEnvelopeStatus.ERROR, ProtocolEnvelopeStatus.fromWireValue("error"));
    assertEquals(OperationCategory.DISCOVERY, OperationCategory.fromWireValue("discovery"));
    assertEquals(
        OperationCategory.ADMINISTRATION, OperationCategory.fromWireValue("administration"));
    assertEquals(OperationCategory.QUERY, OperationCategory.fromWireValue("query"));
    assertEquals(OperationCategory.WRITE, OperationCategory.fromWireValue("write"));
    assertEquals(PlanTransactionMode.ATOMIC, PlanTransactionMode.fromWireValue("atomic"));
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
        PlanFailurePolicy.fromWireValue("halt-on-first-failure"));
    assertEquals(PlanResultDetail.SUMMARY, PlanResultDetail.fromWireValue("summary"));
    assertEquals(PlanResultDetail.FULL, PlanResultDetail.fromWireValue("full"));
    assertEquals(DiscoveryDetail.MINIMAL, DiscoveryDetail.fromWireValue("minimal"));
    assertEquals(DiscoveryDetail.COMPACT, DiscoveryDetail.fromWireValue("compact"));
    assertEquals(DiscoveryDetail.FULL, DiscoveryDetail.fromWireValue("full"));
    assertEquals(CapabilityStatus.PARTIAL, CapabilityStatus.fromWireValue("partial"));
    assertEquals(DiscoveryFocus.OVERVIEW, DiscoveryFocus.fromWireValue("overview"));
    assertEquals(DiscoveryFocus.COMMANDS, DiscoveryFocus.fromWireValue("commands"));
    assertEquals(
        DiscoveryFocus.CAPABILITY_CATALOG, DiscoveryFocus.fromWireValue("capability-catalog"));
    assertEquals(
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        SqliteRuntimeProvenance.fromWireValue("bundle-managed"));
    assertEquals(
        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
        SqliteRuntimeProvenance.fromWireValue("source-checkout-managed"));
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
        SqliteRuntimeTrustBasis.fromWireValue("bundle-sidecar-consistency"));
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        SqliteRuntimeTrustBasis.fromWireValue("source-checkout-sidecar-consistency"));
    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
        SqliteRuntimeTrustBasis.fromProvenance(SqliteRuntimeProvenance.BUNDLE_MANAGED));
    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        SqliteRuntimeTrustBasis.fromProvenance(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED));
    assertEquals(
        PublicCliBundleTarget.WINDOWS_AARCH64,
        PublicCliBundleTarget.fromWireValue("windows-aarch64"));

    assertThrows(
        IllegalArgumentException.class, () -> ProtocolEnvelopeStatus.fromWireValue("partial-ok"));
    assertThrows(
        IllegalArgumentException.class, () -> OperationCategory.fromWireValue("inspection"));
    assertThrows(
        IllegalArgumentException.class, () -> PlanTransactionMode.fromWireValue("best-effort"));
    assertThrows(
        IllegalArgumentException.class, () -> PlanFailurePolicy.fromWireValue("collect-all"));
    assertThrows(IllegalArgumentException.class, () -> PlanResultDetail.fromWireValue("verbose"));
    assertThrows(IllegalArgumentException.class, () -> DiscoveryDetail.fromWireValue("expanded"));
    assertThrows(IllegalArgumentException.class, () -> CapabilityStatus.fromWireValue("available"));
    assertThrows(
        IllegalArgumentException.class, () -> DiscoveryFocus.fromWireValue("documentation"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteRuntimeProvenance.fromWireValue("maybe-managed"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteRuntimeTrustBasis.fromWireValue("unsigned-trust"));
    assertThrows(
        IllegalArgumentException.class, () -> PublicCliBundleTarget.fromWireValue("plan9-x86"));
  }
}
