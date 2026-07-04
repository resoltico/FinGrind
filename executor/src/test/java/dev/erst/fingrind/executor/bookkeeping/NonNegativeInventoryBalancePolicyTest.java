package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Direct unit coverage for non-negative inventory helper branches. */
class NonNegativeInventoryBalancePolicyTest {
  private static final NonNegativeInventoryBalancePolicy POLICY =
      new NonNegativeInventoryBalancePolicy();

  @Test
  void rejectionFor_ignoresUndeclaredAndNonInventoryAccounts() {
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(cash.accountCode(), cash);

    Optional<BookkeepingPostingRejection> rejection =
        POLICY.rejectionFor(
            postingCommand(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("9999", JournalLine.EntrySide.CREDIT, "10.00")))),
            book.accounts,
            book);

    assertEquals(Optional.empty(), rejection);
  }

  @Test
  void signedBalanceSide_handlesDebitCreditAndZero() {
    assertEquals(BalanceSide.DEBIT, invokeSignedBalanceSide(1L));
    assertEquals(BalanceSide.CREDIT, invokeSignedBalanceSide(-1L));
    assertEquals(BalanceSide.ZERO, invokeSignedBalanceSide(0L));
  }

  @Test
  void field_namesTypedAndFallbackInventoryAttributes() {
    BookkeepingEntry.SaleSettled saleSettled =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "7000"),
            null,
            null,
            null,
            null);
    BookkeepingEntry.SaleOnCredit saleOnCredit =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1200"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "7000"),
            null,
            null,
            null);
    BookkeepingEntry.PurchaseSettled purchaseSettled =
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null);
    BookkeepingEntry.OpeningPosition openingPosition =
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1400"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000")),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("3000"),
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"))));
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("fix inventory")),
            null,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    line("1400", JournalLine.EntrySide.CREDIT, "5.00"),
                    line("5000", JournalLine.EntrySide.DEBIT, "5.00"))));

    assertEquals("inventoryRelief.amount", invokeField(saleSettled));
    assertEquals("inventoryRelief.amount", invokeField(saleOnCredit));
    assertEquals("lines[].amount", invokeField(purchaseSettled));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2000"),
                new MonetaryAmount("EUR", "1000"))));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.DirectJournal(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("1400", JournalLine.EntrySide.DEBIT, "10.00"),
                        line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1200"),
                new MonetaryAmount("EUR", "1000"),
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null)));
    assertEquals(
        "lines[].amount",
        invokeField(
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null)));
    assertEquals("openingBalances[].amount", invokeField(openingPosition));
    assertEquals("reversal.priorPostingId", invokeField(reversal));
    assertEquals("inventoryRelief.amount", invokeRequestField(postingCommand(saleSettled)));
    assertEquals("inventoryRelief.amount", invokeRequestField(postingCommand(saleOnCredit)));
    assertEquals("lines[].amount", invokeRequestField(postingCommand(purchaseSettled)));
    assertEquals("openingBalances[].amount", invokeRequestField(postingCommand(openingPosition)));
    assertEquals("reversal.priorPostingId", invokeRequestField(postingCommand(reversal)));
    assertEquals(
        "lines[].amount",
        invokeRequestField(
            postingCommand(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        line("1400", JournalLine.EntrySide.CREDIT, "5.00"),
                        line("5000", JournalLine.EntrySide.DEBIT, "5.00"))))));
  }

  private static PostingCommand postingCommand(JournalEntry journalEntry) {
    return new PostingCommand(
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        journalEntry,
        PostingLineageModel.direct(),
        accountingEvidence("idem-policy"),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-policy"),
            new IdempotencyKey("idem-policy"),
            new CausationId("cause-policy"),
            Optional.of(new CorrelationId("corr-policy"))),
        SourceChannel.CLI);
  }

  private static PostingCommand postingCommand(BookkeepingEntry entry) {
    return new PostingCommand(
        entry.postingKind(),
        entry.postingOriginKind(),
        entry.journalEntry(),
        BookkeepingPublishedLanguageTranslator.fromPublished(entry.postingLineage()),
        accountingEvidence("idem-policy-entry"),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-policy-entry"),
            new IdempotencyKey("idem-policy-entry"),
            new CausationId("cause-policy-entry"),
            Optional.of(new CorrelationId("corr-policy-entry"))),
        SourceChannel.CLI,
        entry);
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode), side, dev.erst.fingrind.core.Money.parse("EUR", amount));
  }

  private static BalanceSide invokeSignedBalanceSide(long signedMinorUnits) {
    try {
      MethodHandle method =
          MethodHandles.privateLookupIn(
                  NonNegativeInventoryBalancePolicy.class, MethodHandles.lookup())
              .findStatic(
                  NonNegativeInventoryBalancePolicy.class,
                  "signedBalanceSide",
                  MethodType.methodType(BalanceSide.class, long.class));
      return (BalanceSide) method.invokeExact(signedMinorUnits);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke signedBalanceSide.", throwable);
    }
  }

  private static String invokeField(BookkeepingEntry entry) {
    try {
      MethodHandle method =
          MethodHandles.privateLookupIn(
                  NonNegativeInventoryBalancePolicy.class, MethodHandles.lookup())
              .findStatic(
                  NonNegativeInventoryBalancePolicy.class,
                  "field",
                  MethodType.methodType(String.class, BookkeepingEntry.class));
      return (String) method.invoke(entry);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke field(BookkeepingEntry).", throwable);
    }
  }

  private static String invokeRequestField(PostingRequestModel postingRequest) {
    try {
      MethodHandle method =
          MethodHandles.privateLookupIn(
                  NonNegativeInventoryBalancePolicy.class, MethodHandles.lookup())
              .findStatic(
                  NonNegativeInventoryBalancePolicy.class,
                  "field",
                  MethodType.methodType(String.class, PostingRequestModel.class));
      return (String) method.invoke(postingRequest);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke field(PostingRequestModel).", throwable);
    }
  }

  /** Minimal validation-store double for direct inventory-policy tests. */
  private static final class RecordingValidationBook implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"));
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      throw new AssertionError("Policy test passes declared accounts directly.");
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
