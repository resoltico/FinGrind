package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Constructor guards for local bookkeeping posting rejections. */
class BookkeepingPostingRejectionTest {
  @Test
  void openingBalanceWindowClosed_requiresBothBlockingFacts() throws Exception {
    InvocationTargetException nullPostingKind =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningBalanceWindowClosed.class
                    .getDeclaredConstructor(PostingKind.class, LocalDate.class)
                    .newInstance(null, LocalDate.parse("2026-04-07")));
    InvocationTargetException nullEffectiveDate =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningBalanceWindowClosed.class
                    .getDeclaredConstructor(PostingKind.class, LocalDate.class)
                    .newInstance(PostingKind.STANDARD, null));

    assertEquals(
        "firstBlockingPostingKind",
        assertInstanceOf(NullPointerException.class, nullPostingKind.getCause()).getMessage());
    assertEquals(
        "firstBlockingEffectiveDate",
        assertInstanceOf(NullPointerException.class, nullEffectiveDate.getCause()).getMessage());
  }

  @Test
  void entrySemanticsPayloadsRejectBlankFieldsAndEmptyLists() throws Exception {
    InvocationTargetException nullCode =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.EntrySemanticsViolation.class
                    .getDeclaredConstructor(String.class, String.class, String.class)
                    .newInstance(null, null, "message"));
    IllegalArgumentException blankCode =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookkeepingPostingRejection.EntrySemanticsViolation(" ", null, "message"));
    IllegalArgumentException blankField =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookkeepingPostingRejection.EntrySemanticsViolation("code", " ", "message"));
    IllegalArgumentException blankMessage =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookkeepingPostingRejection.EntrySemanticsViolation("code", null, " "));
    InvocationTargetException nullMessage =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.EntrySemanticsViolation.class
                    .getDeclaredConstructor(String.class, String.class, String.class)
                    .newInstance("code", null, null));
    IllegalArgumentException emptyViolations =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookkeepingPostingRejection.EntrySemanticsViolations(List.of()));

    assertEquals(
        "Entry semantics violation code must not be blank.",
        assertInstanceOf(IllegalArgumentException.class, nullCode.getCause()).getMessage());
    assertEquals("Entry semantics violation code must not be blank.", blankCode.getMessage());
    assertEquals(
        "Entry semantics violation field must not be blank when present.", blankField.getMessage());
    assertEquals("Entry semantics violation message must not be blank.", blankMessage.getMessage());
    assertEquals(
        "Entry semantics violation message must not be blank.",
        assertInstanceOf(IllegalArgumentException.class, nullMessage.getCause()).getMessage());
    assertEquals(
        "Entry semantics violations must contain at least one issue.",
        emptyViolations.getMessage());
  }

  @Test
  void entrySemanticsFactoriesProduceCanonicalViolationPayloads() {
    BookkeepingPostingRejection.EntrySemanticsViolation accountTypeMismatch =
        BookkeepingPostingRejection.accountTypeMismatch(
            BookkeepingEntryKind.CASH_REVENUE,
            "cashAccountCode",
            new AccountCode("2000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    BookkeepingPostingRejection.EntrySemanticsViolation classificationMismatch =
        BookkeepingPostingRejection.financialPositionClassificationMismatch(
            BookkeepingEntryKind.EQUITY_WITHDRAWAL,
            "equityAccountCode",
            new AccountCode("3200"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            null);
    BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted =
        BookkeepingPostingRejection.sourceDocumentTypeNotAccepted(
            BookkeepingEntryKind.CASH_EXPENSE,
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));

    assertEquals("account-type-mismatch", accountTypeMismatch.code());
    assertEquals("cashAccountCode", accountTypeMismatch.field());
    assertEquals(
        "Entry kind 'CASH_REVENUE' requires cashAccountCode '2000' to be account type 'ASSET', but the declared account type is 'REVENUE'.",
        accountTypeMismatch.message());
    assertEquals("financial-position-classification-mismatch", classificationMismatch.code());
    assertEquals("equityAccountCode", classificationMismatch.field());
    assertEquals(
        "Entry kind 'EQUITY_WITHDRAWAL' requires equityAccountCode '3200' to use financialPositionLineClassification 'EQUITY_WITHDRAWAL', but the declared account uses '<absent>'.",
        classificationMismatch.message());
    assertEquals("source-document-type-not-accepted", sourceDocumentTypeNotAccepted.code());
    assertEquals(
        "evidence.sourceDocuments[].sourceDocumentType", sourceDocumentTypeNotAccepted.field());
    assertEquals(
        "Entry kind 'CASH_EXPENSE' does not accept sourceDocumentType 'invoice'. Accepted values: expense-receipt, cash-disbursement.",
        sourceDocumentTypeNotAccepted.message());
    assertNull(
        new BookkeepingPostingRejection.EntrySemanticsViolation("code", null, "message").field());
  }
}
