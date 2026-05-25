package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for low-level CLI argument helper branches. */
class CliArgumentValueParserTest {
  @Test
  void requirePageLimit_acceptsBoundaryValuesAndRejectsOutOfRangeValues() {
    assertEquals(1, CliArgumentValueParser.requirePageLimit(1, "--limit"));
    assertEquals(200, CliArgumentValueParser.requirePageLimit(200, "--limit"));

    CliArgumentsException belowMinimum =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(0, "--limit"));
    CliArgumentsException aboveMaximum =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(201, "--limit"));

    assertEquals("--limit", belowMinimum.argument());
    assertEquals("--limit", aboveMaximum.argument());
  }

  @Test
  void parseDomainValueOptions_acceptValidValuesAndRejectInvalidOnes() {
    assertEquals(
        CurrencyUnit.of("EUR"),
        CliArgumentValueParser.parseCurrencyUnitOption("EUR", "--functional-currency"));
    assertEquals(
        new BookEntityName("Acme Studio"),
        CliArgumentValueParser.parseBookEntityNameOption("Acme Studio", "--entity-name"));
    assertEquals(
        FiscalYearStart.parse("01-01"),
        CliArgumentValueParser.parseFiscalYearStartOption("01-01", "--fiscal-year-start"));

    assertEquals(
        "--functional-currency",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliArgumentValueParser.parseCurrencyUnitOption("ZZZ", "--functional-currency"))
            .argument());
    assertEquals(
        "--entity-name",
        assertThrows(
                CliArgumentsException.class,
                () -> CliArgumentValueParser.parseBookEntityNameOption(" ", "--entity-name"))
            .argument());
    assertEquals(
        "--fiscal-year-start",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliArgumentValueParser.parseFiscalYearStartOption(
                        "13-40", "--fiscal-year-start"))
            .argument());
  }

  @Test
  void parseStructuredOpenBookValueOptions_acceptValidValuesAndRejectInvalidOnes() {
    assertEquals(
        new BusinessActivityTag("translation,localization"),
        CliArgumentValueParser.parseBusinessActivityTagOption(
            "translation,localization", "--business-activity-tag"));

    assertEquals(
        "--business-activity-tag",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliArgumentValueParser.parseBusinessActivityTagOption(
                        " ", "--business-activity-tag"))
            .argument());
  }

  @Test
  void requirePostingCoverage_acceptsOneValueAndRejectsDuplicateOrUnknownValues() {
    ListIterator<String> validIterator = List.of("non-closing-postings").listIterator();
    assertEquals(
        PostingCoverage.NON_CLOSING_POSTINGS,
        CliArgumentValueParser.requirePostingCoverage(null, validIterator));

    CliArgumentsException duplicateException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requirePostingCoverage(
                    PostingCoverage.ALL_POSTING_KINDS, List.<String>of().listIterator()));
    assertEquals(ProtocolOptions.POSTING_COVERAGE, duplicateException.argument());

    CliArgumentsException invalidException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requirePostingCoverage(
                    null, List.of("unknown-coverage").listIterator()));
    assertEquals(ProtocolOptions.POSTING_COVERAGE, invalidException.argument());
  }

  @Test
  void requirePlanResultDetail_acceptsOneValueAndRejectsDuplicateOrUnknownValues() {
    assertEquals(
        PlanResultDetail.FULL,
        CliArgumentValueParser.requirePlanResultDetail(
            null, List.of(PlanResultDetail.FULL.wireValue()).listIterator()));

    CliArgumentsException duplicateException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requirePlanResultDetail(
                    PlanResultDetail.SUMMARY, List.<String>of().listIterator()));
    assertEquals(ProtocolOptions.RESULT_DETAIL, duplicateException.argument());

    CliArgumentsException invalidException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requirePlanResultDetail(
                    null, List.of("verbose").listIterator()));
    assertEquals(ProtocolOptions.RESULT_DETAIL, invalidException.argument());
    assertTrue(
        Objects.requireNonNull(invalidException.getMessage())
            .contains("Accepted values: summary, full."));
  }

  @Test
  void requireDiscoveryDetail_acceptsOneValueAndRejectsDuplicateOrUnknownValues() {
    assertEquals(
        DiscoveryDetail.FULL,
        CliArgumentValueParser.requireDiscoveryDetail(
            null, List.of(DiscoveryDetail.FULL.wireValue()).listIterator()));

    CliArgumentsException duplicateException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requireDiscoveryDetail(
                    DiscoveryDetail.COMPACT, List.<String>of().listIterator()));
    assertEquals(ProtocolOptions.DETAIL, duplicateException.argument());

    CliArgumentsException invalidException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArgumentValueParser.requireDiscoveryDetail(
                    null, List.of("expanded").listIterator()));
    assertEquals(ProtocolOptions.DETAIL, invalidException.argument());
    assertTrue(
        Objects.requireNonNull(invalidException.getMessage())
            .contains("Accepted values: minimal, compact, full."));
  }
}
