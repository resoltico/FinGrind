package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validates generated postings, journal lines, and derived close links shared by period closes. */
final class AttestationPeriodClosePostingEffects {
  private AttestationPeriodClosePostingEffects() {}

  static List<AttestationPreimage.Fact> postingsForKind(
      AttestationPreimage effectPreimage, String expectedKind) {
    return AttestationPreimageFields.records(
            effectPreimage, AttestationPeriodCloseProfileFacts.POSTING)
        .stream()
        .filter(
            posting -> expectedKind.equals(AttestationPeriodCloseProfileFacts.token(posting, 3)))
        .toList();
  }

  static List<AttestationPreimage.Fact> requireOnlyExpectedPostingKinds(
      AttestationPreimage effectPreimage,
      List<AttestationPreimage.Fact> fiscalPostings,
      List<AttestationPreimage.Fact> interimPostings) {
    List<AttestationPreimage.Fact> allPostings =
        AttestationPreimageFields.records(
            effectPreimage, AttestationPeriodCloseProfileFacts.POSTING);
    AttestationPeriodCloseProfileFacts.require(
        allPostings.size() == fiscalPostings.size() + interimPostings.size());
    for (AttestationPreimage.Fact posting : allPostings) {
      String operationKind = AttestationPeriodCloseProfileFacts.token(posting, 3);
      AttestationPeriodCloseProfileFacts.require(
          AttestationPeriodCloseProfileFacts.PERIOD_CLOSE.equals(
                  AttestationPeriodCloseProfileFacts.token(posting, 4))
              && operationKind.equals(AttestationPeriodCloseProfileFacts.token(posting, 5))
              && AttestationPeriodCloseProfileFacts.SYSTEM.equals(
                  AttestationPeriodCloseProfileFacts.token(posting, 12)));
    }
    return allPostings;
  }

  static void requireJournalLines(
      AttestationPreimage effectPreimage, List<AttestationPreimage.Fact> postings) {
    Set<UUID> postingIds = postingIds(postings);
    Map<UUID, Set<BigInteger>> lineOrdersByPosting = lineOrdersByPosting(postings.size());
    for (AttestationPreimage.Fact posting : postings) {
      AttestationPeriodCloseProfileFacts.require(
          AttestationPeriodCloseProfileFacts.mutation(posting, 0)
              == AttestationEffectMutation.CREATE.wireValue());
      lineOrdersByPosting.put(AttestationPeriodCloseProfileFacts.uuid(posting, 1), newLineOrders());
    }
    for (AttestationPreimage.Fact line :
        AttestationPreimageFields.records(
            effectPreimage, AttestationPeriodCloseProfileFacts.JOURNAL_LINE)) {
      AttestationPeriodCloseProfileFacts.require(
          AttestationPeriodCloseProfileFacts.mutation(line, 0)
              == AttestationEffectMutation.CREATE.wireValue());
      UUID postingId = AttestationPeriodCloseProfileFacts.uuid(line, 1);
      AttestationPeriodCloseProfileFacts.require(postingIds.contains(postingId));
      Set<BigInteger> lineOrders =
          Objects.requireNonNull(lineOrdersByPosting.get(postingId), "lineOrders");
      lineOrders.add(AttestationPeriodCloseProfileFacts.unsigned32(line, 2));
    }
    for (Set<BigInteger> lineOrders : lineOrdersByPosting.values()) {
      AttestationPeriodCloseProfileFacts.require(lineOrders.size() >= 2);
      for (int lineOrder = 0; lineOrder < lineOrders.size(); lineOrder++) {
        AttestationPeriodCloseProfileFacts.require(
            lineOrders.contains(BigInteger.valueOf(lineOrder)));
      }
    }
  }

  static void requireCreatedEffects(AttestationPreimage effectPreimage) {
    for (AttestationPreimage.Fact fact : effectPreimage.records()) {
      AttestationPeriodCloseProfileFacts.require(
          AttestationPeriodCloseProfileFacts.mutation(fact, 0)
              == AttestationEffectMutation.CREATE.wireValue());
    }
  }

  static void requireLinkedPostings(
      AttestationPreimage effectPreimage,
      int linkTag,
      BigInteger expectedOrder,
      List<AttestationPreimage.Fact> postings,
      LocalDate expectedEffectiveDate) {
    Set<UUID> postingIds = postingIds(postings);
    List<AttestationPreimage.Fact> links =
        AttestationPreimageFields.records(effectPreimage, linkTag);
    AttestationPeriodCloseProfileFacts.require(links.size() == postingIds.size());
    Set<UUID> linkedPostingIds = new HashSet<>();
    for (AttestationPreimage.Fact link : links) {
      AttestationPeriodCloseProfileFacts.require(
          expectedOrder.equals(AttestationPeriodCloseProfileFacts.unsigned64(link, 1)));
      linkedPostingIds.add(AttestationPeriodCloseProfileFacts.uuid(link, 2));
    }
    AttestationPeriodCloseProfileFacts.require(linkedPostingIds.equals(postingIds));
    for (AttestationPreimage.Fact posting : postings) {
      AttestationPeriodCloseProfileFacts.require(
          expectedEffectiveDate.equals(AttestationPeriodCloseProfileFacts.date(posting, 6)));
    }
  }

  static void requireUniqueSweepTotals(
      AttestationPreimage effectPreimage, BigInteger expectedOrder) {
    for (AttestationPreimage.Fact fact :
        AttestationPreimageFields.records(
            effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_TOTAL)) {
      AttestationPeriodCloseProfileFacts.require(
          expectedOrder.equals(AttestationPeriodCloseProfileFacts.unsigned64(fact, 1)));
    }
  }

  private static Set<UUID> postingIds(List<AttestationPreimage.Fact> postings) {
    Set<UUID> postingIds = new HashSet<>();
    for (AttestationPreimage.Fact posting : postings) {
      postingIds.add(AttestationPeriodCloseProfileFacts.uuid(posting, 1));
    }
    return Set.copyOf(postingIds);
  }

  private static Map<UUID, Set<BigInteger>> lineOrdersByPosting(int expectedPostingCount) {
    return new HashMap<>(expectedPostingCount);
  }

  private static Set<BigInteger> newLineOrders() {
    return new HashSet<>();
  }
}
