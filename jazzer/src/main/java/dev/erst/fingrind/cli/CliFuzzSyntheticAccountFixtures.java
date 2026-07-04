package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;

/** Synthetic account declarations shared by Jazzer workflow fixtures. */
public final class CliFuzzSyntheticAccountFixtures {
  private CliFuzzSyntheticAccountFixtures() {}

  /** Returns deterministic declare-account commands for every distinct posting account. */
  public static List<DeclareAccountCommand> declarePostingAccountCommands(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return distinctAccountDeclarations(
        switch (command.entry()) {
          case BookkeepingEntry.DirectJournal directJournal ->
              distinctJournalLineAccountDeclarations(directJournal.lines());
          case BookkeepingEntry.OpeningPosition openingPosition ->
              openingPositionAccountDeclarations(openingPosition);
          case BookkeepingEntry.Reversal _ ->
              distinctJournalLineAccountDeclarations(CliFuzzFixtures.journalEntry(command).lines());
          default -> CliFuzzTypedEntryAccountDeclarations.declare(command.entry());
        });
  }

  /** Returns the first journal-line account code for lifecycle assertions. */
  public static AccountCode firstAccountCode(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return CliFuzzFixtures.journalEntry(command).lines().getFirst().accountCode();
  }

  private static List<DeclareAccountCommand> distinctJournalLineAccountDeclarations(
      List<dev.erst.fingrind.core.JournalLine> journalLines) {
    return journalLines.stream()
        .map(line -> line.accountCode())
        .distinct()
        .map(CliFuzzSyntheticAccountFixtures::syntheticDeclareAccountCommand)
        .toList();
  }

  private static List<DeclareAccountCommand> openingPositionAccountDeclarations(
      BookkeepingEntry.OpeningPosition openingPosition) {
    return openingPosition.lines().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                line -> line.accountCode(),
                line ->
                    line.side() == dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT
                        ? syntheticDeclareAccountCommand(
                            line.accountCode(),
                            AccountType.ASSET,
                            CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                                FinancialPositionLineClassification.CURRENT_ASSET))
                        : syntheticDeclareAccountCommand(
                            line.accountCode(),
                            AccountType.LIABILITY,
                            CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                                FinancialPositionLineClassification.CURRENT_LIABILITY)),
                (first, ignored) -> first,
                java.util.LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private static List<DeclareAccountCommand> distinctAccountDeclarations(
      List<DeclareAccountCommand> declarations) {
    return List.copyOf(
        declarations.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    declaration -> Objects.requireNonNull(declaration, "declaration").accountCode(),
                    declaration -> declaration,
                    (first, ignored) -> first,
                    java.util.LinkedHashMap::new))
            .values());
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(AccountCode accountCode) {
    AccountType accountType = CliFuzzSyntheticAccountDoctrine.accountType(accountCode);
    return syntheticDeclareAccountCommand(
        accountCode, accountType, CliFuzzSyntheticAccountDoctrine.accountTaxonomy(accountType));
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(
      AccountCode accountCode, AccountType accountType, AccountTaxonomy accountTaxonomy) {
    return new DeclareAccountCommand(
        accountCode,
        new AccountName("Synthetic " + accountCode.value()),
        accountType,
        accountTaxonomy);
  }
}
