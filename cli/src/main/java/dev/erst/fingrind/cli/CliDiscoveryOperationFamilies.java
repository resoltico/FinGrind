package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import java.util.Set;

/** Owns the operation families shared by human command-discovery guidance. */
final class CliDiscoveryOperationFamilies {
  private static final Set<OperationId> BOOK_READ_OPERATIONS =
      Set.of(
          OperationId.ACCOUNT_BALANCE,
          OperationId.ACCOUNT_LEDGER,
          OperationId.TRIAL_BALANCE,
          OperationId.FINANCIAL_POSITION,
          OperationId.INVENTORY_VALUATION,
          OperationId.INCOME_STATEMENT,
          OperationId.CHANGES_IN_EQUITY,
          OperationId.PERIOD_SUMMARY,
          OperationId.TAX_OBLIGATION,
          OperationId.LIST_POSTINGS,
          OperationId.GET_POSTING);

  private CliDiscoveryOperationFamilies() {}

  static boolean isBookRead(OperationId operationId) {
    return BOOK_READ_OPERATIONS.contains(operationId);
  }

  static boolean isEntryRequest(OperationId operationId) {
    return ProtocolPostingRequestTopics.acceptsAnyEntryKind(operationId)
        || ProtocolPostingRequestTopics.requiredEntryKind(operationId).isPresent();
  }

  static boolean hasTemporalScope(OperationId operationId) {
    return ProtocolCatalog.domain().requestSurface().hasTemporalScopeFor(operationId);
  }
}
