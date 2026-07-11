package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.InventoryPostingResolution;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-boundary semantic validation for the published post-entry command surface. */
final class PostEntrySemanticsPolicy {
  private static final PostEntrySemanticsPolicy CURRENT_KERNEL = new PostEntrySemanticsPolicy();
  private static final RequestSurfaceFacts REQUEST_SURFACE =
      ProtocolCatalog.domain().requestSurface();

  private PostEntrySemanticsPolicy() {}

  static PostEntrySemanticsPolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostEntryCommand command, PostingValidationStore book) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(book, "book");
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    PostEntrySemanticContext authoredSemanticContext =
        PostEntrySemanticContext.from(command.entry(), REQUEST_SURFACE);
    BookTemplateId bookTemplateId =
        book.requireInitializedBookIdentity().bookDoctrine().bookTemplateId();
    PostEntryAdmissionSupport.validateTradingTemplateEntryAdmission(
        violations,
        command.entry(),
        bookTemplateId,
        authoredSemanticContext.selectorField(),
        authoredSemanticContext.selectorValue());
    Map<AccountCode, RegisteredAccount> authoredAccounts =
        book.findAccounts(authoredSemanticContext.referencedAccounts());
    boolean allAuthoredAccountsResolved =
        ResolvedJournalSupport.canResolveAllAccounts(
            authoredSemanticContext.referencedAccounts(), authoredAccounts);
    PostEntryRoleAccountSemantics.validate(
        violations,
        authoredAccounts,
        command.entry(),
        authoredSemanticContext.selectorField(),
        authoredSemanticContext.selectorValue());
    validateAdmissionByVerbAndBasisBeforeResolution(
        violations,
        command.entry().entryKind(),
        book.requireInitializedBookIdentity().bookDoctrine().accountingBasis(),
        authoredSemanticContext.selectorField(),
        authoredSemanticContext.selectorValue());
    boolean roleSemanticsAccepted = violations.isEmpty();
    PostEntryRequestValidationSupport.requireSourceDocumentTypes(
        violations,
        authoredSemanticContext.selectorField(),
        authoredSemanticContext.selectorValue(),
        authoredSemanticContext.sourceDocumentTypes(),
        command.evidence().sourceDocuments());
    int violationCountBeforeTaxValidation = violations.size();
    TaxEntrySemantics.validate(
        violations,
        book,
        command.entry(),
        authoredSemanticContext.selectorField(),
        authoredSemanticContext.selectorValue());
    PostEntryResolutionSupport.ResolutionOutcome resolutionOutcome =
        allAuthoredAccountsResolved
            ? PostEntryResolutionSupport.resolveAfterTaxValidation(
                command.entry(), book, violations, violationCountBeforeTaxValidation)
            : new PostEntryResolutionSupport.ResolutionOutcome(
                InventoryPostingResolution.withoutInventory(command.entry()), Optional.empty());
    BookkeepingEntry resolvedEntry = resolutionOutcome.entry();
    if (resolutionOutcome.rejection().isPresent()
        && !(resolutionOutcome.rejection().orElseThrow()
            instanceof BookkeepingPostingRejection.AccountStateViolations)) {
      return resolutionOutcome.rejection();
    }
    PostEntrySemanticContext resolvedSemanticContext =
        PostEntrySemanticContext.from(resolvedEntry, REQUEST_SURFACE);
    Map<AccountCode, RegisteredAccount> resolvedAccounts =
        book.findAccounts(resolvedSemanticContext.referencedAccounts());
    if (ResolvedJournalSupport.canResolveAllAccounts(
        resolvedSemanticContext.referencedAccounts(), resolvedAccounts)) {
      BookkeepingPostingRejection.EntrySemanticsViolation rawJournalInventoryViolation =
          PostEntryRequestValidationSupport.rawJournalTouchesInventory(
              resolvedAccounts,
              command.entry().entryKind(),
              resolvedSemanticContext.selectorField(),
              resolvedSemanticContext.selectorValue(),
              resolvedSemanticContext.referencedAccounts());
      if (rawJournalInventoryViolation != null) {
        violations.add(rawJournalInventoryViolation);
        return Optional.of(new BookkeepingPostingRejection.EntrySemanticsViolations(violations));
      }
    }
    if (canResolveResolvedJournal(resolvedEntry)
        && ResolvedJournalSupport.canResolveAllAccounts(
            resolvedSemanticContext.referencedAccounts(), resolvedAccounts)) {
      ResolvedJournal resolvedJournal =
          ResolvedJournalSupport.resolve(resolvedEntry, command.evidence(), resolvedAccounts);
      PostEntryRequestValidationSupport.requireOpeningWindowAccounts(
          violations,
          resolvedAccounts,
          resolvedEntry,
          resolvedSemanticContext.selectorField(),
          resolvedSemanticContext.selectorValue(),
          resolvedSemanticContext.referencedAccounts());
      if (roleSemanticsAccepted
          && !(command.entry()
              instanceof dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.DirectJournal)) {
        assertVerbClass(command.entry().entryKind(), resolvedJournal);
      }
      validateEvidence(
          violations,
          resolvedSemanticContext.selectorField(),
          resolvedSemanticContext.selectorValue(),
          resolvedJournal);
      validateAdmissionByVerbAndBasis(
          violations,
          command.entry().entryKind(),
          book.requireInitializedBookIdentity().bookDoctrine().accountingBasis(),
          resolvedSemanticContext.selectorField(),
          resolvedSemanticContext.selectorValue(),
          resolvedJournal);
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BookkeepingPostingRejection.EntrySemanticsViolations(violations));
  }

  private static void validateEvidence(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    PostEntryAdmissionSupport.validateEvidence(
        violations, selectorField, selectorValue, resolvedJournal);
  }

  private static void validateAdmissionByVerbAndBasis(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntryKind entryKind,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      rawAdmission(violations, accountingBasis, selectorField, selectorValue, resolvedJournal);
      return;
    }
    PostEntryAdmissionSupport.validateAdmissionByVerbAndBasis(
        violations, entryKind, accountingBasis, selectorField, selectorValue, resolvedJournal);
  }

  private static void validateAdmissionByVerbAndBasisBeforeResolution(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntryKind entryKind,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue) {
    PostEntryAdmissionSupport.validateAdmissionByVerbAndBasisBeforeResolution(
        violations, entryKind, accountingBasis, selectorField, selectorValue);
  }

  private static void rawAdmission(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    PostEntryAdmissionSupport.rawAdmission(
        violations, accountingBasis, selectorField, selectorValue, resolvedJournal);
  }

  private static void assertVerbClass(
      BookkeepingEntryKind entryKind, ResolvedJournal resolvedJournal) {
    PostEntryAdmissionSupport.assertVerbClass(entryKind, resolvedJournal);
  }

  private static boolean canResolveResolvedJournal(BookkeepingEntry entry) {
    return PostEntryAdmissionSupport.canResolveResolvedJournal(entry);
  }
}
