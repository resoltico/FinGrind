package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for Financing and Realized Foreign Exchange registers. */
public interface CliLifecycleContextReportJsonModels {
  /** Sealed family for retained-lifecycle registers with a shared CSV projection owner. */
  sealed interface LifecycleContextReportPayload extends CliReportJsonModels.ReportPayload
      permits FinancingRegisterPayload, RealizedForeignExchangeRegisterPayload {}

  record FinancingRegisterPayload(
      String family,
      CliBookInspectionJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.FinancingRegisterResolvedQuery resolvedQuery,
      String generatedAt,
      List<FinancingRegisterRowPayload> rows)
      implements LifecycleContextReportPayload {
    public FinancingRegisterPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record FinancingRegisterRowPayload(
      String financingArrangementId,
      String originatedOn,
      String lifecycleHorizon,
      String principalLiabilityAccountCode,
      String interestPayableAccountCode,
      MoneyPayload originalPrincipal,
      MoneyPayload principalRepaid,
      MoneyPayload principalOutstanding,
      MoneyPayload interestAccrued,
      MoneyPayload interestPaid,
      MoneyPayload interestOutstanding) {
    public FinancingRegisterRowPayload {
      financingArrangementId = requireText(financingArrangementId, "financingArrangementId");
      originatedOn = requireText(originatedOn, "originatedOn");
      lifecycleHorizon = requireText(lifecycleHorizon, "lifecycleHorizon");
      principalLiabilityAccountCode =
          requireText(principalLiabilityAccountCode, "principalLiabilityAccountCode");
      interestPayableAccountCode =
          requireText(interestPayableAccountCode, "interestPayableAccountCode");
      Objects.requireNonNull(originalPrincipal, "originalPrincipal");
      Objects.requireNonNull(principalRepaid, "principalRepaid");
      Objects.requireNonNull(principalOutstanding, "principalOutstanding");
      Objects.requireNonNull(interestAccrued, "interestAccrued");
      Objects.requireNonNull(interestPaid, "interestPaid");
      Objects.requireNonNull(interestOutstanding, "interestOutstanding");
    }
  }

  record RealizedForeignExchangeRegisterPayload(
      String family,
      CliBookInspectionJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.RealizedForeignExchangeRegisterResolvedQuery resolvedQuery,
      String generatedAt,
      List<RealizedForeignExchangeRegisterRowPayload> rows)
      implements LifecycleContextReportPayload {
    public RealizedForeignExchangeRegisterPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record RealizedForeignExchangeRegisterRowPayload(
      String foreignCurrencyObligationId,
      String originatedOn,
      String lifecycleHorizon,
      String receivableAccountCode,
      MoneyPayload transactionAmount,
      MoneyPayload functionalCarryingAmount,
      @Nullable String settledOn,
      @Nullable MoneyPayload functionalSettlementAmount,
      @Nullable MoneyPayload realizedGainOrLossAmount,
      @Nullable Boolean realizedGain) {
    public RealizedForeignExchangeRegisterRowPayload {
      foreignCurrencyObligationId =
          requireText(foreignCurrencyObligationId, "foreignCurrencyObligationId");
      originatedOn = requireText(originatedOn, "originatedOn");
      lifecycleHorizon = requireText(lifecycleHorizon, "lifecycleHorizon");
      receivableAccountCode = requireText(receivableAccountCode, "receivableAccountCode");
      Objects.requireNonNull(transactionAmount, "transactionAmount");
      Objects.requireNonNull(functionalCarryingAmount, "functionalCarryingAmount");
      settledOn = requireOptionalText(settledOn, "settledOn");
      boolean allSettlementFactsPresent =
          functionalSettlementAmount != null
              && realizedGainOrLossAmount != null
              && realizedGain != null;
      boolean noSettlementFactsPresent =
          functionalSettlementAmount == null
              && realizedGainOrLossAmount == null
              && realizedGain == null;
      if (!allSettlementFactsPresent && !noSettlementFactsPresent) {
        throw new IllegalArgumentException(
            "Settlement amount, gain-or-loss amount, and gain flag must be present together.");
      }
      if ((settledOn != null) != allSettlementFactsPresent) {
        throw new IllegalArgumentException(
            "Settlement fields must be present exactly when settledOn is present.");
      }
    }
  }
}
