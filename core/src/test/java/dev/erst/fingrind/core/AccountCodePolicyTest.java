package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the canonical FinGrind account-code meaning and chart-structure policy. */
class AccountCodePolicyTest {
  @Test
  void accountCodePolicy_exposesCurrentMeaningAndChartStructure() {
    assertEquals(
        AccountCodePolicy.Meaning.OPAQUE_BOOK_LOCAL_IDENTIFIER, AccountCodePolicy.meaning());
    assertEquals(AccountCodePolicy.ChartStructure.FLAT, AccountCodePolicy.chartStructure());
  }

  @Test
  void accountCodePolicy_validate_requiresInputs_andAcceptsSupportedDeclarations() {
    AccountCode accountCode = new AccountCode("1000");
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(
                nullOf(AccountCode.class), AccountType.ASSET, AccountRole.ORDINARY));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(
                accountCode, nullOf(AccountType.class), AccountRole.ORDINARY));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(accountCode, AccountType.ASSET, nullOf(AccountRole.class)));
    assertDoesNotThrow(
        () -> AccountCodePolicy.validate(accountCode, AccountType.ASSET, AccountRole.ORDINARY));
    assertDoesNotThrow(
        () -> AccountCodePolicy.validate(accountCode, AccountType.ASSET, AccountRole.CONTRA));
    assertDoesNotThrow(
        () ->
            AccountCodePolicy.validate(
                accountCode, AccountType.EQUITY, AccountRole.RETAINED_EARNINGS));
  }
}
