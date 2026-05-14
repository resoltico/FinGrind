package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for newly typed protocol wire vocabularies. */
class ProtocolWireVocabularyTest {
  @Test
  void responseAndPlanEnums_publishStableWireValues() {
    assertEquals(List.of("ok"), ProtocolSuccessStatus.wireValues());
    assertEquals("ok", ProtocolSuccessStatus.OK.toString());
    assertEquals(List.of("rejected"), ProtocolRejectionStatus.wireValues());
    assertEquals("rejected", ProtocolRejectionStatus.REJECTED.toString());
    assertEquals(List.of("error"), ProtocolFailureStatus.wireValues());
    assertEquals("error", ProtocolFailureStatus.ERROR.toString());
    assertEquals(
        List.of("pdf-exported", "pdf-export-warning"), ProtocolDiagnosticCode.wireValues());
    assertEquals("pdf-export-warning", ProtocolDiagnosticCode.PDF_EXPORT_WARNING.toString());
    assertEquals(List.of("atomic"), PlanTransactionMode.wireValues());
    assertEquals("atomic", PlanTransactionMode.ATOMIC.toString());
    assertEquals(List.of("halt-on-first-failure"), PlanFailurePolicy.wireValues());
    assertEquals("halt-on-first-failure", PlanFailurePolicy.HALT_ON_FIRST_FAILURE.toString());
    assertEquals(List.of("summary", "full"), PlanResultDetail.wireValues());
    assertEquals("summary", PlanResultDetail.SUMMARY.toString());
    assertEquals("full", PlanResultDetail.FULL.toString());
    assertEquals(
        List.of("bundle-managed", "source-checkout-managed", "environment-configured"),
        SqliteRuntimeProvenance.wireValues());
    assertEquals("bundle-managed", SqliteRuntimeProvenance.BUNDLE_MANAGED.wireValue());
    assertEquals(
        "source-checkout-managed", SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED.toString());
    assertEquals(
        "environment-configured", SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED.toString());
    assertEquals(
        List.of("publisher-authenticated", "operator-trusted"),
        SqliteRuntimeTrustBasis.wireValues());
    assertEquals(
        "publisher-authenticated", SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED.wireValue());
    assertEquals("operator-trusted", SqliteRuntimeTrustBasis.OPERATOR_TRUSTED.toString());
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
    assertEquals(ProtocolSuccessStatus.OK, ProtocolSuccessStatus.fromWireValue("ok"));
    assertEquals(
        ProtocolRejectionStatus.REJECTED, ProtocolRejectionStatus.fromWireValue("rejected"));
    assertEquals(ProtocolFailureStatus.ERROR, ProtocolFailureStatus.fromWireValue("error"));
    assertEquals(
        ProtocolDiagnosticCode.PDF_EXPORTED, ProtocolDiagnosticCode.fromWireValue("pdf-exported"));
    assertEquals(PlanTransactionMode.ATOMIC, PlanTransactionMode.fromWireValue("atomic"));
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
        PlanFailurePolicy.fromWireValue("halt-on-first-failure"));
    assertEquals(PlanResultDetail.SUMMARY, PlanResultDetail.fromWireValue("summary"));
    assertEquals(PlanResultDetail.FULL, PlanResultDetail.fromWireValue("full"));
    assertEquals(
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        SqliteRuntimeProvenance.fromWireValue("bundle-managed"));
    assertEquals(
        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
        SqliteRuntimeProvenance.fromWireValue("source-checkout-managed"));
    assertEquals(
        SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
        SqliteRuntimeTrustBasis.fromWireValue("publisher-authenticated"));
    assertEquals(
        SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
        SqliteRuntimeTrustBasis.fromProvenance(SqliteRuntimeProvenance.BUNDLE_MANAGED));
    assertEquals(
        SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
        SqliteRuntimeTrustBasis.fromProvenance(SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED));
    assertEquals(
        SqliteRuntimeTrustBasis.OPERATOR_TRUSTED,
        SqliteRuntimeTrustBasis.fromProvenance(SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED));
    assertEquals(
        PublicCliBundleTarget.WINDOWS_AARCH64,
        PublicCliBundleTarget.fromWireValue("windows-aarch64"));

    assertThrows(
        IllegalArgumentException.class, () -> ProtocolSuccessStatus.fromWireValue("done-maybe"));
    assertThrows(
        IllegalArgumentException.class, () -> ProtocolRejectionStatus.fromWireValue("soft-nope"));
    assertThrows(
        IllegalArgumentException.class, () -> ProtocolFailureStatus.fromWireValue("bad-news"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtocolDiagnosticCode.fromWireValue("pdf-export-pending"));
    assertThrows(
        IllegalArgumentException.class, () -> PlanTransactionMode.fromWireValue("best-effort"));
    assertThrows(
        IllegalArgumentException.class, () -> PlanFailurePolicy.fromWireValue("collect-all"));
    assertThrows(IllegalArgumentException.class, () -> PlanResultDetail.fromWireValue("verbose"));
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
