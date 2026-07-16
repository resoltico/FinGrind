package dev.erst.fingrind.executor.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Documents the empty optional capability provided to stores that do not own accrual cut-offs. */
class AccrualCutoffLookupStoreTest {
  @Test
  void defaultMethodsExposeNoAccrualCutoffs() {
    AccrualCutoffLookupStore store = new AccrualCutoffLookupStore() {};

    assertEquals(Optional.empty(), store.findAccrualCutoff(new AccrualCutoffId("cutoff-2026")));
    assertEquals(List.of(), store.accrualCutoffs(Optional.of(LocalDate.parse("2026-04-30"))));
  }
}
