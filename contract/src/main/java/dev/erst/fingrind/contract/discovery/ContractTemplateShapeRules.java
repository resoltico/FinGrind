package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.Map;

/** Canonical template-shape policy tables for ledger-plan steps and assertions. */
final class ContractTemplateShapeRules {
  private ContractTemplateShapeRules() {}

  static Map<LedgerStepKind, ContractTemplateStepShapeRequirements> stepShapeRequirements() {
    return Map.ofEntries(
        Map.entry(
            LedgerStepKind.OPEN_BOOK,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.INSPECT_BOOK,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.DECLARE_ACCOUNT,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.PREFLIGHT_ENTRY,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.POST_ENTRY,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.LIST_ACCOUNTS,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.OPTIONAL,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.LIST_POSTINGS,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.OPTIONAL,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.ACCOUNT_BALANCE,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                true)),
        Map.entry(
            LedgerStepKind.GET_POSTING,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED,
                false)),
        Map.entry(
            LedgerStepKind.ASSERT,
            new ContractTemplateStepShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                false)));
  }

  static Map<LedgerAssertionKind, ContractTemplateAssertionShapeRequirements>
      assertionShapeRequirements() {
    return Map.ofEntries(
        Map.entry(
            LedgerAssertionKind.ACCOUNT_DECLARED,
            new ContractTemplateAssertionShapeRequirements(
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN)),
        Map.entry(
            LedgerAssertionKind.ACCOUNT_ACTIVE,
            new ContractTemplateAssertionShapeRequirements(
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN)),
        Map.entry(
            LedgerAssertionKind.POSTING_EXISTS,
            new ContractTemplateAssertionShapeRequirements(
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED)),
        Map.entry(
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
            new ContractTemplateAssertionShapeRequirements(
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.FORBIDDEN,
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.REQUIRED,
                ContractTemplateFieldPresence.FORBIDDEN)));
  }
}
