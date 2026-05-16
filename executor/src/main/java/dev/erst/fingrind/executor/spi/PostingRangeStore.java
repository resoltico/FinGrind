package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Reads committed posting streams and close-horizon facts from the selected book. */
public interface PostingRangeStore {
  /** Returns the committed postings that fall inside the selected effective-date range. */
  List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange);

  /** Returns the earliest committed posting in this book, if one exists already. */
  default Optional<CommittedPosting> firstCommittedPosting() {
    return postings(EffectiveDateRange.unbounded()).stream().findFirst();
  }

  /** Returns the earliest committed effective date when one posting already exists. */
  Optional<LocalDate> earliestPostingEffectiveDate();

  /** Returns the inclusive closed-through effective date when one period has been closed. */
  Optional<LocalDate> closedThroughEffectiveDate();
}
