package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for fiscal-year-close draft generation rules and metadata. */
class FiscalYearCloseDraftFactoryTest {
  private static final Instant CLOSED_AT = Instant.parse("2026-12-31T23:59:59Z");
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final MethodHandle NATURAL_SIGNED_MINOR_UNITS =
      privateStaticHelper(
          "naturalSignedMinorUnits",
          MethodType.methodType(
              long.class, RegisteredAccount.class, JournalLine.EntrySide.class, long.class));

  private final FiscalYearCloseDraftFactory draftFactory = new FiscalYearCloseDraftFactory();

  @Test
  void withdrawalCloseDrafts_settlesDebitNormalWithdrawalsIntoCapital() {
    RegisteredAccount capital =
        account("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount withdrawals =
        account("3100", "Owner Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
    RegisteredAccount travelDraw =
        account("3101", "Travel Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL);

    List<PostingDraft> drafts =
        draftFactory.withdrawalCloseDrafts(
            FISCAL_YEAR,
            Map.of(
                capital.accountCode(),
                capital,
                withdrawals.accountCode(),
                withdrawals,
                travelDraw.accountCode(),
                travelDraw),
            capital,
            List.of(
                posting(
                    "posting-withdrawal",
                    PostingKind.STANDARD,
                    line("3100", JournalLine.EntrySide.DEBIT, "25.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "25.00")),
                posting(
                    "posting-travel-draw",
                    PostingKind.STANDARD,
                    line("3101", JournalLine.EntrySide.DEBIT, "5.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "5.00")),
                posting(
                    "posting-already-closed",
                    PostingKind.FISCAL_YEAR_CLOSE,
                    line("3100", JournalLine.EntrySide.DEBIT, "9.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "9.00"))),
            CLOSED_AT);

    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3100"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "25.00")),
            new JournalLine(
                new AccountCode("3101"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "5.00")),
            new JournalLine(
                new AccountCode("3000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "30.00"))),
        drafts.getFirst().journalEntry().lines());
    assertEquals(
        "year-end-close-plan",
        drafts.getFirst().evidence().sourceDocuments().getFirst().sourceDocumentType().value());
    assertEquals(SourceChannel.SYSTEM, drafts.getFirst().sourceChannel());
  }

  @Test
  void withdrawalCloseDrafts_returnsEmptyWhenWithdrawalsNetToZero() {
    RegisteredAccount capital =
        account("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount withdrawals =
        account("3100", "Owner Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL);

    List<PostingDraft> drafts =
        draftFactory.withdrawalCloseDrafts(
            FISCAL_YEAR,
            Map.of(capital.accountCode(), capital, withdrawals.accountCode(), withdrawals),
            capital,
            List.of(
                posting(
                    "posting-withdrawal",
                    PostingKind.STANDARD,
                    line("3100", JournalLine.EntrySide.DEBIT, "25.00"),
                    line("1000", JournalLine.EntrySide.CREDIT, "25.00")),
                posting(
                    "posting-refund",
                    PostingKind.STANDARD,
                    line("1000", JournalLine.EntrySide.DEBIT, "25.00"),
                    line("3100", JournalLine.EntrySide.CREDIT, "25.00"))),
            CLOSED_AT);

    assertEquals(List.of(), drafts);
  }

  @Test
  void withdrawalCloseDrafts_creditsCapitalForCreditBalancedWithdrawals() {
    RegisteredAccount capital =
        account("3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    RegisteredAccount withdrawals =
        account("3100", "Owner Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL);

    List<PostingDraft> drafts =
        draftFactory.withdrawalCloseDrafts(
            FISCAL_YEAR,
            Map.of(capital.accountCode(), capital, withdrawals.accountCode(), withdrawals),
            capital,
            List.of(
                posting(
                    "posting-overcorrection",
                    PostingKind.STANDARD,
                    line("1000", JournalLine.EntrySide.DEBIT, "9.00"),
                    line("3100", JournalLine.EntrySide.CREDIT, "9.00"))),
            CLOSED_AT);

    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3100"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "9.00")),
            new JournalLine(
                new AccountCode("3000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "9.00"))),
        drafts.getFirst().journalEntry().lines());
  }

  @Test
  void retainedAccumulationDrafts_creditTargetForProfitAndUsePlannedSweepDrafts() {
    RegisteredAccount resultHolding =
        account("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        account(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);
    PostingDraft plannedSweepDraft =
        RequestFingerprintTestSupport.fingerprintedDraft(
            new JournalEntry(
                LocalDate.parse("2026-12-31"),
                List.of(
                    line("4000", JournalLine.EntrySide.DEBIT, "75.00"),
                    line("3200", JournalLine.EntrySide.CREDIT, "75.00"))),
            PostingLineageModel.direct(),
            PostingKind.INTERIM_RESULT_SWEEP,
            dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
            dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence(
                "planned-sweep", "interim-result-sweep-plan"),
            new CommittedProvenance(
                new RequestProvenance(
                    new CommandId("12cb0f8f-1479-3c08-b6ab-77a05fd51b2c"),
                    new IdempotencyKey("interimResultSweep:planned"),
                    new CausationId("interimResultSweep:planned"),
                    Optional.of(new CorrelationId("interimResultSweep:planned"))),
                CLOSED_AT,
                SourceChannel.SYSTEM));

    List<PostingDraft> drafts =
        draftFactory.retainedAccumulationDrafts(
            FISCAL_YEAR,
            resultHolding,
            retainedAccumulated,
            List.of(),
            List.of(plannedSweepDraft),
            CLOSED_AT);

    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3200"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "75.00")),
            new JournalLine(
                new AccountCode("3300"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "75.00"))),
        drafts.getFirst().journalEntry().lines());
  }

  @Test
  void retainedAccumulationDrafts_returnsEmptyWhenResultHoldingNetToZero() {
    RegisteredAccount resultHolding =
        account("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        account(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);

    List<PostingDraft> drafts =
        draftFactory.retainedAccumulationDrafts(
            FISCAL_YEAR,
            resultHolding,
            retainedAccumulated,
            List.of(
                posting(
                    "posting-profit",
                    PostingKind.STANDARD,
                    line("3200", JournalLine.EntrySide.CREDIT, "12.00"),
                    line("4000", JournalLine.EntrySide.DEBIT, "12.00")),
                posting(
                    "posting-loss",
                    PostingKind.STANDARD,
                    line("3200", JournalLine.EntrySide.DEBIT, "12.00"),
                    line("5000", JournalLine.EntrySide.CREDIT, "12.00"))),
            List.of(),
            CLOSED_AT);

    assertEquals(List.of(), drafts);
  }

  @Test
  void retainedAccumulationDrafts_debitTargetForLossBalances() {
    RegisteredAccount resultHolding =
        account("3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING);
    RegisteredAccount retainedAccumulated =
        account(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);

    List<PostingDraft> drafts =
        draftFactory.retainedAccumulationDrafts(
            FISCAL_YEAR,
            resultHolding,
            retainedAccumulated,
            List.of(
                posting(
                    "posting-loss",
                    PostingKind.STANDARD,
                    line("3200", JournalLine.EntrySide.DEBIT, "12.00"),
                    line("5000", JournalLine.EntrySide.CREDIT, "12.00"))),
            List.of(),
            CLOSED_AT);

    assertEquals(
        List.of(
            new JournalLine(
                new AccountCode("3200"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "12.00")),
            new JournalLine(
                new AccountCode("3300"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "12.00"))),
        drafts.getFirst().journalEntry().lines());
  }

  @Test
  void naturalSignedMinorUnits_rejectsNegativeAmounts() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                naturalSignedMinorUnits(
                    account(
                        "3200",
                        "Result Holding",
                        FinancialPositionLineClassification.RESULT_HOLDING),
                    JournalLine.EntrySide.CREDIT,
                    -1L));

    assertEquals("amountMinor must not be negative.", failure.getMessage());
  }

  private static RegisteredAccount account(
      String accountCode, String accountName, FinancialPositionLineClassification classification) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.EQUITY,
        financialPositionTaxonomy(classification),
        true,
        CLOSED_AT);
  }

  private static CommittedPosting posting(
      String postingId, PostingKind postingKind, JournalLine... lines) {
    return new CommittedPosting(
        new dev.erst.fingrind.core.PostingId(java.util.UUID.nameUUIDFromBytes(("fingrind-test-postingid:" + postingId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()),
        new JournalEntry(LocalDate.parse("2026-12-31"), List.of(lines)),
        PostingLineageModel.direct(),
        postingKind,
        postingKind == PostingKind.FISCAL_YEAR_CLOSE
            ? dev.erst.fingrind.core.PostingOriginKind.FISCAL_YEAR_CLOSE
            : dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence(postingId),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.empty()),
            CLOSED_AT,
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  private static long naturalSignedMinorUnits(
      RegisteredAccount account, JournalLine.EntrySide balanceSide, long amountMinor) {
    try {
      return (long) NATURAL_SIGNED_MINOR_UNITS.invokeExact(account, balanceSide, amountMinor);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke fiscal-year-close helper.", throwable);
    }
  }

  private static MethodHandle privateStaticHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(FiscalYearCloseDraftFactory.class, MethodHandles.lookup());
      return lookup.findStatic(FiscalYearCloseDraftFactory.class, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind fiscal-year-close helper: " + methodName, exception);
    }
  }
}
