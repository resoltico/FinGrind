package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
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
    assertEquals(
        BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
        BookMigrationPolicy.fromWireValue("sequential-in-place"));
    assertEquals(List.of("sequential-in-place"), BookMigrationPolicy.wireValues());
    assertEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE.wireValue());
    assertEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE.toString());
    assertNotEquals(null, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertNotEquals("sequential-in-place", BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
    assertNotEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, null);
    assertNotEquals(BookMigrationPolicy.SEQUENTIAL_IN_PLACE, "sequential-in-place");
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
    assertThrows(NullPointerException.class, () -> BookMigrationPolicy.fromWireValue(null));
    assertThrows(
        IllegalArgumentException.class, () -> BookMigrationPolicy.fromWireValue("migrate"));
  }

  @Test
  void descriptorNamespacesPublishTheirRecordInventories() {
    ContractResponse.RejectionDescriptor leafRejection =
        new ContractResponse.RejectionDescriptor("code", "description");

    assertTrue(ContractDiscovery.descriptorTypes().contains(HelpDescriptor.class));
    assertTrue(
        ContractRequestShapes.descriptorTypes()
            .contains(ContractRequestShapes.LedgerPlanRequestShapeDescriptor.class));
    assertTrue(
        ContractResponse.descriptorTypes()
            .contains(ContractResponse.ResponseModelDescriptor.class));
    assertTrue(
        ContractTemplates.descriptorTypes()
            .contains(ContractTemplates.LedgerPlanTemplateDescriptor.class));
    assertEquals(List.of(), leafRejection.detailFields());
    assertEquals(List.of(), leafRejection.detailRejections());
  }
}
