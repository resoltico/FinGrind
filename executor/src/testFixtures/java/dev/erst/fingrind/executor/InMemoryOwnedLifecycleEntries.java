package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Selects active lifecycle facts in the same deterministic order used by the durable stores. */
final class InMemoryOwnedLifecycleEntries {
  private InMemoryOwnedLifecycleEntries() {}

  static boolean historyContains(
      InMemoryOwnedLifecycleProjectionSource source, Predicate<BookkeepingEntry> predicate) {
    return InMemoryBookSessionSupport.withLock(
        source.lifecycleLock(),
        () ->
            source.lifecyclePostingsByPostingId().values().stream()
                .flatMap(posting -> posting.resolvedOriginatingEntry().stream())
                .anyMatch(predicate));
  }

  static List<BookkeepingEntry> activeEntries(
      InMemoryOwnedLifecycleProjectionSource source, Optional<LocalDate> effectiveDateAsOf) {
    return InMemoryBookSessionSupport.withLock(
        source.lifecycleLock(),
        () ->
            source.lifecyclePostingsByPostingId().values().stream()
                .filter(posting -> includedAt(source, posting, effectiveDateAsOf))
                .sorted(
                    Comparator.comparing(
                            (CommittedPosting posting) -> posting.journalEntry().effectiveDate())
                        .thenComparing(posting -> posting.provenance().recordedAt())
                        .thenComparing(posting -> posting.postingId().value()))
                .flatMap(posting -> posting.resolvedOriginatingEntry().stream())
                .toList());
  }

  private static boolean includedAt(
      InMemoryOwnedLifecycleProjectionSource source,
      CommittedPosting posting,
      Optional<LocalDate> effectiveDateAsOf) {
    if (effectiveDateAsOf.isPresent()
        && posting.journalEntry().effectiveDate().isAfter(effectiveDateAsOf.orElseThrow())) {
      return false;
    }
    CommittedPosting reversal =
        source.lifecycleReversalsByPriorPostingId().get(posting.postingId());
    return reversal == null
        || effectiveDateAsOf.stream()
            .anyMatch(asOf -> reversal.journalEntry().effectiveDate().isAfter(asOf));
  }
}
