package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for shared posting validation rules. */
class PostingValidationTest {
  @Test
  void rejectionFor_reportsDuplicateIdempotencyBeforeAccountViolations() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.existingPosting = Optional.of(existingPosting("posting-1", "idem-1"));

    Optional<PostingRejection> rejection = PostingValidation.rejectionFor(command("idem-1"), book);

    assertEquals(Optional.of(new PostingRejection.DuplicateIdempotencyKey()), rejection);
    assertEquals(0, book.findAccountsCalls);
  }

  @Test
  void rejectionFor_usesOneBulkLookupAndDeduplicatesRepeatedAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.accounts.put(
        new AccountCode("1000"),
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            false,
            Instant.parse("2026-04-07T10:15:30Z")));

    Optional<PostingRejection> rejection =
        PostingValidation.rejectionFor(
            command(
                "idem-2",
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "1.00"),
                    line("1000", JournalLine.EntrySide.DEBIT, "2.00"),
                    line("2000", JournalLine.EntrySide.CREDIT, "3.00"))),
            book);

    assertEquals(
        Optional.of(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.InactiveAccount(new AccountCode("1000")),
                    new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
        rejection);
    assertEquals(1, book.findAccountsCalls);
    assertEquals(List.of(new AccountCode("1000"), new AccountCode("2000")), book.requestedAccounts);
    assertThrows(AssertionError.class, () -> book.findAccount(new AccountCode("1000")));
  }

  @Test
  void defaultFindAccounts_delegatesToSingleAccountLookupsInStableOrder() {
    FallbackValidationBook book = new FallbackValidationBook();
    AccountCode cash = new AccountCode("1000");
    AccountCode revenue = new AccountCode("2000");
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            cash,
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cash, cashAccount);

    assertEquals(
        Map.of(cash, cashAccount),
        book.findAccounts(new java.util.LinkedHashSet<>(List.of(cash, revenue))));
    assertEquals(List.of(cash, revenue), book.requestedAccounts);
  }

  private static PostEntryCommand command(String idempotencyKey) {
    return command(
        idempotencyKey,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static PostEntryCommand command(String idempotencyKey, List<JournalLine> lines) {
    return new PostEntryCommand(
        new JournalEntry(LocalDate.parse("2026-04-07"), lines),
        PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  private static PostingFact existingPosting(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineage.direct(),
        new dev.erst.fingrind.core.CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  /** Validation-book double that exposes the batch account lookup path explicitly. */
  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static final class RecordingValidationBook implements PostingValidationBook {
    private final Map<AccountCode, DeclaredAccount> accounts = new LinkedHashMap<>();
    private boolean initialized;
    private Optional<PostingFact> existingPosting = Optional.empty();
    private int findAccountsCalls;
    private List<AccountCode> requestedAccounts = List.of();

    @Override
    public boolean isInitialized() {
      return initialized;
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      throw new AssertionError("findAccount should not be used when batch lookup is available");
    }

    @Override
    public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
      findAccountsCalls++;
      requestedAccounts = List.copyOf(accountCodes);
      Map<AccountCode, DeclaredAccount> matchedAccounts = new LinkedHashMap<>();
      for (AccountCode accountCode : accountCodes) {
        DeclaredAccount account = accounts.get(accountCode);
        if (account != null) {
          matchedAccounts.put(accountCode, account);
        }
      }
      return Map.copyOf(matchedAccounts);
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return existingPosting;
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }
  }

  /** Validation-book double that exercises the default single-account fallback lookup path. */
  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static final class FallbackValidationBook implements PostingValidationBook {
    private final Map<AccountCode, DeclaredAccount> accounts = new LinkedHashMap<>();
    private List<AccountCode> requestedAccounts = List.of();

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      requestedAccounts =
          java.util.stream.Stream.concat(
                  requestedAccounts.stream(), java.util.stream.Stream.of(accountCode))
              .toList();
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }
  }
}
