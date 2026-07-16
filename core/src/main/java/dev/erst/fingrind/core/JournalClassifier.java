package dev.erst.fingrind.core;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Total semantic classifier over anchor-role incidence plus evidence and structural context. */
public final class JournalClassifier {
  private JournalClassifier() {}

  /** Resolves one declared account code into the account role used by classifier semantics. */
  @FunctionalInterface
  public interface AccountRoleLookup {
    /** Returns the semantic account role for one resolved journal-line account code. */
    AccountRole roleFor(AccountCode accountCode);
  }

  /** Returns the total classifier outcome for one resolved journal plus its role lookup. */
  public static ClassificationResult classify(
      JournalEntry journalEntry,
      AccountRoleLookup accountRoleLookup,
      EvidenceClass evidenceClass,
      StructuralContext structural,
      java.util.Optional<EconomicEventClass> assertedTypedEventClass) {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(accountRoleLookup, "accountRoleLookup");
    return classifyDerived(
        anchorSignature(journalEntry, accountRoleLookup),
        hasCashLine(journalEntry, accountRoleLookup),
        evidenceClass,
        structural,
        assertedTypedEventClass);
  }

  private static ClassificationResult classifyDerived(
      Set<AnchorEntry> anchorSignature,
      boolean hasCashLine,
      EvidenceClass evidenceClass,
      StructuralContext structural,
      java.util.Optional<EconomicEventClass> assertedTypedEventClass) {
    Set<AnchorEntry> signature =
        Set.copyOf(Objects.requireNonNull(anchorSignature, "anchorSignature"));
    boolean requiredHasCashLine = hasCashLine;
    EvidenceClass requiredEvidenceClass = Objects.requireNonNull(evidenceClass, "evidenceClass");
    StructuralContext requiredStructural = Objects.requireNonNull(structural, "structural");
    java.util.Optional<EconomicEventClass> requiredAssertedTypedEventClass =
        java.util.Optional.ofNullable(
            Objects.requireNonNull(assertedTypedEventClass, "assertedTypedEventClass")
                .orElse(null));
    if (requiredStructural.reversesPriorPosting().isPresent()) {
      return new ClassificationResult(
          EconomicEventClass.REVERSAL,
          signature,
          Set.of(),
          requiredHasCashLine,
          requiredEvidenceClass,
          requiredStructural);
    }
    if (requiredStructural.adoptionOpeningEntry()) {
      return new ClassificationResult(
          EconomicEventClass.OPENING,
          signature,
          Set.of(),
          requiredHasCashLine,
          requiredEvidenceClass,
          requiredStructural);
    }
    if (requiredAssertedTypedEventClass.isPresent()) {
      EconomicEventClass assertedEventClass = requiredAssertedTypedEventClass.orElseThrow();
      TypedJournalSignatureCatalog.requireCompatible(assertedEventClass, signature);
      return new ClassificationResult(
          assertedEventClass,
          signature,
          Set.of(assertedEventClass),
          requiredHasCashLine,
          requiredEvidenceClass,
          requiredStructural);
    }
    Set<EconomicEventClass> containedTypedEvents =
        TypedJournalSignatureCatalog.containedEvents(signature);
    EconomicEventClass eventClass =
        TypedJournalSignatureCatalog.exactSingleton(signature, containedTypedEvents)
            .orElseGet(
                () ->
                    containedTypedEvents.isEmpty()
                        ? EconomicEventClass.ADJUSTMENT
                        : EconomicEventClass.COMPOUND_OPERATIONAL);
    return new ClassificationResult(
        eventClass,
        signature,
        containedTypedEvents,
        requiredHasCashLine,
        requiredEvidenceClass,
        requiredStructural);
  }

  private static Set<AnchorEntry> anchorSignature(
      JournalEntry journalEntry, AccountRoleLookup accountRoleLookup) {
    var debitTotals = new EnumMap<AccountRole, Long>(AccountRole.class);
    var creditTotals = new EnumMap<AccountRole, Long>(AccountRole.class);
    for (JournalLine line : journalEntry.lines()) {
      AccountRole role = accountRoleLookup.roleFor(line.accountCode());
      if (!role.anchorRole()) {
        continue;
      }
      var totals = line.side() == JournalLine.EntrySide.DEBIT ? debitTotals : creditTotals;
      totals.merge(role, line.amount().minorUnits(), Math::addExact);
    }
    Set<AnchorEntry> signature = new LinkedHashSet<>();
    for (AccountRole role : AccountRole.values()) {
      if (!role.anchorRole()) {
        continue;
      }
      long debit = debitTotals.getOrDefault(role, 0L);
      long credit = creditTotals.getOrDefault(role, 0L);
      if (debit == credit) {
        continue;
      }
      signature.add(
          new AnchorEntry(
              role, debit > credit ? JournalLine.EntrySide.DEBIT : JournalLine.EntrySide.CREDIT));
    }
    return Set.copyOf(signature);
  }

  private static boolean hasCashLine(
      JournalEntry journalEntry, AccountRoleLookup accountRoleLookup) {
    return journalEntry.lines().stream()
        .map(JournalLine::accountCode)
        .map(accountRoleLookup::roleFor)
        .anyMatch(role -> role == AccountRole.CASH);
  }
}
