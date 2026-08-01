package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that one derived interim-sweep effect owns and validates its complete value boundary.
 */
class AttestationInterimResultSweepEffectTest {
  private static final ReportingPeriod PERIOD =
      new ReportingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

  @Test
  void ownsItsCollectionsAndRejectsIncompleteSweepDeclarations() {
    List<dev.erst.fingrind.core.CurrencyBalance> totals = new ArrayList<>();
    List<AttestationClosePostingSnapshot> postings = new ArrayList<>();
    AttestationInterimResultSweepEffect effect =
        new AttestationInterimResultSweepEffect(PERIOD, "3000", 1, totals, postings);
    totals.add(nullOf());
    postings.add(nullOf());

    assertEquals(List.of(), effect.sweptTotals());
    assertEquals(List.of(), effect.postings());
    assertThrows(
        NullPointerException.class,
        () -> new AttestationInterimResultSweepEffect(nullOf(), "3000", 1, List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationInterimResultSweepEffect(PERIOD, nullOf(), 1, List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationInterimResultSweepEffect(PERIOD, " ", 1, List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationInterimResultSweepEffect(PERIOD, "3000", 0, List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationInterimResultSweepEffect(PERIOD, "3000", 1, nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AttestationInterimResultSweepEffect(PERIOD, "3000", 1, List.of(), nullOf()));
  }
}
