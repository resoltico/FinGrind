package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
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
        BookkeepingAccountSemanticsViolations.accountTypeMismatch(
            "entryKind",
            BookkeepingEntryKind.SALE_SETTLED.wireValue(),
            "cashAccountCode",
            new AccountCode("2000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    BookkeepingPostingRejection.EntrySemanticsViolation classificationMismatch =
        BookkeepingAccountSemanticsViolations.financialPositionClassificationMismatch(
            "entryKind",
            BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue(),
            "equityAccountCode",
            new AccountCode("3200"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            null);
    BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted =
        BookkeepingEvidenceSemanticsViolations.sourceDocumentTypeNotAccepted(
            "entryKind",
            BookkeepingEntryKind.EXPENSE_SETTLED.wireValue(),
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));
    BookkeepingPostingRejection.EntrySemanticsViolation economicNullJournal =
        BookkeepingEntryModeSemanticsViolations.economicNullJournal(
            "entryKind", BookkeepingEntryKind.DIRECT_JOURNAL.wireValue());
    BookkeepingPostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine =
        BookkeepingEntryModeSemanticsViolations.rawJournalRequiresCashLine(
            "entryKind", BookkeepingEntryKind.DIRECT_JOURNAL.wireValue());
    BookkeepingPostingRejection.EntrySemanticsViolation accountRoleMismatch =
        BookkeepingAccountSemanticsViolations.accountRoleMismatch(
            "entryKind",
            BookkeepingEntryKind.RECEIPT.wireValue(),
            "settlementAdjunct.accountCode",
            new AccountCode("6100"),
            AccountRole.SETTLEMENT_ADJUNCT,
            AccountRole.EXPENSE);
    BookkeepingPostingRejection.EntrySemanticsViolation evidenceClassConflict =
        BookkeepingEvidenceSemanticsViolations.evidenceClassConflict(
            "entryKind",
            BookkeepingEntryKind.SALE_SETTLED.wireValue(),
            EvidenceClass.INVOICE,
            EconomicEventClass.SETTLED_SALE);

    assertEquals("account-type-mismatch", accountTypeMismatch.code());
    assertEquals("cashAccountCode", accountTypeMismatch.field());
    assertEquals(
        "entryKind 'SALE_SETTLED' requires cashAccountCode '2000' to be account type 'ASSET', but the declared account type is 'REVENUE'.",
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
        "entryKind 'EXPENSE_SETTLED' does not accept evidence.sourceDocuments[].sourceDocumentType 'invoice'. Accepted values: expense-receipt, cash-disbursement.",
        sourceDocumentTypeNotAccepted.message());
    assertEquals("economic-null-journal", economicNullJournal.code());
    assertEquals("lines", economicNullJournal.field());
    assertEquals(
        "entryKind 'DIRECT_JOURNAL' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement.",
        economicNullJournal.message());
    assertEquals("raw-journal-requires-cash-line", rawJournalRequiresCashLine.code());
    assertEquals("lines[].accountCode", rawJournalRequiresCashLine.field());
    assertEquals(
        "entryKind 'DIRECT_JOURNAL' is an adjustment on a cash-basis book, so at least one journal line must use a declared cash account.",
        rawJournalRequiresCashLine.message());
    assertEquals("account-role-mismatch", accountRoleMismatch.code());
    assertEquals("settlementAdjunct.accountCode", accountRoleMismatch.field());
    assertEquals(
        "entryKind 'RECEIPT' requires settlementAdjunct.accountCode '6100' to resolve to accountRole 'SETTLEMENT_ADJUNCT', but the declared account resolves to 'EXPENSE'.",
        accountRoleMismatch.message());
    assertEquals("evidence-class-conflict", evidenceClassConflict.code());
    assertEquals("evidence.sourceDocuments[].sourceDocumentType", evidenceClassConflict.field());
    assertEquals(
        "entryKind 'SALE_SETTLED' resolves to eventClass 'SETTLED_SALE', but the evidence resolves to evidenceClass 'INVOICE'.",
        evidenceClassConflict.message());
    assertNull(
        new BookkeepingPostingRejection.EntrySemanticsViolation("code", null, "message").field());
  }

  @Test
  void entrySemanticsFactoriesSupportCanonicalEntryKindsAndReferencedAccountSets() {
    BookkeepingPostingRejection.EntrySemanticsViolation accountTypeMismatch =
        BookkeepingAccountSemanticsViolations.accountTypeMismatch(
            "entryKind",
            BookkeepingEntryKind.SALE_SETTLED.wireValue(),
            "cashAccountCode",
            new AccountCode("2000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    BookkeepingPostingRejection.EntrySemanticsViolation classificationMismatch =
        BookkeepingAccountSemanticsViolations.financialPositionClassificationMismatch(
            "entryKind",
            BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue(),
            "equityAccountCode",
            new AccountCode("3200"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            FinancialPositionLineClassification.OTHER_EQUITY);
    BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted =
        BookkeepingEvidenceSemanticsViolations.sourceDocumentTypeNotAccepted(
            "entryKind",
            BookkeepingEntryKind.SALE_SETTLED.wireValue(),
            new SourceDocumentType("invoice"),
            List.of("cash-receipt", "bank-deposit"));
    BookkeepingPostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired =
        BookkeepingAccountSemanticsViolations.distinctRoleAccountsRequired(
            "entryKind",
            BookkeepingEntryKind.SALE_SETTLED.wireValue(),
            "cashAccountCode",
            "revenueAccountCode",
            new AccountCode("1000"));

    assertEquals(
        "entryKind 'SALE_SETTLED' requires cashAccountCode '2000' to be account type 'ASSET', but the declared account type is 'REVENUE'.",
        accountTypeMismatch.message());
    assertEquals(
        "entryKind 'OWNER_WITHDRAWAL' requires equityAccountCode '3200' to use financialPositionLineClassification 'EQUITY_WITHDRAWAL', but the declared account uses 'OTHER_EQUITY'.",
        classificationMismatch.message());
    assertEquals(
        "entryKind 'SALE_SETTLED' does not accept evidence.sourceDocuments[].sourceDocumentType 'invoice'. Accepted values: cash-receipt, bank-deposit.",
        sourceDocumentTypeNotAccepted.message());
    assertEquals(
        "entryKind 'SALE_SETTLED' requires cashAccountCode and revenueAccountCode to reference distinct accounts, but both point to '1000'.",
        distinctRoleAccountsRequired.message());
    assertEquals(
        List.of(new AccountCode("1000"), new AccountCode("2000")),
        List.copyOf(
            BookkeepingAccountSemanticsViolations.referencedAccountSet(
                new AccountCode("1000"), new AccountCode("2000"), new AccountCode("1000"))));

    NullPointerException nullAccountCode =
        assertThrows(
            NullPointerException.class,
            () ->
                BookkeepingAccountSemanticsViolations.referencedAccountSet(
                    new AccountCode("1000"), nullOf(), new AccountCode("2000")));
    assertEquals("accountCode", nullAccountCode.getMessage());
  }

  @Test
  void inventoryBalanceBelowZeroViolation_validatesFieldAndMoneyInvariants() {
    assertEquals(
        new InventoryBalanceBelowZeroViolation(
            new AccountCode("1400"),
            "inventoryRelief.amount",
            LocalDate.parse("2026-04-07"),
            BalanceSide.DEBIT,
            Money.parse("EUR", "10.00"),
            Money.parse("EUR", "50.00"),
            Money.parse("EUR", "40.00")),
        new InventoryBalanceBelowZeroViolation(
            new AccountCode("1400"),
            "inventoryRelief.amount",
            LocalDate.parse("2026-04-07"),
            BalanceSide.DEBIT,
            Money.parse("EUR", "10.00"),
            Money.parse("EUR", "50.00"),
            Money.parse("EUR", "40.00")));
    assertDoesNotThrow(
        () ->
            new InventoryBalanceBelowZeroViolation(
                new AccountCode("1400"),
                "inventoryRelief.amount",
                LocalDate.parse("2026-04-07"),
                BalanceSide.ZERO,
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "50.00"),
                Money.parse("EUR", "40.00")));
    assertDoesNotThrow(
        () ->
            new InventoryBalanceBelowZeroViolation(
                new AccountCode("1400"),
                "inventoryRelief.amount",
                LocalDate.parse("2026-04-07"),
                BalanceSide.CREDIT,
                Money.parse("EUR", "10.00"),
                Money.parse("EUR", "50.00"),
                Money.parse("EUR", "40.00")));

    assertEquals(
        "field must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        " ",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.DEBIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00")))
            .getMessage());
    assertEquals(
        "field must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        nullOf(),
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.DEBIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00")))
            .getMessage());
    assertEquals(
        "requestedDecreaseAmount must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.DEBIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "0.00"),
                        Money.parse("EUR", "40.00")))
            .getMessage());
    assertEquals(
        "resultingCreditBalance must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.DEBIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "0.00")))
            .getMessage());
    assertEquals(
        "currentNetAmount must be zero when currentBalanceSide is ZERO.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.ZERO,
                        Money.parse("EUR", "1.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00")))
            .getMessage());
    assertEquals(
        "currentNetAmount must be positive when currentBalanceSide is not ZERO.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryBalanceBelowZeroViolation(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.CREDIT,
                        Money.parse("EUR", "0.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "40.00")))
            .getMessage());
  }
}
