package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
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
  void openingPositionWindowClosed_requiresBothBlockingFacts() throws Exception {
    InvocationTargetException nullPostingKind =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningPositionWindowClosed.class
                    .getDeclaredConstructor(PostingKind.class, LocalDate.class)
                    .newInstance(null, LocalDate.parse("2026-04-07")));
    InvocationTargetException nullEffectiveDate =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningPositionWindowClosed.class
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
        BookkeepingEntrySemanticsViolationFactory.accountTypeMismatch(
            "entryKind",
            BookkeepingEntryKind.SALE.wireValue(),
            "cashAccountCode",
            new AccountCode("2000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    BookkeepingPostingRejection.EntrySemanticsViolation classificationMismatch =
        BookkeepingEntrySemanticsViolationFactory.financialPositionClassificationMismatch(
            "entryKind",
            BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue(),
            "equityAccountCode",
            new AccountCode("3200"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            null);
    BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted =
        BookkeepingEntrySemanticsViolationFactory.sourceDocumentTypeNotAccepted(
            "entryKind",
            BookkeepingEntryKind.EXPENSE.wireValue(),
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));
    BookkeepingPostingRejection.EntrySemanticsViolation economicNullJournal =
        BookkeepingEntrySemanticsViolationFactory.economicNullJournal(
            "entryKind", BookkeepingEntryKind.DIRECT_JOURNAL.wireValue());
    BookkeepingPostingRejection.EntrySemanticsViolation cashBasisAccountRequired =
        BookkeepingEntrySemanticsViolationFactory.cashBasisAccountRequired(
            "entryKind",
            BookkeepingEntryKind.DIRECT_JOURNAL.wireValue(),
            List.of(new AccountCode("3000"), new AccountCode("3200")));

    assertEquals("account-type-mismatch", accountTypeMismatch.code());
    assertEquals("cashAccountCode", accountTypeMismatch.field());
    assertEquals(
        "entryKind 'SALE' requires cashAccountCode '2000' to be account type 'ASSET', but the declared account type is 'REVENUE'.",
        accountTypeMismatch.message());
    assertEquals("financial-position-classification-mismatch", classificationMismatch.code());
    assertEquals("equityAccountCode", classificationMismatch.field());
    assertEquals(
        "entryKind 'OWNER_WITHDRAWAL' requires equityAccountCode '3200' to use financialPositionLineClassification 'EQUITY_WITHDRAWAL', but the declared account uses '<absent>'.",
        classificationMismatch.message());
    assertEquals("source-document-type-not-accepted", sourceDocumentTypeNotAccepted.code());
    assertEquals(
        "evidence.sourceDocuments[].sourceDocumentType", sourceDocumentTypeNotAccepted.field());
    assertEquals(
        "entryKind 'EXPENSE' does not accept evidence.sourceDocuments[].sourceDocumentType 'invoice'. Accepted values: expense-receipt, cash-disbursement.",
        sourceDocumentTypeNotAccepted.message());
    assertEquals("economic-null-journal", economicNullJournal.code());
    assertEquals("lines", economicNullJournal.field());
    assertEquals(
        "entryKind 'DIRECT_JOURNAL' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement.",
        economicNullJournal.message());
    assertEquals("cash-basis-account-required", cashBasisAccountRequired.code());
    assertEquals("lines[].accountCode", cashBasisAccountRequired.field());
    assertEquals(
        "entryKind 'DIRECT_JOURNAL' requires at least one lines[].accountCode to reference a declared cash-and-cash-equivalent asset account, but the request references only '3000', '3200'.",
        cashBasisAccountRequired.message());
    assertNull(
        new BookkeepingPostingRejection.EntrySemanticsViolation("code", null, "message").field());
  }

  @Test
  void cashBasisAccountRequired_rejectsEmptyReferencedAccountLists() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BookkeepingEntrySemanticsViolationFactory.cashBasisAccountRequired(
                    "entryKind", BookkeepingEntryKind.DIRECT_JOURNAL.wireValue(), List.of()));

    assertEquals("referencedAccountCodes must contain at least one item.", exception.getMessage());
  }

  @Test
  void entrySemanticsFactoriesSupportCanonicalEntryKindsAndReferencedAccountSets() {
    BookkeepingPostingRejection.EntrySemanticsViolation accountTypeMismatch =
        BookkeepingEntrySemanticsViolationFactory.accountTypeMismatch(
            "entryKind",
            BookkeepingEntryKind.SALE.wireValue(),
            "cashAccountCode",
            new AccountCode("2000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    BookkeepingPostingRejection.EntrySemanticsViolation classificationMismatch =
        BookkeepingEntrySemanticsViolationFactory.financialPositionClassificationMismatch(
            "entryKind",
            BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue(),
            "equityAccountCode",
            new AccountCode("3200"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            FinancialPositionLineClassification.OTHER_EQUITY);
    BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted =
        BookkeepingEntrySemanticsViolationFactory.sourceDocumentTypeNotAccepted(
            "entryKind",
            BookkeepingEntryKind.SALE.wireValue(),
            new SourceDocumentType("invoice"),
            List.of("cash-receipt", "bank-deposit"));
    BookkeepingPostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired =
        BookkeepingEntrySemanticsViolationFactory.distinctRoleAccountsRequired(
            "entryKind",
            BookkeepingEntryKind.SALE.wireValue(),
            "cashAccountCode",
            "revenueAccountCode",
            new AccountCode("1000"));

    assertEquals(
        "entryKind 'SALE' requires cashAccountCode '2000' to be account type 'ASSET', but the declared account type is 'REVENUE'.",
        accountTypeMismatch.message());
    assertEquals(
        "entryKind 'OWNER_WITHDRAWAL' requires equityAccountCode '3200' to use financialPositionLineClassification 'EQUITY_WITHDRAWAL', but the declared account uses 'OTHER_EQUITY'.",
        classificationMismatch.message());
    assertEquals(
        "entryKind 'SALE' does not accept evidence.sourceDocuments[].sourceDocumentType 'invoice'. Accepted values: cash-receipt, bank-deposit.",
        sourceDocumentTypeNotAccepted.message());
    assertEquals(
        "entryKind 'SALE' requires cashAccountCode and revenueAccountCode to reference distinct accounts, but both point to '1000'.",
        distinctRoleAccountsRequired.message());
    assertEquals(
        List.of(new AccountCode("1000"), new AccountCode("2000")),
        List.copyOf(
            BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
                new AccountCode("1000"), new AccountCode("2000"), new AccountCode("1000"))));

    NullPointerException nullAccountCode =
        assertThrows(
            NullPointerException.class,
            () ->
                BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
                    new AccountCode("1000"), nullOf(), new AccountCode("2000")));
    assertEquals("accountCode", nullAccountCode.getMessage());
  }
}
