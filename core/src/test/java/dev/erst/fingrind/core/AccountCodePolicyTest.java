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
    assertEquals(
        AccountCodePolicy.ChartStructure.PARENT_CHILD_HIERARCHY,
        AccountCodePolicy.chartStructure());
  }

  @Test
  void accountCodePolicy_validate_requiresInputs_andAcceptsSupportedDeclarations() {
    AccountCode accountCode = new AccountCode("1000");
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(
                nullOf(AccountCode.class),
                AccountType.ASSET,
                AccountRole.ORDINARY,
                AccountTaxonomy.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(
                accountCode,
                nullOf(AccountType.class),
                AccountRole.ORDINARY,
                AccountTaxonomy.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountCodePolicy.validate(
                accountCode,
                AccountType.ASSET,
                nullOf(AccountRole.class),
                AccountTaxonomy.empty()));
    assertDoesNotThrow(
        () ->
            AccountCodePolicy.validate(
                accountCode,
                AccountType.ASSET,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountCodePolicy.validate(
                accountCode,
                AccountType.ASSET,
                AccountRole.CONTRA,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountCodePolicy.validate(
                accountCode,
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                    java.util.Optional.empty())));
  }
}
