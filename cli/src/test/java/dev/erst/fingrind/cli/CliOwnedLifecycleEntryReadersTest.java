package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Exercises every owned lifecycle request shape through the typed-reader dispatcher. */
class CliOwnedLifecycleEntryReadersTest extends CliRequestReaderTestSupport {
  @Test
  void read_fixedAssetVariants_preservesCallerFacts() throws IOException {
    FixedAssetBookkeepingEntryVariants.Capitalization capitalization =
        assertInstanceOf(
            FixedAssetBookkeepingEntryVariants.Capitalization.class,
            CliTypedBookkeepingEntryReaders.read(
                rootNode(
                    """
                    {
                      "effectiveDate": "2026-06-01",
                      "fixedAssetId": "delivery-van-001",
                      "assetAccountCode": "1600",
                      "accumulatedDepreciationAccountCode": "1601",
                      "depreciationExpenseAccountCode": "5000",
                      "disposalGainAccountCode": "4100",
                      "disposalLossAccountCode": "5001",
                      "cashAccountCode": "1000",
                      "cost": %s,
                      "depreciationSchedule": {
                        "inServiceDate": "2026-06-01",
                        "usefulLifeMonths": 60,
                        "residualValue": %s
                      }
                    }
                    """
                        .formatted(eurMoneyJson("12000"), eurMoneyJson("0"))),
                BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION));
    assertEquals(new AccountCode("1600"), capitalization.assetAccountCode());
    assertEquals(60, capitalization.depreciationSchedule().usefulLifeMonths());

    assertEquals(
        "delivery-van-001",
        assertInstanceOf(
                FixedAssetBookkeepingEntryVariants.Depreciation.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-06-30",
                          "fixedAssetId": "delivery-van-001"
                        }
                        """),
                    BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION))
            .fixedAssetId()
            .value());
    assertEquals(
        new AccountCode("1000"),
        assertInstanceOf(
                FixedAssetBookkeepingEntryVariants.Disposal.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-07-01",
                          "fixedAssetId": "delivery-van-001",
                          "cashAccountCode": "1000",
                          "proceeds": %s
                        }
                        """
                            .formatted(eurMoneyJson("11000"))),
                    BookkeepingEntryKind.FIXED_ASSET_DISPOSAL))
            .cashAccountCode());
  }

  @Test
  void read_financingVariants_preservesArrangementState() throws IOException {
    assertEquals(
        new AccountCode("2100"),
        assertInstanceOf(
                FinancingBookkeepingEntryVariants.Borrowing.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-06-01",
                          "financingArrangementId": "working-capital-001",
                          "cashAccountCode": "1000",
                          "principalLiabilityAccountCode": "2100",
                          "interestPayableAccountCode": "2101",
                          "principalAmount": %s
                        }
                        """
                            .formatted(eurMoneyJson("10000"))),
                    BookkeepingEntryKind.FINANCING_BORROWING))
            .principalLiabilityAccountCode());
    assertEquals(
        "working-capital-001",
        assertInstanceOf(
                FinancingBookkeepingEntryVariants.PrincipalRepayment.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-06-02",
                          "financingArrangementId": "working-capital-001",
                          "cashAccountCode": "1000",
                          "principalAmount": %s
                        }
                        """
                            .formatted(eurMoneyJson("4000"))),
                    BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT))
            .financingArrangementId()
            .value());
    assertEquals(
        new AccountCode("5002"),
        assertInstanceOf(
                FinancingBookkeepingEntryVariants.InterestAccrual.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-06-03",
                          "financingArrangementId": "working-capital-001",
                          "interestExpenseAccountCode": "5002",
                          "interestAmount": %s
                        }
                        """
                            .formatted(eurMoneyJson("500"))),
                    BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL))
            .interestExpenseAccountCode());
    assertEquals(
        new AccountCode("1000"),
        assertInstanceOf(
                FinancingBookkeepingEntryVariants.InterestPayment.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-06-04",
                          "financingArrangementId": "working-capital-001",
                          "cashAccountCode": "1000",
                          "interestAmount": %s
                        }
                        """
                            .formatted(eurMoneyJson("500"))),
                    BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT))
            .cashAccountCode());
  }

  @Test
  void read_realizedForeignExchangeVariants_requiresAndPreservesExchangeFacts() throws IOException {
    RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable obligation =
        assertInstanceOf(
            RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable.class,
            CliTypedBookkeepingEntryReaders.read(
                rootNode(
                    """
                    {
                      "effectiveDate": "2026-07-01",
                      "foreignCurrencyObligationId": "usd-receivable-001",
                      "receivableAccountCode": "1100",
                      "revenueAccountCode": "4000",
                      "realizedGainAccountCode": "4100",
                      "realizedLossAccountCode": "5003",
                      "foreignExchange": %s
                    }
                    """
                        .formatted(foreignExchangeJson("92.00", "2026-07-01"))),
                BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION));
    assertEquals("USD", obligation.foreignExchangeDetails().transactionAmount().currencyCode());

    assertEquals(
        new AccountCode("1000"),
        assertInstanceOf(
                RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-07-03",
                          "foreignCurrencyObligationId": "usd-receivable-001",
                          "cashAccountCode": "1000",
                          "foreignExchange": %s
                        }
                        """
                            .formatted(foreignExchangeJson("95.00", "2026-07-03"))),
                    BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT))
            .cashAccountCode());
  }

  @Test
  void contextReaders_rejectKindsOwnedByAnotherContext() throws IOException {
    ObjectNode rootNode = rootNode("{}");

    assertThrows(
        IllegalArgumentException.class, () -> CliTypedBookkeepingEntryReaders.read(rootNode, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliInventoryBookkeepingEntryReaders.read(rootNode, BookkeepingEntryKind.SALE_SETTLED));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliAccrualCutoffBookkeepingEntryReaders.read(
                rootNode, BookkeepingEntryKind.SALE_SETTLED));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliFixedAssetBookkeepingEntryReaders.read(
                rootNode, BookkeepingEntryKind.FINANCING_BORROWING));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliFinancingBookkeepingEntryReaders.read(
                rootNode, BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliRealizedForeignExchangeBookkeepingEntryReaders.read(
                rootNode, BookkeepingEntryKind.FIXED_ASSET_DISPOSAL));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliLatvianPayrollBookkeepingEntryReaders.read(
                rootNode, BookkeepingEntryKind.FIXED_ASSET_DISPOSAL));
  }

  private static ObjectNode rootNode(String json) throws IOException {
    return (ObjectNode) CliJsonObjectMappers.configuredObjectMapper().readTree(json);
  }

  private static String foreignExchangeJson(String functionalAmount, String quotedOn) {
    return """
        {
          "transactionAmount": {
            "currencyCode": "USD",
            "minorUnits": "10000"
          },
          "functionalAmount": {
            "currencyCode": "EUR",
            "minorUnits": "%s"
          },
          "quotedRate": {
            "transactionCurrencyAmount": {
              "currencyCode": "USD",
              "minorUnits": "10000"
            },
            "functionalCurrencyAmount": {
              "currencyCode": "EUR",
              "minorUnits": "%s"
            },
            "quotedOn": "%s",
            "quoteSource": "ecb-spot"
          },
          "treatmentKind": "SPOT_TRANSACTION"
        }
        """
        .formatted(functionalAmount.replace(".", ""), functionalAmount.replace(".", ""), quotedOn);
  }
}
