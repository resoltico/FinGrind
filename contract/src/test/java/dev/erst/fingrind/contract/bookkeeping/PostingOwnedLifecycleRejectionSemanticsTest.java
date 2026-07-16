package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves that every owned lifecycle rejection has canonical public metadata. */
class PostingOwnedLifecycleRejectionSemanticsTest {
  @Test
  void fixedAssetFinancingAndRealizedForeignExchangeCodesArePublished() {
    List<String> codes =
        List.of(
            "fixed-asset-id-already-exists",
            "fixed-asset-not-found",
            "fixed-asset-already-disposed",
            "fixed-asset-depreciation-precedes-in-service-date",
            "fixed-asset-lifecycle-precedes-horizon",
            "fixed-asset-fully-depreciated",
            "fixed-asset-disposal-currency-mismatch",
            "financing-arrangement-id-already-exists",
            "financing-arrangement-not-found",
            "financing-principal-repayment-exceeds-outstanding",
            "financing-interest-payment-exceeds-accrued",
            "financing-lifecycle-precedes-horizon",
            "financing-currency-mismatch",
            "foreign-currency-obligation-id-already-exists",
            "foreign-currency-obligation-not-found",
            "foreign-currency-obligation-already-settled",
            "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
            "realized-foreign-exchange-settlement-transaction-amount-mismatch",
            "realized-foreign-exchange-settlement-functional-currency-mismatch");

    codes.forEach(
        code -> {
          EntrySemanticsViolation violation = new EntrySemanticsViolation(code, "field", "message");
          assertEquals(code, violation.code());
          assertEquals(EntrySemanticsViolationOwner.require(code).category(), violation.category());
          assertEquals(EntrySemanticsViolationOwner.require(code).repair(), violation.repair());
        });
  }
}
