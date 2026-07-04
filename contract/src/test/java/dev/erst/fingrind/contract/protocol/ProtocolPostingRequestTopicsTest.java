package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import org.junit.jupiter.api.Test;

/** Coverage and contract tests for raw-versus-typed posting command topic selection. */
class ProtocolPostingRequestTopicsTest {
  @Test
  void acceptsAnyEntryKind_onlyForPreflightEntry() {
    assertTrue(ProtocolPostingRequestTopics.acceptsAnyEntryKind(OperationId.PREFLIGHT_ENTRY));
    assertFalse(ProtocolPostingRequestTopics.acceptsAnyEntryKind(OperationId.POST_ENTRY));
    assertFalse(ProtocolPostingRequestTopics.acceptsAnyEntryKind(OperationId.RECORD_SALE_SETTLED));
  }

  @Test
  void requiredEntryKind_returnsExactOwnedKindOrEmpty() {
    assertEquals(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.POST_ENTRY).orElseThrow());
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_SALE_SETTLED)
            .orElseThrow());
    assertEquals(
        BookkeepingEntryKind.EXPENSE_SETTLED,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_EXPENSE_SETTLED)
            .orElseThrow());
    assertEquals(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_OWNER_CONTRIBUTION)
            .orElseThrow());
    assertEquals(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_OWNER_WITHDRAWAL)
            .orElseThrow());
    assertEquals(
        BookkeepingEntryKind.OPENING_POSITION,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_OPENING_POSITION)
            .orElseThrow());
    assertEquals(
        BookkeepingEntryKind.REVERSAL,
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.RECORD_REVERSAL).orElseThrow());
    assertTrue(
        ProtocolPostingRequestTopics.requiredEntryKind(OperationId.PREFLIGHT_ENTRY).isEmpty());
    assertTrue(ProtocolPostingRequestTopics.requiredEntryKind(OperationId.HELP).isEmpty());
  }

  @Test
  void scaffoldEntryKind_returnsCanonicalScaffoldOrRejectsUnownedOperation() {
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED,
        ProtocolPostingRequestTopics.scaffoldEntryKind(OperationId.PREFLIGHT_ENTRY));
    assertEquals(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        ProtocolPostingRequestTopics.scaffoldEntryKind(OperationId.POST_ENTRY));
    assertEquals(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        ProtocolPostingRequestTopics.scaffoldEntryKind(OperationId.RECORD_OWNER_WITHDRAWAL));

    IllegalArgumentException helpFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolPostingRequestTopics.scaffoldEntryKind(OperationId.HELP));
    assertTrue(
        java.util.Objects.requireNonNullElse(helpFailure.getMessage(), "")
            .contains("does not own one posting-request scaffold."));
  }

  @Test
  void nullOperationIsRejected() {
    assertThrows(
        NullPointerException.class,
        () -> ProtocolPostingRequestTopics.acceptsAnyEntryKind(nullOf(OperationId.class)));
    assertThrows(
        NullPointerException.class,
        () -> ProtocolPostingRequestTopics.requiredEntryKind(nullOf(OperationId.class)));
    assertThrows(
        NullPointerException.class,
        () -> ProtocolPostingRequestTopics.scaffoldEntryKind(nullOf(OperationId.class)));
  }

  @Test
  void economicEventClasses_mapToTypedWriteOperationsOrRejectNonSingletons() {
    assertEquals(
        OperationId.RECORD_SALE_ON_CREDIT,
        OperationId.forEconomicEventClass(EconomicEventClass.CREDIT_SALE));
    assertEquals(
        OperationId.RECORD_EXPENSE_ON_CREDIT,
        OperationId.forEconomicEventClass(EconomicEventClass.CREDIT_EXPENSE));
    assertEquals(
        OperationId.RECORD_RECEIPT,
        OperationId.forEconomicEventClass(EconomicEventClass.AR_SETTLEMENT));
    assertEquals(
        OperationId.RECORD_PAYMENT,
        OperationId.forEconomicEventClass(EconomicEventClass.AP_SETTLEMENT));

    IllegalArgumentException unsupported =
        assertThrows(
            IllegalArgumentException.class,
            () -> OperationId.forEconomicEventClass(EconomicEventClass.ADJUSTMENT));
    assertTrue(
        java.util.Objects.requireNonNullElse(unsupported.getMessage(), "")
            .contains("No typed write operation exists for economicEventClass 'ADJUSTMENT'."));
  }
}
