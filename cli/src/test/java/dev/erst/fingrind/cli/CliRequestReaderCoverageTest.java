package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.JournalEntryValidationException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Direct coverage tests for request-reader failure translation. */
class CliRequestReaderCoverageTest {
  @Test
  void invalidRequestFromValueFailure_routesLedgerPlanFailuresThroughLedgerPlanHints() {
    CliFailure failure =
        CliRequestReader.invalidRequestFromValueFailure(
                new IllegalArgumentException("Missing required field: query.effectiveDateFrom"),
                "fallback",
                null)
            .failure();

    assertEquals(ContractErrors.Descriptor.INVALID_REQUEST.code(), failure.code());
    assertTrue(Objects.requireNonNull(failure.hint()).contains("ledger plan document"));
    assertTrue(
        Objects.requireNonNull(failure.hint())
            .contains(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
  }

  @Test
  void invalidRequestFromJournalValidation_keepsStructuredDetailsForLedgerPlanFailures() {
    CliFailure failure =
        CliRequestReader.invalidRequestFromJournalValidation(
                new JournalEntryValidationException(
                    List.of(
                        "Expected one canonical YYYY-MM-DD local date for query.effectiveDateFrom.")),
                "fallback",
                null)
            .failure();

    assertEquals(ContractErrors.Descriptor.INVALID_REQUEST.code(), failure.code());
    assertInstanceOf(
        dev.erst.fingrind.cli.json.CliErrorJsonModels.InvalidRequestDetails.class,
        failure.details());
    assertEquals("fallback", Objects.requireNonNull(failure.hint()));
  }

  @Test
  void invalidRequestFromJournalValidation_routesPostingFailuresThroughActionFirstRequestHints() {
    CliFailure failure =
        invalidPostingJournalFailure(
            new JournalEntryValidationException(
                List.of("entry.journalEntry.lines must contain at least one line")));

    assertEquals(ContractErrors.Descriptor.INVALID_REQUEST.code(), failure.code());
    assertTrue(Objects.requireNonNull(failure.hint()).contains("print-request-template"));
    assertTrue(Objects.requireNonNull(failure.hint()).contains("balanced journal line"));
  }

  private static CliFailure invalidPostingJournalFailure(
      JournalEntryValidationException exception) {
    return CliRequestReader.invalidRequestFromJournalValidation(
            exception,
            CliJsonRequestHints.postEntryRequestHint(OperationId.POST_ENTRY),
            OperationId.POST_ENTRY)
        .failure();
  }
}
