package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerEntryView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PeriodCloseStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** In-memory book session for tests and non-durable harness composition. */
public final class InMemoryBookSession
    implements BookAdministrationStore,
        BookkeepingReadStore,
        PostingValidationStore,
        PostingCommitStore,
        PeriodCloseStore,
        LedgerPlanTransaction,
        AutoCloseable {
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<AccountCode, RegisteredAccount> accountsByCode = mutableMap();
  private final Map<IdempotencyKey, CommittedPosting> postingsByIdempotencyKey = mutableMap();
  private final Map<PostingId, CommittedPosting> postingsByPostingId = mutableMap();
  private final Map<PostingId, CommittedPosting> reversalsByPriorPostingId = mutableMap();
  private final List<ClosedPeriod> closedPeriods = new ArrayList<>();
  private @Nullable Snapshot transactionSnapshot;
  private boolean initialized;
  private Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
  private BookIdentity bookIdentity =
      new BookIdentity(
          new EntityProfile(
              new BookEntityName("FinGrind Test Entity"),
              EntityForm.COMPANY,
              OwnerModel.MULTI_OWNER,
              ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
              TaxRegistrationStatus.UNSPECIFIED,
              List.of()),
          CurrencyUnit.of("USD"),
          new FiscalYearStart(1, 1),
          AccountingBasis.ACCRUAL);

  @Override
  public BookLifecycleInspection inspectBook() {
    return withLock(
        () -> {
          if (!initialized) {
            return new BookLifecycleInspection.Missing(BookFormatContract.FORMAT_VERSION);
          }
          return new BookLifecycleInspection.Initialized(
              BookFormatContract.APPLICATION_ID,
              BookFormatContract.FORMAT_VERSION,
              BookFormatContract.FORMAT_VERSION,
              initializedAt,
              bookIdentity);
        });
  }

  @Override
  public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    return withLock(
        () -> {
          if (initialized) {
            return new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookAlreadyInitialized());
          }
          initialized = true;
          this.initializedAt = initializedAt;
          this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
          return new BookOpeningOutcome.Opened(initializedAt, bookIdentity);
        });
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return withLock(() -> Optional.ofNullable(accountsByCode.get(accountCode)));
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return withLock(
        () ->
            accountCodes.stream()
                .filter(accountsByCode::containsKey)
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        accountCode -> accountCode, accountsByCode::get)));
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return withLock(
        () -> {
          if (!initialized) {
            return new AccountDeclarationOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          AccountDeclarationOutcome declarationOutcome =
              RegisteredAccount.declare(
                  accountsByCode.get(accountCode),
                  new AccountDeclaration(
                      accountCode, accountName, accountType, accountRole, accountTaxonomy),
                  declaredAt);
          if (declarationOutcome instanceof AccountDeclarationOutcome.Declared declared) {
            accountsByCode.put(accountCode, declared.account());
          }
          return declarationOutcome;
        });
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return withLock(
        () -> {
          List<RegisteredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(Comparator.comparing(account -> account.accountCode().value()))
                  .filter(account -> matchesAccountCursor(account, query.cursor()))
                  .toList();
          int end = Math.min(query.limit(), accounts.size());
          List<RegisteredAccount> pageItems = accounts.subList(0, end);
          return new AccountRegistryPage(
              pageItems,
              query.limit(),
              end < accounts.size()
                  ? Optional.of(new AccountRegistryCursor(pageItems.getLast().accountCode()))
                  : Optional.empty());
        });
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return withLock(() -> Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey)));
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return withLock(() -> Optional.ofNullable(postingsByPostingId.get(postingId)));
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return withLock(() -> Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId)));
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return withLock(
        () ->
            accountsByCode.values().stream()
                .sorted(Comparator.comparing(account -> account.accountCode().value()))
                .toList());
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    return withLock(
        () ->
            postingsByPostingId.values().stream()
                .filter(
                    posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
                .sorted(
                    Comparator.comparing(
                            (CommittedPosting posting) -> posting.journalEntry().effectiveDate())
                        .thenComparing(posting -> posting.provenance().recordedAt())
                        .thenComparing(posting -> posting.postingId().value()))
                .toList());
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    return withLock(
        () ->
            postingsByPostingId.values().stream()
                .map(posting -> posting.journalEntry().effectiveDate())
                .min(Comparator.naturalOrder()));
  }

  @Override
  public Optional<LocalDate> closedThroughEffectiveDate() {
    return withLock(
        () ->
            closedPeriods.stream()
                .map(closedPeriod -> closedPeriod.reportingPeriod().effectiveDateTo())
                .max(Comparator.naturalOrder()));
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return withLock(
        () -> {
          Optional<BookkeepingPostingRejection> rejection =
              PostingAcceptancePolicy.rejectionFor(postingDraft, this);
          if (rejection.isPresent()) {
            return new PostingCommitResult.Rejected(rejection.orElseThrow());
          }
          CommittedPosting postingFact =
              postingDraft.materialize(postingIdGenerator.nextPostingId());
          IdempotencyKey idempotencyKey =
              postingFact.provenance().requestProvenance().idempotencyKey();
          CommittedPosting existingPosting =
              postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
          if (existingPosting != null) {
            return new PostingCommitResult.Rejected(
                new BookkeepingPostingRejection.DuplicateIdempotencyKey());
          }
          postingsByPostingId.put(postingFact.postingId(), postingFact);

          Optional<dev.erst.fingrind.core.ReversalReference> reversalReference =
              postingFact.postingLineage().reversalReference();
          if (reversalReference.isPresent()) {
            dev.erst.fingrind.core.ReversalReference postedReversal =
                reversalReference.orElseThrow();
            PostingId priorPostingId = postedReversal.priorPostingId();
            CommittedPosting existingReversal =
                reversalsByPriorPostingId.putIfAbsent(priorPostingId, postingFact);
            if (existingReversal != null) {
              postingsByIdempotencyKey.remove(idempotencyKey, postingFact);
              postingsByPostingId.remove(postingFact.postingId(), postingFact);
              return new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.ReversalAlreadyExists(priorPostingId));
            }
          }
          return new PostingCommitResult.Committed(postingFact);
        });
  }

  /** Fixture helper that commits one fully materialized posting with its predefined posting id. */
  public PostingCommitResult commit(CommittedPosting postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(),
            postingFact.postingLineage(),
            postingFact.postingKind(),
            postingFact.provenance()),
        postingFact::postingId);
  }

  @Override
  public PeriodCloseOutcome closePeriod(
      PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(periodCloseDraft, "periodCloseDraft");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return withLock(
        () -> {
          if (!initialized) {
            return new PeriodCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }

          Snapshot rollbackSnapshot = snapshotState();
          List<PostingId> closingPostingIds = new ArrayList<>();
          boolean committed = false;
          try {
            for (PostingDraft closingPostingDraft : periodCloseDraft.closingPostings()) {
              PostingCommitResult commitResult = commit(closingPostingDraft, postingIdGenerator);
              if (commitResult instanceof PostingCommitResult.Rejected rejected) {
                throw new IllegalStateException(
                    "Generated period-close posting failed bookkeeping acceptance: "
                        + rejected.rejection());
              }
              closingPostingIds.add(
                  ((PostingCommitResult.Committed) commitResult).postingFact().postingId());
            }
            ClosedPeriod closedPeriod =
                new ClosedPeriod(
                    closedPeriods.size() + 1,
                    periodCloseDraft.reportingPeriod(),
                    periodCloseDraft.closingEquityAccountCode(),
                    periodCloseDraft.closedTotals(),
                    periodCloseDraft.closedAt(),
                    closingPostingIds);
            closedPeriods.add(closedPeriod);
            committed = true;
            return new PeriodCloseOutcome.Closed(closedPeriod);
          } finally {
            if (!committed) {
              restoreSnapshot(rollbackSnapshot);
            }
          }
        });
  }

  @Override
  public PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return withLock(
        () -> {
          List<CommittedPosting> matchingPostings =
              postingsByPostingId.values().stream()
                  .filter(posting -> matchesAccountFilter(posting, query.accountCode()))
                  .filter(
                      posting ->
                          matchesDateRange(
                              posting,
                              query.effectiveDateRange().effectiveDateFrom(),
                              query.effectiveDateRange().effectiveDateTo()))
                  .filter(posting -> matchesCursor(posting, query.cursor()))
                  .sorted(
                      Comparator.comparing(
                              (CommittedPosting posting) -> posting.journalEntry().effectiveDate())
                          .reversed()
                          .thenComparing(
                              posting -> posting.provenance().recordedAt(),
                              Comparator.reverseOrder())
                          .thenComparing(
                              posting -> posting.postingId().value(), Comparator.reverseOrder()))
                  .toList();
          int end = Math.min(query.limit(), matchingPostings.size());
          List<CommittedPosting> pageItems = matchingPostings.subList(0, end);
          return new PostingHistoryPage(
              pageItems,
              query.limit(),
              end < matchingPostings.size()
                  ? Optional.of(postingHistoryCursor(pageItems.getLast()))
                  : Optional.empty());
        });
  }

  @Override
  public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    return withLock(
        () -> {
          RegisteredAccount account = accountsByCode.get(query.accountCode());
          if (account == null) {
            return Optional.empty();
          }
          Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
          postingsByPostingId.values().stream()
              .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
              .filter(
                  posting ->
                      matchesDateRange(
                          posting,
                          query.effectiveDateRange().effectiveDateFrom(),
                          query.effectiveDateRange().effectiveDateTo()))
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .filter(line -> line.accountCode().equals(query.accountCode()))
              .forEach(line -> accumulate(totalsByCurrency, line));
          List<CurrencyBalance> balances =
              totalsByCurrency.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().code()))
                  .map(entry -> balance(entry.getKey(), entry.getValue()))
                  .toList();
          return Optional.of(
              new AccountBalanceView(
                  account, query.effectiveDateRange(), query.postingCoverage(), balances));
        });
  }

  @Override
  public List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    return withLock(
        () -> {
          Map<AccountCode, Map<CurrencyUnit, Totals>> totalsByAccount = mutableMap();
          postingsByPostingId.values().stream()
              .filter(posting -> postingCoverage.includes(posting.postingKind()))
              .filter(
                  posting ->
                      matchesDateRange(
                          posting,
                          effectiveDateRange.effectiveDateFrom(),
                          effectiveDateRange.effectiveDateTo()))
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .forEach(
                  line ->
                      accumulate(
                          totalsByAccount.computeIfAbsent(
                              line.accountCode(), ignored -> mutableMap()),
                          line));
          return totalsByAccount.entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.comparing(AccountCode::value)))
              .flatMap(
                  accountEntry ->
                      accountEntry.getValue().entrySet().stream()
                          .sorted(
                              Map.Entry.comparingByKey(Comparator.comparing(CurrencyUnit::code)))
                          .map(
                              currencyEntry ->
                                  new AccountCurrencyTotals(
                                      Objects.requireNonNull(
                                          accountsByCode.get(accountEntry.getKey()), "account"),
                                      currencyEntry.getKey(),
                                      currencyEntry.getValue().debit,
                                      currencyEntry.getValue().credit)))
              .toList();
        });
  }

  @Override
  public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    return withLock(
        () ->
            new TrialBalanceView(
                bookIdentity,
                query.effectiveDateTo(),
                dev.erst.fingrind.core.EffectiveDateRange.of(null, null),
                query.postingCoverage(),
                accountsByCode.values().stream()
                    .sorted(Comparator.comparing(account -> account.accountCode().value()))
                    .flatMap(
                        account ->
                            balancesFor(
                                    account,
                                    postingsByPostingId.values().stream()
                                        .filter(
                                            posting ->
                                                query
                                                    .postingCoverage()
                                                    .includes(posting.postingKind()))
                                        .filter(
                                            posting ->
                                                query.effectiveDateTo().stream()
                                                    .allMatch(
                                                        date ->
                                                            !posting
                                                                .journalEntry()
                                                                .effectiveDate()
                                                                .isAfter(date)))
                                        .toList())
                                .stream()
                                .map(balance -> new TrialBalanceRowView(account, balance)))
                    .toList(),
                List.of()));
  }

  @Override
  public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    return withLock(
        () -> {
          EffectiveDateRange range = query.effectiveDateRange();
          List<CommittedPosting> orderedPostings =
              postingsByPostingId.values().stream()
                  .sorted(
                      Comparator.comparing(
                              (CommittedPosting posting) -> posting.journalEntry().effectiveDate())
                          .thenComparing(posting -> posting.provenance().recordedAt())
                          .thenComparing(posting -> posting.postingId().value()))
                  .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
                  .filter(
                      posting ->
                          posting.journalEntry().lines().stream()
                              .anyMatch(line -> line.accountCode().equals(account.accountCode())))
                  .toList();
          List<CurrencyBalance> openingBalances =
              balancesFor(
                  account,
                  orderedPostings.stream()
                      .filter(
                          posting ->
                              query.effectiveDateRange().effectiveDateFrom().stream()
                                  .allMatch(
                                      lowerBound ->
                                          posting
                                              .journalEntry()
                                              .effectiveDate()
                                              .isBefore(lowerBound)))
                      .toList());
          Map<CurrencyUnit, Totals> runningTotals = totalsMap(openingBalances);
          List<AccountLedgerEntryView> entries = new java.util.ArrayList<>();
          orderedPostings.stream()
              .filter(posting -> range.contains(posting.journalEntry().effectiveDate()))
              .forEach(
                  posting -> {
                    CurrencyBalance movement = movementFor(account, posting);
                    Totals totals =
                        runningTotals.computeIfAbsent(
                            movement.netAmount().currencyUnit(), ignored -> new Totals());
                    totals.debit = Math.addExact(totals.debit, movement.debitTotal().minorUnits());
                    totals.credit =
                        Math.addExact(totals.credit, movement.creditTotal().minorUnits());
                    dev.erst.fingrind.core.CurrencyBalance runningBalance =
                        balance(movement.netAmount().currencyUnit(), totals);
                    entries.add(
                        new AccountLedgerEntryView(
                            posting,
                            movement,
                            runningBalance.netAmount(),
                            runningBalance.balanceSide()));
                  });
          List<CurrencyBalance> closingBalances = balancesFromTotals(runningTotals);
          return new AccountLedgerView(
              account, range, query.postingCoverage(), openingBalances, entries, closingBalances);
        });
  }

  @Override
  public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    return withLock(
        () -> {
          List<CommittedPosting> postings =
              postingsByPostingId.values().stream()
                  .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
                  .filter(
                      posting ->
                          !posting
                                  .journalEntry()
                                  .effectiveDate()
                                  .isBefore(query.effectiveDateFrom())
                              && !posting
                                  .journalEntry()
                                  .effectiveDate()
                                  .isAfter(query.effectiveDateTo()))
                  .toList();
          Map<CurrencyUnit, Totals> currencyTotals = mutableMap();
          Map<AccountCode, Map<CurrencyUnit, Totals>> accountTotals = mutableMap();
          postings.stream()
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .forEach(
                  line -> {
                    accumulate(currencyTotals, line);
                    accountTotals
                        .computeIfAbsent(line.accountCode(), ignored -> mutableMap())
                        .computeIfAbsent(line.amount().currencyUnit(), ignored -> new Totals());
                    accumulate(accountTotals.get(line.accountCode()), line);
                  });
          List<PeriodCurrencySummaryView> currencySummaries =
              currencyTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().code()))
                  .map(
                      entry ->
                          new PeriodCurrencySummaryView(balance(entry.getKey(), entry.getValue())))
                  .toList();
          List<PeriodAccountActivityView> accountActivity =
              accountTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                  .flatMap(
                      entry -> {
                        RegisteredAccount account =
                            Objects.requireNonNull(accountsByCode.get(entry.getKey()), "account");
                        return entry.getValue().entrySet().stream()
                            .sorted(Comparator.comparing(currency -> currency.getKey().code()))
                            .map(
                                currencyEntry ->
                                    new PeriodAccountActivityView(
                                        account,
                                        balance(currencyEntry.getKey(), currencyEntry.getValue())));
                      })
                  .toList();
          long accountsTouched =
              postings.stream()
                  .flatMap(posting -> posting.journalEntry().lines().stream())
                  .map(JournalLine::accountCode)
                  .distinct()
                  .count();
          int postingLineCount =
              postings.stream().mapToInt(posting -> posting.journalEntry().lines().size()).sum();
          return new PeriodSummaryView(
              query.effectiveDateFrom(),
              query.effectiveDateTo(),
              query.postingCoverage(),
              postings.size(),
              postingLineCount,
              Math.toIntExact(accountsTouched),
              currencySummaries,
              accountActivity);
        });
  }

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  @Override
  public void beginLedgerPlanTransaction() {
    withLock(
        () -> {
          if (transactionSnapshot != null) {
            throw new IllegalStateException("Ledger plan transaction is already active.");
          }
          transactionSnapshot =
              new Snapshot(
                  initialized,
                  initializedAt,
                  bookIdentity,
                  Map.copyOf(accountsByCode),
                  Map.copyOf(postingsByIdempotencyKey),
                  Map.copyOf(postingsByPostingId),
                  Map.copyOf(reversalsByPriorPostingId),
                  List.copyOf(closedPeriods));
        });
  }

  @Override
  public void commitLedgerPlanTransaction() {
    withLock(
        () -> {
          if (transactionSnapshot == null) {
            throw new IllegalStateException("No ledger plan transaction is active.");
          }
          transactionSnapshot = null;
        });
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    withLock(
        () -> {
          Snapshot snapshot = transactionSnapshot;
          if (snapshot == null) {
            return;
          }
          initialized = snapshot.initialized();
          initializedAt = snapshot.initializedAt();
          accountsByCode.clear();
          accountsByCode.putAll(snapshot.accountsByCode());
          postingsByIdempotencyKey.clear();
          postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
          postingsByPostingId.clear();
          postingsByPostingId.putAll(snapshot.postingsByPostingId());
          reversalsByPriorPostingId.clear();
          reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
          closedPeriods.clear();
          closedPeriods.addAll(snapshot.closedPeriods());
          transactionSnapshot = null;
        });
  }

  /** Deactivates one declared account for fixture-driven tests. */
  public void deactivateAccount(AccountCode accountCode) {
    withLock(
        () -> {
          RegisteredAccount existingAccount = accountsByCode.get(accountCode);
          if (existingAccount == null) {
            throw new IllegalArgumentException("Account is not declared: " + accountCode.value());
          }
          accountsByCode.put(
              accountCode,
              new RegisteredAccount(
                  existingAccount.accountCode(),
                  existingAccount.accountName(),
                  existingAccount.accountType(),
                  existingAccount.accountRole(),
                  existingAccount.accountTaxonomy(),
                  false,
                  existingAccount.declaredAt()));
        });
  }

  private static boolean matchesAccountFilter(
      CommittedPosting postingFact, Optional<AccountCode> accountCode) {
    return accountCode.isEmpty()
        || postingFact.journalEntry().lines().stream()
            .anyMatch(line -> line.accountCode().equals(accountCode.orElseThrow()));
  }

  private static boolean matchesDateRange(
      CommittedPosting postingFact,
      Optional<java.time.LocalDate> effectiveDateFrom,
      Optional<java.time.LocalDate> effectiveDateTo) {
    java.time.LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    return effectiveDateFrom.stream().allMatch(date -> !effectiveDate.isBefore(date))
        && effectiveDateTo.stream().allMatch(date -> !effectiveDate.isAfter(date));
  }

  private static boolean matchesAccountCursor(
      RegisteredAccount account, Optional<AccountRegistryCursor> cursor) {
    return cursor.isEmpty()
        || account.accountCode().value().compareTo(cursor.orElseThrow().accountCode().value()) > 0;
  }

  private static boolean matchesCursor(
      CommittedPosting postingFact, Optional<PostingHistoryCursor> cursor) {
    if (cursor.isEmpty()) {
      return true;
    }
    PostingHistoryCursor pageCursor = cursor.orElseThrow();
    java.time.LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    Instant recordedAt = postingFact.provenance().recordedAt();
    String postingId = postingFact.postingId().value();
    return effectiveDate.isBefore(pageCursor.effectiveDate())
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.isBefore(pageCursor.recordedAt()))
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.equals(pageCursor.recordedAt())
            && postingId.compareTo(pageCursor.postingId().value()) < 0);
  }

  private static void accumulate(Map<CurrencyUnit, Totals> totalsByCurrency, JournalLine line) {
    Totals totals =
        totalsByCurrency.computeIfAbsent(
            line.amount().currencyUnit(), ignoredCurrencyCode -> new Totals());
    if (line.side() == JournalLine.EntrySide.DEBIT) {
      totals.debit = Math.addExact(totals.debit, line.amount().minorUnits());
      return;
    }
    totals.credit = Math.addExact(totals.credit, line.amount().minorUnits());
  }

  private static CurrencyBalance balance(CurrencyUnit currencyUnit, Totals totals) {
    return CurrencyBalance.ofTotals(
        Money.ofMinorUnits(currencyUnit, totals.debit),
        Money.ofMinorUnits(currencyUnit, totals.credit));
  }

  private static List<CurrencyBalance> balancesFor(
      RegisteredAccount account, List<CommittedPosting> postings) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    postings.stream()
        .flatMap(posting -> posting.journalEntry().lines().stream())
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return balancesFromTotals(totalsByCurrency);
  }

  private static List<CurrencyBalance> balancesFromTotals(
      Map<CurrencyUnit, Totals> totalsByCurrency) {
    return totalsByCurrency.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().code()))
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static Map<CurrencyUnit, Totals> totalsMap(List<CurrencyBalance> balances) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    for (CurrencyBalance balance : balances) {
      totalsByCurrency.put(balance.netAmount().currencyUnit(), totalsFrom(balance));
    }
    return totalsByCurrency;
  }

  private static Totals totalsFrom(CurrencyBalance balance) {
    Totals totals = new Totals();
    totals.debit = balance.debitTotal().minorUnits();
    totals.credit = balance.creditTotal().minorUnits();
    return totals;
  }

  private static CurrencyBalance movementFor(
      RegisteredAccount account, CommittedPosting postingFact) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    postingFact.journalEntry().lines().stream()
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return totalsByCurrency.entrySet().stream()
        .findFirst()
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .orElseThrow();
  }

  private static PostingHistoryCursor postingHistoryCursor(CommittedPosting posting) {
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  private <T> T withLock(Supplier<T> action) {
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }

  private void withLock(Runnable action) {
    lock.lock();
    try {
      action.run();
    } finally {
      lock.unlock();
    }
  }

  private static <K, V> Map<K, V> mutableMap() {
    return new ConcurrentHashMap<>();
  }

  private Snapshot snapshotState() {
    return new Snapshot(
        initialized,
        initializedAt,
        bookIdentity,
        Map.copyOf(accountsByCode),
        Map.copyOf(postingsByIdempotencyKey),
        Map.copyOf(postingsByPostingId),
        Map.copyOf(reversalsByPriorPostingId),
        List.copyOf(closedPeriods));
  }

  private void restoreSnapshot(Snapshot snapshot) {
    initialized = snapshot.initialized();
    initializedAt = snapshot.initializedAt();
    bookIdentity = snapshot.bookIdentity();
    accountsByCode.clear();
    accountsByCode.putAll(snapshot.accountsByCode());
    postingsByIdempotencyKey.clear();
    postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
    postingsByPostingId.clear();
    postingsByPostingId.putAll(snapshot.postingsByPostingId());
    reversalsByPriorPostingId.clear();
    reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
    closedPeriods.clear();
    closedPeriods.addAll(snapshot.closedPeriods());
  }

  /** Mutable debit and credit accumulators for one currency bucket. */
  private static final class Totals {
    private long debit;
    private long credit;
  }

  private record Snapshot(
      boolean initialized,
      Instant initializedAt,
      BookIdentity bookIdentity,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      Map<IdempotencyKey, CommittedPosting> postingsByIdempotencyKey,
      Map<PostingId, CommittedPosting> postingsByPostingId,
      Map<PostingId, CommittedPosting> reversalsByPriorPostingId,
      List<ClosedPeriod> closedPeriods) {}
}
