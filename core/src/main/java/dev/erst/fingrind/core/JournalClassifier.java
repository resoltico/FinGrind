package dev.erst.fingrind.core;

import dev.erst.fingrind.core.JournalLine.EntrySide;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Total semantic classifier over anchor-role incidence plus evidence and structural context. */
public final class JournalClassifier {
  private static final TypedSignature SETTLED_SALE =
      new TypedSignature(
          EconomicEventClass.SETTLED_SALE,
          Set.of(
              new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.REVENUE, EntrySide.CREDIT)));
  private static final TypedSignature CREDIT_SALE =
      new TypedSignature(
          EconomicEventClass.CREDIT_SALE,
          Set.of(
              new AnchorEntry(AccountRole.RECEIVABLE, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.REVENUE, EntrySide.CREDIT)));
  private static final TypedSignature SETTLED_PURCHASE =
      new TypedSignature(
          EconomicEventClass.SETTLED_PURCHASE,
          Set.of(
              new AnchorEntry(AccountRole.INVENTORY, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.CASH, EntrySide.CREDIT)));
  private static final TypedSignature CREDIT_PURCHASE =
      new TypedSignature(
          EconomicEventClass.CREDIT_PURCHASE,
          Set.of(
              new AnchorEntry(AccountRole.INVENTORY, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.PAYABLE, EntrySide.CREDIT)));
  private static final TypedSignature SETTLED_EXPENSE =
      new TypedSignature(
          EconomicEventClass.SETTLED_EXPENSE,
          Set.of(
              new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.CASH, EntrySide.CREDIT)));
  private static final TypedSignature CREDIT_EXPENSE =
      new TypedSignature(
          EconomicEventClass.CREDIT_EXPENSE,
          Set.of(
              new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.PAYABLE, EntrySide.CREDIT)));
  private static final TypedSignature AR_SETTLEMENT =
      new TypedSignature(
          EconomicEventClass.AR_SETTLEMENT,
          Set.of(
              new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.RECEIVABLE, EntrySide.CREDIT)));
  private static final TypedSignature AP_SETTLEMENT =
      new TypedSignature(
          EconomicEventClass.AP_SETTLEMENT,
          Set.of(
              new AnchorEntry(AccountRole.PAYABLE, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.CASH, EntrySide.CREDIT)));
  private static final TypedSignature OWNER_CONTRIBUTION =
      new TypedSignature(
          EconomicEventClass.OWNER_CONTRIBUTION,
          Set.of(
              new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.EQUITY_CONTRIBUTED, EntrySide.CREDIT)));
  private static final TypedSignature OWNER_WITHDRAWAL =
      new TypedSignature(
          EconomicEventClass.OWNER_WITHDRAWAL,
          Set.of(
              new AnchorEntry(AccountRole.EQUITY_DRAWS, EntrySide.DEBIT),
              new AnchorEntry(AccountRole.CASH, EntrySide.CREDIT)));
  private static final List<TypedSignature> TYPED_SIGNATURES =
      List.of(
          SETTLED_SALE,
          CREDIT_SALE,
          SETTLED_PURCHASE,
          CREDIT_PURCHASE,
          SETTLED_EXPENSE,
          CREDIT_EXPENSE,
          AR_SETTLEMENT,
          AP_SETTLEMENT,
          OWNER_CONTRIBUTION,
          OWNER_WITHDRAWAL);

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
      StructuralContext structural) {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(accountRoleLookup, "accountRoleLookup");
    return classifyDerived(
        anchorSignature(journalEntry, accountRoleLookup),
        hasCashLine(journalEntry, accountRoleLookup),
        evidenceClass,
        structural);
  }

  private static ClassificationResult classifyDerived(
      Set<AnchorEntry> anchorSignature,
      boolean hasCashLine,
      EvidenceClass evidenceClass,
      StructuralContext structural) {
    Set<AnchorEntry> signature =
        Set.copyOf(Objects.requireNonNull(anchorSignature, "anchorSignature"));
    boolean requiredHasCashLine = hasCashLine;
    EvidenceClass requiredEvidenceClass = Objects.requireNonNull(evidenceClass, "evidenceClass");
    StructuralContext requiredStructural = Objects.requireNonNull(structural, "structural");
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
    Set<EconomicEventClass> containedTypedEvents = containedTypedEvents(signature);
    EconomicEventClass eventClass =
        ifExactSingleton(signature, containedTypedEvents)
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

  private static java.util.Optional<EconomicEventClass> ifExactSingleton(
      Set<AnchorEntry> signature, Set<EconomicEventClass> containedTypedEvents) {
    if (containedTypedEvents.size() != 1) {
      return java.util.Optional.empty();
    }
    EconomicEventClass candidate = containedTypedEvents.iterator().next();
    if (candidate == EconomicEventClass.SETTLED_SALE
        && tradingSaleSignature(signature, SETTLED_SALE.anchorSignature)) {
      return java.util.Optional.of(candidate);
    }
    if (candidate == EconomicEventClass.CREDIT_SALE
        && tradingSaleSignature(signature, CREDIT_SALE.anchorSignature)) {
      return java.util.Optional.of(candidate);
    }
    for (TypedSignature typedSignature : TYPED_SIGNATURES) {
      if (typedSignature.eventClass == candidate
          && typedSignature.anchorSignature.equals(signature)) {
        return java.util.Optional.of(candidate);
      }
    }
    return java.util.Optional.empty();
  }

  private static boolean tradingSaleSignature(
      Set<AnchorEntry> signature, Set<AnchorEntry> saleSignature) {
    return signature.size() == saleSignature.size() + 2
        && signature.contains(new AnchorEntry(AccountRole.EXPENSE, EntrySide.DEBIT))
        && signature.contains(new AnchorEntry(AccountRole.INVENTORY, EntrySide.CREDIT));
  }

  private static Set<EconomicEventClass> containedTypedEvents(Set<AnchorEntry> anchorSignature) {
    Set<EconomicEventClass> contained = new LinkedHashSet<>();
    for (TypedSignature typedSignature : TYPED_SIGNATURES) {
      if (anchorSignature.containsAll(typedSignature.anchorSignature)) {
        contained.add(typedSignature.eventClass);
      }
    }
    return Set.copyOf(contained);
  }

  private record TypedSignature(EconomicEventClass eventClass, Set<AnchorEntry> anchorSignature) {
    private TypedSignature {
      Objects.requireNonNull(eventClass, "eventClass");
      anchorSignature = Set.copyOf(Objects.requireNonNull(anchorSignature, "anchorSignature"));
    }
  }
}
