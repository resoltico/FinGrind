package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PositiveMoney;
import java.util.Map;
import java.util.stream.Collectors;

/** Compares one candidate journal to the exact full negation of a prior journal. */
final class JournalReversalEquivalence {
  private JournalReversalEquivalence() {}

  static boolean negates(JournalEntry candidateReversal, JournalEntry original) {
    return normalizedLines(candidateReversal).equals(negatedLines(original));
  }

  private static Map<LineFingerprint, Long> normalizedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .collect(Collectors.groupingBy(LineFingerprint::from, Collectors.counting()));
  }

  private static Map<LineFingerprint, Long> negatedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .map(LineFingerprint::negatedFrom)
        .collect(Collectors.groupingBy(fingerprint -> fingerprint, Collectors.counting()));
  }

  private record LineFingerprint(
      AccountCode accountCode, JournalLine.EntrySide side, PositiveMoney amount) {
    static LineFingerprint from(JournalLine line) {
      return new LineFingerprint(line.accountCode(), line.side(), line.amount());
    }

    static LineFingerprint negatedFrom(JournalLine line) {
      return new LineFingerprint(line.accountCode(), opposite(line.side()), line.amount());
    }

    private static JournalLine.EntrySide opposite(JournalLine.EntrySide side) {
      return switch (side) {
        case DEBIT -> JournalLine.EntrySide.CREDIT;
        case CREDIT -> JournalLine.EntrySide.DEBIT;
      };
    }
  }
}
