package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Typed write variants owned by the realized-foreign-exchange context. */
public sealed interface RealizedForeignExchangeBookkeepingEntryVariants
    extends TypedBookkeepingEntry
    permits RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable,
        RealizedForeignExchangeBookkeepingEntryVariants.Settlement {
  /** Creates a foreign-currency receivable at its initial functional-currency carrying amount. */
  record ForeignCurrencyReceivable(
      LocalDate effectiveDate,
      ForeignCurrencyObligationId foreignCurrencyObligationId,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      AccountCode realizedGainAccountCode,
      AccountCode realizedLossAccountCode,
      ForeignExchangeDetails foreignExchangeDetails)
      implements RealizedForeignExchangeBookkeepingEntryVariants {
    public ForeignCurrencyReceivable {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
      receivableAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              receivableAccountCode, "receivableAccountCode");
      revenueAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              revenueAccountCode, "revenueAccountCode");
      realizedGainAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              realizedGainAccountCode, "realizedGainAccountCode");
      realizedLossAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              realizedLossAccountCode, "realizedLossAccountCode");
      java.util.Objects.requireNonNull(foreignExchangeDetails, "foreignExchangeDetails");
    }
  }

  /** Settles a foreign-currency receivable and lets the executor derive realized gain or loss. */
  record Settlement(
      LocalDate effectiveDate,
      ForeignCurrencyObligationId foreignCurrencyObligationId,
      AccountCode cashAccountCode,
      ForeignExchangeDetails foreignExchangeDetails,
      @Nullable ResolvedRealizedForeignExchangeSettlement resolvedSettlement)
      implements RealizedForeignExchangeBookkeepingEntryVariants {
    public Settlement {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      java.util.Objects.requireNonNull(foreignExchangeDetails, "foreignExchangeDetails");
    }
  }
}
