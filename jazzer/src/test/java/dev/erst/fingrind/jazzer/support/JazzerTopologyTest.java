package dev.erst.fingrind.jazzer.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the shared Jazzer topology contract consumed by support code and build logic. */
class JazzerTopologyTest {
  @Test
  void harnessValues_followCommittedTopologyOrder() {
    assertArrayEquals(
        new JazzerHarness[] {
          JazzerHarness.cliRequest(),
          JazzerHarness.postingWorkflow(),
          JazzerHarness.sqliteBookRoundTrip()
        },
        JazzerHarness.values());
  }

  @Test
  void runTargets_followCommittedTopologyOrder() {
    assertArrayEquals(
        new JazzerRunTarget[] {
          JazzerRunTarget.regression(),
          JazzerRunTarget.cliRequest(),
          JazzerRunTarget.postingWorkflow(),
          JazzerRunTarget.sqliteBookRoundTrip()
        },
        JazzerRunTarget.values());
  }

  @Test
  void harnessMetadata_matchesCommittedPaths() {
    assertEquals(
        "dev/erst/fingrind/cli/CliRequestFuzzTestInputs/readPostEntryCommand",
        JazzerHarness.cliRequest().inputResourceDirectory());
    assertEquals(
        "dev/erst/fingrind/cli/SqliteBookRoundTripFuzzTestInputs/roundTripSingleBook",
        JazzerHarness.sqliteBookRoundTrip().inputResourceDirectory());
  }

  @Test
  void runTargets_resolveStableTaskNamesAndHarnessAssignments() {
    assertEquals(JazzerRunTarget.postingWorkflow(), JazzerRunTarget.fromTaskName("fuzzPostingWorkflow"));
    assertEquals(
        List.of(
            JazzerHarness.cliRequest(),
            JazzerHarness.postingWorkflow(),
            JazzerHarness.sqliteBookRoundTrip()),
        JazzerRunTarget.regression().harnesses());
    assertEquals(JazzerHarness.cliRequest(), JazzerRunTarget.cliRequest().replayHarness());
  }

  @Test
  void invalidLookup_rejectsUnknownKeys() {
    assertThrows(IllegalArgumentException.class, () -> JazzerHarness.fromKey("missing"));
    assertThrows(IllegalArgumentException.class, () -> JazzerRunTarget.fromKey("missing"));
    assertThrows(IllegalArgumentException.class, () -> JazzerRunTarget.fromTaskName("missing"));
  }

  @Test
  void activeFuzzTargets_shareTheirKeyWithSingleHarness() {
    List<JazzerRunTarget> activeTargets =
        Arrays.stream(JazzerRunTarget.values()).filter(JazzerRunTarget::activeFuzzing).toList();

    activeTargets.forEach(
        target -> {
          assertEquals(1, target.harnesses().size());
          assertEquals(target.key(), target.harnesses().getFirst().key());
        });
  }
}
