package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins constructor invariants for package-private CLI JSON transport models. */
class CliJsonModelValidationTest {
  @Test
  void responseModels_trimTextAndRejectBlankValues() {
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        new CliEnvelopeJsonModels.RejectedEnvelope(
            " rejected ",
            " query-book-not-initialized ",
            " The book is not initialized. ",
            " idem-1 ",
            null);

    assertEquals("rejected", envelope.status());
    assertEquals("query-book-not-initialized", envelope.code());
    assertEquals("The book is not initialized.", envelope.message());
    assertEquals("idem-1", envelope.idempotencyKey());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliEnvelopeJsonModels.RejectedEnvelope(" ", "code", "message", null, null));
  }

  @Test
  void validationHelpers_coverNullAndFailingNumericBranches() {
    assertEquals(List.of(), CliJsonModelValidation.copyList(null));
    assertNull(CliJsonModelValidation.requireOptionalText(null, "hint"));
    assertThrows(
        IllegalArgumentException.class, () -> CliJsonModelValidation.requirePositive(0, "limit"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliJsonModelValidation.requireNonNegative(-1, "offset"));
  }

  @Test
  void planAndRejectionPayloads_rejectEmptyRequiredLists() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliPlanJsonModels.GroupLedgerFactPayload("group", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliPlanJsonModels.LedgerExecutionJournalPayload("start", "finish", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.AccountStateViolationsDetails(List.of()));
  }

  @Test
  void cliFailure_normalizesTextAndRejectsBlankFields() {
    CliFailure failure = new CliFailure(" invalid-request ", " Message ", null, " --limit ");

    assertEquals("invalid-request", failure.code());
    assertEquals("Message", failure.message());
    assertEquals("--limit", failure.argument());
    assertThrows(IllegalArgumentException.class, () -> new CliFailure(" ", "message", null, null));
    assertThrows(IllegalArgumentException.class, () -> new CliFailure("code", " ", null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("code", "message", " ", null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("code", "message", null, " "));
  }

  @Test
  void parsedBookArguments_coalesceNullCommandArguments() {
    CliBookArgumentSupport.ParsedBookArguments parsedBookArguments =
        new CliBookArgumentSupport.ParsedBookArguments(
            new BookAccess(
                Path.of("book.sqlite"), BookAccess.PassphraseSource.StandardInput.INSTANCE),
            null,
            null);

    assertEquals(List.of(), parsedBookArguments.commandArguments());
    assertEquals(Path.of("book.sqlite"), parsedBookArguments.bookAccess().bookFilePath());
    assertTrue(parsedBookArguments.optionalRequestFile().isEmpty());
  }
}
