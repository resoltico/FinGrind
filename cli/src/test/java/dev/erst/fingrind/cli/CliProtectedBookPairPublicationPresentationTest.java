package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the explicit absence presentation for externally published protected-book pairs. */
class CliProtectedBookPairPublicationPresentationTest {
  @Test
  void appendTextRows_reportsWhenNoFinGrindPublicationTransactionExists() {
    List<List<String>> rows = new ArrayList<>();
    CliBookPairPublicationJsonModels.@org.jspecify.annotations.Nullable PairPublicationPayload
        noPublication = null;

    CliProtectedBookPairPublicationPresentation.appendTextRows(rows, noPublication);

    assertEquals(
        List.of(
            List.of(
                "Pair publication facts",
                "No FinGrind publication transaction (already-published acknowledgement)")),
        rows);
  }
}
