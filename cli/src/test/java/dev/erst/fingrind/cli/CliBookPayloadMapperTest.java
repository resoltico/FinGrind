package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliCloseTargetReadinessPayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused regression tests for {@link CliBookPayloadMapper}. */
class CliBookPayloadMapperTest extends FinGrindCliTestSupport {
  @Test
  void bookAndPostingContextPayloads_mapIdentityAndSelectedFilters() {
    CliBookQueryJsonModels.BookContextPayload bookContext =
        CliBookPayloadMapper.bookContextPayload(bookIdentity());
    CliBookQueryJsonModels.PostingQueryContextPayload unbounded =
        CliBookPayloadMapper.postingQueryContextPayload(bookIdentity(), null, null, null);
    CliBookQueryJsonModels.PostingQueryContextPayload filtered =
        CliBookPayloadMapper.postingQueryContextPayload(
            bookIdentity(),
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"));

    assertEquals("Acme Studio", bookContext.bookIdentity().entityName());
    assertEquals(
        "internal-management-bookkeeping-kernel",
        bookContext.bookIdentity().accountingKernelProfile());
    assertEquals("CASH", bookContext.bookIdentity().accountingBasis());
    assertEquals(
        "NON_STATUTORY_INTERNAL_MANAGEMENT",
        bookContext.bookIdentity().accountingFrameworkPosition());

    assertNull(unbounded.accountCodeFilter());
    assertNull(unbounded.effectiveDateFrom());
    assertEquals("book-start", unbounded.effectiveDateFromMeaning());
    assertNull(unbounded.effectiveDateTo());
    assertEquals("current-book-horizon", unbounded.effectiveDateToMeaning());

    assertEquals("1000", filtered.accountCodeFilter());
    assertEquals("2026-04-01", filtered.effectiveDateFrom());
    assertEquals("selected-date", filtered.effectiveDateFromMeaning());
    assertEquals("2026-04-30", filtered.effectiveDateTo());
    assertEquals("selected-date", filtered.effectiveDateToMeaning());
  }

  @Test
  void bookIdentityPayload_mapsDoctrineFields() {
    BookIdentity doctrinalIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));

    var payload = CliBookPayloadMapper.bookIdentityPayload(doctrinalIdentity);

    assertEquals("internal-management-bookkeeping-kernel", payload.accountingKernelProfile());
    assertEquals("CASH", payload.accountingBasis());
    assertEquals("NON_STATUTORY_INTERNAL_MANAGEMENT", payload.accountingFrameworkPosition());
    assertEquals("OWNER_MANAGED_SINGLE_ENTITY", payload.entityForm());
    assertEquals("OWNER_MANAGED_SERVICE", payload.bookTemplateId());
    assertNull(payload.inventoryCostingDoctrine());

    var tradingPayload = CliBookPayloadMapper.bookIdentityPayload(tradingBookIdentity());

    assertEquals("WEIGHTED_AVERAGE", tradingPayload.inventoryCostingDoctrine());
  }

  @Test
  void evidencePayload_mapsApprovalEvidence() {
    CliBookQueryJsonModels.AccountingEvidencePayload payload =
        CliBookPayloadMapper.evidencePayload(CliFixtureSupport.accountingEvidenceWithApproval("1"));

    assertEquals(1, payload.sourceDocuments().size());
    assertEquals("document-1", payload.sourceDocuments().get(0).sourceDocumentId());
    assertEquals("cash-receipt", payload.sourceDocuments().get(0).sourceDocumentType());
    assertEquals(1, payload.approvals().size());
    assertEquals("approval-1", payload.approvals().get(0).approvalId());
    assertEquals("manager-signoff", payload.approvals().get(0).approvalType());
  }

  @Test
  void postingSummaryPayload_mapsApprovalIdsWhenPresent() {
    CliBookQueryJsonModels.PostingSummaryPayload payload =
        CliBookPayloadMapper.postingSummaryPayload(
            CliResponseWriterTestSupport.postingFactWithApproval());

    assertEquals(List.of("document-idem-1"), payload.sourceDocumentIds());
    assertEquals(List.of("approval-idem-1"), payload.approvalIds());
  }

  @Test
  void postingDetailsPayload_mapsCallerAuthoredSaleEntryFacts() {
    CliBookQueryJsonModels.PostingPayload payload =
        CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), salePostingFact()).posting();
    CliPostingEntryPayload entry = payload.entry();
    assertNotNull(entry);
    var amount = entry.amount();
    assertNotNull(amount);

    assertEquals("SALE_SETTLED", entry.entryKind());
    assertEquals("cash", entry.cashAccountCode());
    assertEquals("service-revenue", entry.revenueAccountCode());
    assertEquals("1000", amount.minorUnits());
    assertNull(entry.openingBalances());
    assertNull(entry.reversal());
  }

  @Test
  void accountPayload_mapsInventoryUnitOfMeasure() {
    DeclaredAccount inventoryAccount =
        inventoryDeclaredAccount(
            "1400", "Inventory", "kg", 3, true, Instant.parse("2026-04-23T10:15:30Z"));

    CliBookQueryJsonModels.DeclaredAccountPayload payload =
        CliBookPayloadMapper.accountPayload(inventoryAccount);

    assertNotNull(payload.unitOfMeasure());
    assertEquals("kg", payload.unitOfMeasure().token());
    assertEquals(3, payload.unitOfMeasure().quantityScale());
  }

  @Test
  void closeTargetReadinessPayload_mapsSelectedAndCandidateAccountFacts() {
    CliCloseTargetReadinessPayload readyPayload =
        CliBookInspectionPayloadMapper.closeTargetReadinessPayload(
            readyCloseTarget(
                FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")));
    CliCloseTargetReadinessPayload ambiguousPayload =
        CliBookInspectionPayloadMapper.closeTargetReadinessPayload(
            blockedCloseTarget(
                FinancialPositionLineClassification.RESULT_HOLDING,
                "close-target-account-candidate-ambiguous",
                "More than one active declared result-holding account satisfies required classification 'RESULT_HOLDING': 3200, 3210.",
                List.of(new AccountCode("3200"), new AccountCode("3210"))));

    assertEquals("3200", readyPayload.accountCode());
    assertNull(ambiguousPayload.accountCode());
    assertEquals(List.of("3200", "3210"), ambiguousPayload.candidateAccountCodes());
  }
}
