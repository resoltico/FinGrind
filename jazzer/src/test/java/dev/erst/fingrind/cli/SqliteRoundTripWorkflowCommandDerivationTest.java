package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowCommandDerivationTest {
  @Test
  void reversal_and_provenance_helpers_cover_edge_paths() {
    var command = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    assertEquals(
        2,
        SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                command.journalEntry().lines())
            .size());
    assertEquals(
        3,
        SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                    new JournalLine(
                        new AccountCode("1100"),
                        JournalLine.EntrySide.DEBIT,
                        new Money(new CurrencyCode("EUR"), new BigDecimal("1.00"))),
                    new JournalLine(
                        new AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        new Money(new CurrencyCode("EUR"), new BigDecimal("11.00")))))
            .size());

    IllegalStateException oneSided =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        oneSided, "at least one line on each side");

    RequestProvenance withCorrelation =
        SqliteRoundTripWorkflowCommandDerivation.derivedRequestProvenance(
            command.requestProvenance(), "derived");
    assertTrue(withCorrelation.correlationId().isPresent());

    RequestProvenance withoutCorrelation =
        SqliteRoundTripWorkflowCommandDerivation.derivedRequestProvenance(
            new RequestProvenance(
                command.requestProvenance().actorId(),
                command.requestProvenance().actorType(),
                command.requestProvenance().commandId(),
                command.requestProvenance().idempotencyKey(),
                command.requestProvenance().causationId(),
                Optional.empty()),
            "derived-no-correlation");
    assertTrue(withoutCorrelation.correlationId().isEmpty());
  }
}
