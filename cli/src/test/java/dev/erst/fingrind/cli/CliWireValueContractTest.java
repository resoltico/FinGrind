package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.CapabilityStatus;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WireValue;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps CLI JSON enum serialization bound to the explicit WireValue contract. */
class CliWireValueContractTest {
  @Test
  void projectWireEnumsImplementWireValueAndExposeNonBlankTokens() {
    Enum<?>[][] enumFamilies = wireEnumFamilies().toArray(Enum<?>[][]::new);
    assertTrue(enumFamilies.length > 0, "Expected at least one FinGrind wire enum family.");
    for (Enum<?>[] constants : enumFamilies) {
      for (Enum<?> constant : constants) {
        assertTrue(
            constant instanceof WireValue,
            () -> constant.getDeclaringClass().getName() + " must implement WireValue.");
        assertFalse(
            ((WireValue) constant).wireValue().isBlank(),
            () ->
                constant.getDeclaringClass().getName()
                    + "."
                    + constant.name()
                    + " must expose a non-blank wire value.");
      }
    }
  }

  @Test
  void singletonWireOwnersExposeNonBlankTokens() {
    assertFalse(SourceChannel.CLI.wireValue().isBlank());
    assertTrue(Arrays.asList(SourceChannel.values()).contains(SourceChannel.CLI));
  }

  @Test
  void jsonText_unwrapsFinGrindEnumWireValueFailures() {
    CliWireJson.WireValueSerializationException exception =
        assertThrows(
            CliWireJson.WireValueSerializationException.class,
            () -> CliWireJson.jsonText(new BrokenPayload(BrokenEnum.BROKEN)));

    String message = java.util.Objects.requireNonNullElse(exception.getMessage(), "");
    assertTrue(message.contains("must implement WireValue"));
  }

  @Test
  void jsonText_serializesWireValuePayloads() {
    assertEquals(
        "{\"status\":\"ok\"}", CliWireJson.jsonText(new ValidPayload(ProtocolEnvelopeStatus.OK)));
  }

  private static Stream<Enum<?>[]> wireEnumFamilies() {
    return Stream.<Enum<?>[]>of(
        ActorType.values(),
        BalanceSide.values(),
        NormalBalance.values(),
        JournalLine.EntrySide.values(),
        BookInspection.Status.values(),
        ContractResponse.InitializationRequirement.values(),
        ContractResponse.CommitGuarantee.values(),
        LedgerPlanStatus.values(),
        LedgerStepStatus.values(),
        SqliteCompileOptionsVerificationStatus.values(),
        ExecutionMode.values(),
        CapabilityStatus.values(),
        LedgerAssertionKind.values(),
        LedgerStepKind.values(),
        OutputMode.values(),
        ProtocolEnvelopeStatus.values(),
        PublicCliBundleTarget.values(),
        PlanTransactionMode.values(),
        PlanFailurePolicy.values(),
        SqliteRuntime.Status.values());
  }

  private record BrokenPayload(BrokenEnum status) {}

  private record ValidPayload(ProtocolEnvelopeStatus status) {}

  /** Deliberately not a WireValue to prove contract enforcement. */
  private enum BrokenEnum {
    BROKEN
  }
}
