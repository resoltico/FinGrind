package dev.erst.fingrind.executor.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Preserves empty-store semantics for optional owned lifecycle contexts. */
class OwnedLifecycleLookupStoreDefaultTest {
  @Test
  void fixedAssetDefaults_exposeNoHistoricalOrActiveRecords() {
    FixedAssetLookupStore store = new FixedAssetLookupStore() {};
    FixedAssetId id = new FixedAssetId("office-desk");

    assertEquals(Optional.empty(), store.findFixedAsset(id));
    assertFalse(store.hasFixedAsset(id));
    assertEquals(List.of(), store.fixedAssets(Optional.empty()));
  }

  @Test
  void financingDefaults_exposeNoHistoricalOrActiveRecords() {
    FinancingLookupStore store = new FinancingLookupStore() {};
    FinancingArrangementId id = new FinancingArrangementId("working-capital-loan");

    assertEquals(Optional.empty(), store.findFinancingArrangement(id));
    assertFalse(store.hasFinancingArrangement(id));
    assertEquals(List.of(), store.financingArrangements());
  }

  @Test
  void realizedForeignExchangeDefaults_exposeNoHistoricalOrActiveRecords() {
    RealizedForeignExchangeLookupStore store = new RealizedForeignExchangeLookupStore() {};
    ForeignCurrencyObligationId id = new ForeignCurrencyObligationId("usd-client-invoice");

    assertEquals(Optional.empty(), store.findForeignCurrencyObligation(id));
    assertFalse(store.hasForeignCurrencyObligation(id));
    assertEquals(List.of(), store.foreignCurrencyObligations());
  }
}
