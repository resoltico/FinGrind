package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OutputMode}. */
class OutputModeTest {
  @Test
  void wireValuesAndParsing_followTheCanonicalVocabulary() {
    assertEquals(List.of("json", "text", "csv"), OutputMode.wireValues());
    assertEquals(OutputMode.JSON, OutputMode.fromWireValue("json"));
    assertEquals(OutputMode.TEXT, OutputMode.fromWireValue("text"));
    assertEquals(OutputMode.CSV, OutputMode.fromWireValue("csv"));
    assertEquals("json", OutputMode.JSON.wireValue());
    assertEquals("text", OutputMode.TEXT.wireValue());
    assertEquals("csv", OutputMode.CSV.wireValue());
    assertThrows(NullPointerException.class, () -> OutputMode.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> OutputMode.fromWireValue("spreadsheet-maybe"));
  }

  @Test
  void run_dispatchesToTheSelectedOutputBranch() {
    AtomicInteger counter = new AtomicInteger();
    OutputMode.JSON.run(
        counter::incrementAndGet, () -> counter.addAndGet(10), () -> counter.addAndGet(100));
    assertEquals(1, counter.get());
    OutputMode.TEXT.run(
        counter::incrementAndGet, () -> counter.addAndGet(10), () -> counter.addAndGet(100));
    assertEquals(11, counter.get());
    OutputMode.CSV.run(
        counter::incrementAndGet, () -> counter.addAndGet(10), () -> counter.addAndGet(100));
    assertEquals(111, counter.get());
    assertThrows(
        NullPointerException.class, () -> OutputMode.JSON.run(nullOf(), () -> {}, () -> {}));
    assertThrows(
        NullPointerException.class, () -> OutputMode.TEXT.run(() -> {}, nullOf(), () -> {}));
    assertThrows(
        NullPointerException.class, () -> OutputMode.CSV.run(() -> {}, () -> {}, nullOf()));
  }
}
