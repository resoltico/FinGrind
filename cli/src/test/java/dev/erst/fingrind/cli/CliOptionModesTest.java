package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused unit tests for option-mode parsing helpers. */
class CliOptionModesTest {
  @Test
  void requirePostingCoverage_coversDuplicateInvalidAndValidValues() {
    assertEquals(
        PostingCoverage.ALL_POSTING_KINDS,
        CliOptionModes.requirePostingCoverage(
            null, List.of(PostingCoverage.ALL_POSTING_KINDS.wireValue()).listIterator()));

    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliOptionModes.requirePostingCoverage(
                    PostingCoverage.ALL_POSTING_KINDS, List.of("any").listIterator()));
    assertEquals("--posting-coverage", duplicate.argument());

    CliArgumentsException invalid =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionModes.requirePostingCoverage(null, List.of("bogus").listIterator()));
    assertEquals("--posting-coverage", invalid.argument());
  }

  @Test
  void requirePlanResultDetail_coversDuplicateInvalidAndValidValues() {
    assertEquals(
        PlanResultDetail.FULL,
        CliOptionModes.requirePlanResultDetail(
            null, List.of(PlanResultDetail.FULL.wireValue()).listIterator()));

    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliOptionModes.requirePlanResultDetail(
                    PlanResultDetail.SUMMARY, List.of("full").listIterator()));
    assertEquals("--result-detail", duplicate.argument());

    CliArgumentsException invalid =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionModes.requirePlanResultDetail(null, List.of("bogus").listIterator()));
    assertEquals("--result-detail", invalid.argument());
  }

  @Test
  void requireDiscoveryDetail_coversDuplicateInvalidAndValidValues() {
    assertEquals(
        DiscoveryDetail.FULL,
        CliOptionModes.requireDiscoveryDetail(
            null, List.of(DiscoveryDetail.FULL.wireValue()).listIterator()));

    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliOptionModes.requireDiscoveryDetail(
                    DiscoveryDetail.COMPACT, List.of("full").listIterator()));
    assertEquals("--detail", duplicate.argument());

    CliArgumentsException invalid =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionModes.requireDiscoveryDetail(null, List.of("bogus").listIterator()));
    assertEquals("--detail", invalid.argument());
  }

  @Test
  void requireOutputMode_coversInvalidAndUnsupportedSelections() {
    CliArgumentsException invalid =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionModes.requireOutputMode(null, "bogus", List.of(OutputMode.JSON)));
    assertEquals("--output", invalid.argument());

    CliArgumentsException unsupported =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionModes.requireOutputMode(null, "text", List.of(OutputMode.JSON)));
    assertEquals("--output", unsupported.argument());
  }
}
