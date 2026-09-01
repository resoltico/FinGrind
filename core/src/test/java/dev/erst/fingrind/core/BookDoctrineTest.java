package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookDoctrine}. */
class BookDoctrineTest {
  @Test
  void inventoryCostingDoctrine_isOwnedOnlyByTradingTemplates() {
    assertNull(BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE.inventoryCostingDoctrine());
    assertNull(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL.inventoryCostingDoctrine());
    assertEquals(
        InventoryCostingDoctrine.WEIGHTED_AVERAGE,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING.inventoryCostingDoctrine());
    assertEquals(
        InventoryCostingDoctrine.WEIGHTED_AVERAGE,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL.inventoryCostingDoctrine());
  }

  @Test
  void inventoryCostingDoctrine_rejectsTemplateCombinationsThatContradictTheBookModel() {
    BookDoctrine trading = BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING;
    BookDoctrine service = BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE;

    IllegalArgumentException missingTradingDoctrine =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookDoctrine(
                    trading.accountingKernelProfileId(),
                    trading.accountingBasis(),
                    trading.accountingFrameworkPosition(),
                    trading.entityForm(),
                    trading.bookTemplateId(),
                    null));
    IllegalArgumentException serviceDoctrine =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookDoctrine(
                    service.accountingKernelProfileId(),
                    service.accountingBasis(),
                    service.accountingFrameworkPosition(),
                    service.entityForm(),
                    service.bookTemplateId(),
                    InventoryCostingDoctrine.WEIGHTED_AVERAGE));

    assertEquals(
        "Trading book doctrines require one inventoryCostingDoctrine.",
        missingTradingDoctrine.getMessage());
    assertEquals(
        "Service book doctrines must not declare an inventoryCostingDoctrine.",
        serviceDoctrine.getMessage());
  }
}
