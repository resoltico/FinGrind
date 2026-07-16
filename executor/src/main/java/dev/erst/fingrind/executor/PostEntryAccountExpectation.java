package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One typed-entry account expectation owned by the role-account admission policy. */
record PostEntryAccountExpectation(
    AccountCode accountCode,
    String field,
    @Nullable AccountType expectedAccountType,
    @Nullable FinancialPositionLineClassification expectedFinancialPositionClassification,
    @Nullable CashFlowAssetClassification expectedCashFlowAssetClassification,
    @Nullable AccountRole expectedAccountRole) {
  PostEntryAccountExpectation {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(field, "field");
  }
}
