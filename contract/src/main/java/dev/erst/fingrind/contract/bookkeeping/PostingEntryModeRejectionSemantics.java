package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Objects;
import java.util.Set;

/** Canonical owner for posting-mode and lifecycle entry-semantics rejection details. */
final class PostingEntryModeRejectionSemantics {
  private PostingEntryModeRejectionSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation economicNullJournal(String selectorValue) {
    return economicNullJournal("entryKind", selectorValue);
  }

  /** Returns one entry-semantics violation for raw journals that net every account to zero. */
  static PostingRejection.EntrySemanticsViolation economicNullJournal(
      String selectorField, String selectorValue) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    return new PostingRejection.EntrySemanticsViolation(
        "economic-null-journal",
        "lines",
        "%s '%s' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement."
            .formatted(requiredSelectorField, requiredSelectorValue));
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation verbRequiresRole(
      String selectorValue, AccountRole requiredRole) {
    return verbRequiresRole("entryKind", selectorValue, requiredRole);
  }

  /** Returns one cash-basis refusal for a receivable-side or payable-side typed event. */
  static PostingRejection.EntrySemanticsViolation verbRequiresRole(
      String selectorField, String selectorValue, AccountRole requiredRole) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(requiredRole, "requiredRole");
    return switch (requiredRole) {
      case RECEIVABLE ->
          new PostingRejection.EntrySemanticsViolation(
              "verb-requires-receivable-role",
              requiredSelectorField,
              "%s '%s' requires trade-receivable semantics that this cash-basis book does not admit."
                  .formatted(requiredSelectorField, requiredSelectorValue));
      case PAYABLE ->
          new PostingRejection.EntrySemanticsViolation(
              "verb-requires-payable-role",
              requiredSelectorField,
              "%s '%s' requires trade-payable semantics that this cash-basis book does not admit."
                  .formatted(requiredSelectorField, requiredSelectorValue));
      default -> throw new IllegalArgumentException("requiredRole must be RECEIVABLE or PAYABLE.");
    };
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation rawJournalShadowsTypedEvent(
      String selectorValue, EconomicEventClass eventClass, String operationName) {
    return rawJournalShadowsTypedEvent("entryKind", selectorValue, eventClass, operationName);
  }

  /** Returns one refusal when the raw direct-journal path shadows one typed business event. */
  static PostingRejection.EntrySemanticsViolation rawJournalShadowsTypedEvent(
      String selectorField,
      String selectorValue,
      EconomicEventClass eventClass,
      String operationName) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(eventClass, "eventClass");
    Objects.requireNonNull(operationName, "operationName");
    return new PostingRejection.EntrySemanticsViolation(
        "raw-journal-shadows-typed-event",
        "lines",
        "%s '%s' resolves to eventClass '%s'. Use %s instead of the raw direct-journal path."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                eventClass.wireValue(),
                operationName));
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation rawJournalBundlesOperationalEvents(
      String selectorValue, Set<EconomicEventClass> containedTypedEvents) {
    return rawJournalBundlesOperationalEvents("entryKind", selectorValue, containedTypedEvents);
  }

  /** Returns one refusal when the raw direct-journal path bundles multiple operational events. */
  static PostingRejection.EntrySemanticsViolation rawJournalBundlesOperationalEvents(
      String selectorField, String selectorValue, Set<EconomicEventClass> containedTypedEvents) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Set<EconomicEventClass> requiredContainedTypedEvents =
        Set.copyOf(Objects.requireNonNull(containedTypedEvents, "containedTypedEvents"));
    return new PostingRejection.EntrySemanticsViolation(
        "raw-journal-bundles-operational-events",
        "lines",
        "%s '%s' bundles multiple operational event classes in one raw journal. Split it into %s."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                requiredContainedTypedEvents.stream()
                    .map(OperationId::forEconomicEventClass)
                    .map(ProtocolCatalog::operationName)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(" + "))));
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine(String selectorValue) {
    return rawJournalRequiresCashLine("entryKind", selectorValue);
  }

  /** Returns one refusal when a cash-basis raw adjustment omits every declared cash line. */
  static PostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine(
      String selectorField, String selectorValue) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    return new PostingRejection.EntrySemanticsViolation(
        "raw-journal-requires-cash-line",
        "lines[].accountCode",
        "%s '%s' is an adjustment on a cash-basis book, so at least one journal line must use a declared cash account."
            .formatted(requiredSelectorField, requiredSelectorValue));
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation openingWindowAccountNotPermitted(
      String selectorValue, AccountCode accountCode) {
    return openingWindowAccountNotPermitted("entryKind", selectorValue, accountCode);
  }

  /**
   * Returns one refusal when an opening-position request references a forbidden opening account.
   */
  static PostingRejection.EntrySemanticsViolation openingWindowAccountNotPermitted(
      String selectorField, String selectorValue, AccountCode accountCode) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "opening-window-account-not-permitted",
        "openingBalances[].accountCode",
        "%s '%s' uses openingBalances[].accountCode '%s', which is not permitted in the adoption opening window."
            .formatted(requiredSelectorField, requiredSelectorValue, accountCode.value()));
  }
}
