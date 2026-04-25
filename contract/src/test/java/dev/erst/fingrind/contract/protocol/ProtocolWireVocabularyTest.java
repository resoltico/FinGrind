package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for newly typed protocol wire vocabularies. */
class ProtocolWireVocabularyTest {
  @Test
  void responseAndPlanEnums_publishStableWireValues() {
    assertEquals(
        List.of("ok", "preflight-accepted", "committed", "plan-committed"),
        ProtocolSuccessStatus.wireValues());
    assertEquals("ok", ProtocolSuccessStatus.OK.toString());
    assertEquals(
        List.of("rejected", "plan-rejected", "plan-assertion-failed"),
        ProtocolRejectionStatus.wireValues());
    assertEquals("plan-rejected", ProtocolRejectionStatus.PLAN_REJECTED.toString());
    assertEquals(List.of("error"), ProtocolFailureStatus.wireValues());
    assertEquals("error", ProtocolFailureStatus.ERROR.toString());
    assertEquals(List.of("atomic"), PlanTransactionMode.wireValues());
    assertEquals("atomic", PlanTransactionMode.ATOMIC.toString());
    assertEquals(List.of("halt-on-first-failure"), PlanFailurePolicy.wireValues());
    assertEquals("halt-on-first-failure", PlanFailurePolicy.HALT_ON_FIRST_FAILURE.toString());
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
        ProtocolRejectionStatus.PLAN_REJECTED,
        ProtocolRejectionStatus.fromWireValue("plan-rejected"));
    assertEquals(ProtocolFailureStatus.ERROR, ProtocolFailureStatus.fromWireValue("error"));
    assertEquals(PlanTransactionMode.ATOMIC, PlanTransactionMode.fromWireValue("atomic"));
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
        PlanFailurePolicy.fromWireValue("halt-on-first-failure"));
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
        IllegalArgumentException.class, () -> PlanTransactionMode.fromWireValue("best-effort"));
    assertThrows(
        IllegalArgumentException.class, () -> PlanFailurePolicy.fromWireValue("collect-all"));
    assertThrows(
        IllegalArgumentException.class, () -> PublicCliBundleTarget.fromWireValue("plan9-x86"));
  }
}
