package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Reduces raw journal lines to per-account net movement so semantic guards can reject no-op
 * journals.
 */
final class JournalEconomicMovement {
  private JournalEconomicMovement() {}

  static boolean isEconomicallyNull(List<JournalLine> lines) {
    List<JournalLine> orderedLines = new ArrayList<>(lines);
    orderedLines.sort(java.util.Comparator.comparing(line -> line.accountCode().value()));
    AccountCode currentAccountCode = null;
    long reducedTotalMinorUnits = 0L;
    for (JournalLine line : orderedLines) {
      if (currentAccountCode != null && !currentAccountCode.equals(line.accountCode())) {
        if (reducedTotalMinorUnits != 0L) {
          return false;
        }
        reducedTotalMinorUnits = 0L;
      }
      currentAccountCode = line.accountCode();
      reducedTotalMinorUnits = Math.addExact(reducedTotalMinorUnits, signedMinorUnits(line));
    }
    return reducedTotalMinorUnits == 0L;
  }

  private static long signedMinorUnits(JournalLine line) {
    return line.side() == JournalLine.EntrySide.DEBIT
        ? line.amount().minorUnits()
        : Math.negateExact(line.amount().minorUnits());
  }
}
