package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the explicit absence presentation for externally published protected-book pairs. */
class CliProtectedBookPairPublicationRetentionPresentationTest {
  @Test
  void appendTextRows_reportsWhenNoFinGrindRetainedStageEvidenceExists() {
    List<List<String>> rows = new ArrayList<>();
    CliBookPairPublicationJsonModels.@org.jspecify.annotations.Nullable PairPublicationRetentionPayload
        noRetention = null;

    CliProtectedBookPairPublicationRetentionPresentation.appendTextRows(rows, noRetention);

    assertEquals(
        List.of(
            List.of(
                "Pair publication facts",
                "No FinGrind retained-stage evidence (already-published acknowledgement)")),
        rows);
  }
}
