package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Covers the human-facing doctrine labels shown across operator surfaces. */
class BookDoctrineDisplayTest {
  @Test
  void bookDoctrineDisplay_rendersCanonicalHumanLabels() {
    assertEquals(
        "Internal management bookkeeping",
        BookDoctrineDisplay.accountingKernel(
            AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL));
    assertEquals("Cash basis", BookDoctrineDisplay.accountingBasis(AccountingBasis.CASH));
    assertEquals("Accrual basis", BookDoctrineDisplay.accountingBasis(AccountingBasis.ACCRUAL));
    assertEquals(
        "Non-statutory internal management",
        BookDoctrineDisplay.accountingFrameworkPosition(
            AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT));
    assertEquals(
        "Owner-managed single entity",
        BookDoctrineDisplay.entityForm(EntityForm.OWNER_MANAGED_SINGLE_ENTITY));
    assertEquals(
        "Owner-managed service seed template",
        BookDoctrineDisplay.bookTemplate(BookTemplateId.OWNER_MANAGED_SERVICE));
    assertEquals(
        "Owner-managed trading seed template",
        BookDoctrineDisplay.bookTemplate(BookTemplateId.OWNER_MANAGED_TRADING));
  }

  @Test
  void accountingKernel_returnsWireValueForUnrecognizedKernelProfiles() {
    assertEquals(
        "custom-kernel",
        BookDoctrineDisplay.accountingKernel(new AccountingKernelProfileId("custom-kernel")));
  }

  @Test
  void bookDoctrineDisplay_requiresEveryInput() {
    assertThrows(NullPointerException.class, () -> BookDoctrineDisplay.accountingKernel(nullOf()));
    assertThrows(NullPointerException.class, () -> BookDoctrineDisplay.accountingBasis(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> BookDoctrineDisplay.accountingFrameworkPosition(nullOf()));
    assertThrows(NullPointerException.class, () -> BookDoctrineDisplay.entityForm(nullOf()));
    assertThrows(NullPointerException.class, () -> BookDoctrineDisplay.bookTemplate(nullOf()));
  }
}
