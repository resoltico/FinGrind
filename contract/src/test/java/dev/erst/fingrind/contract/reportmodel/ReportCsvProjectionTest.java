package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks tabular report projections to immutable rectangular data with unique headers. */
class ReportCsvProjectionTest {
  @Test
  void projection_normalizesHeadersAndDefensivelyCopiesRows() {
    List<String> headers = new ArrayList<>(List.of(" code ", "amount"));
    List<String> row = new ArrayList<>(List.of("inventory", "5000"));
    ReportCsvProjection projection = new ReportCsvProjection(headers, List.of(row));

    headers.set(0, "changed");
    row.set(1, "changed");

    assertEquals(List.of("code", "amount"), projection.headers());
    assertEquals(List.of("inventory", "5000"), projection.rows().getFirst());
    assertThrows(UnsupportedOperationException.class, () -> projection.rows().add(List.of()));
    assertThrows(UnsupportedOperationException.class, () -> projection.rows().getFirst().add("x"));
  }

  @Test
  void projection_rejectsMissingDuplicateAndNonRectangularHeadersOrRows() {
    assertThrows(NullPointerException.class, () -> new ReportCsvProjection(nullOf(), List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new ReportCsvProjection(List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReportCsvProjection(List.of("code", "code"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReportCsvProjection(List.of("code"), List.of(List.of("inventory", "5000"))));
  }
}
