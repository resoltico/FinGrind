package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.TemporalScopeArchetype;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shared temporal-scope labels, option names, and meaning tokens derived from one contract owner.
 */
final class CliTemporalScopeText {
  private static final RequestSurfaceFacts REQUEST_SURFACE =
      ProtocolCatalog.domain().requestSurface();

  private CliTemporalScopeText() {}

  static String summaryLabel(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).summaryLabel();
  }

  static String lowerLabel(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).lowerLabel();
  }

  static String upperLabel(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).upperLabel();
  }

  static String scopeKind(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).archetype().wireValue();
  }

  static String boundarySemantics(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).boundarySemantics();
  }

  static String firstOption(OperationId operationId) {
    return optionNames(operationId).getFirst();
  }

  static String secondOption(OperationId operationId) {
    List<String> optionNames = optionNames(operationId);
    if (optionNames.size() < 2) {
      throw new IllegalStateException(operationId + " does not publish a second temporal option.");
    }
    return optionNames.get(1);
  }

  static String lowerDateBoundaryMeaning(@Nullable LocalDate effectiveDateFrom) {
    RequestSurfaceFacts.TemporalScopeFacts facts =
        REQUEST_SURFACE.temporalScope(TemporalScopeArchetype.RANGED_FILTER);
    return effectiveDateFrom == null
        ? facts.omittedLowerBoundaryMeaning()
        : facts.selectedBoundaryMeaning();
  }

  static String upperDateBoundaryMeaning(@Nullable LocalDate effectiveDateTo) {
    RequestSurfaceFacts.TemporalScopeFacts facts =
        REQUEST_SURFACE.temporalScope(TemporalScopeArchetype.RANGED_FILTER);
    return effectiveDateTo == null
        ? facts.omittedUpperBoundaryMeaning()
        : facts.selectedBoundaryMeaning();
  }

  static String resolvedUpperDateBoundaryMeaning(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    RequestSurfaceFacts.TemporalScopeFacts facts =
        REQUEST_SURFACE.temporalScope(TemporalScopeArchetype.AS_OF_DATE);
    if (selectedEffectiveDateTo != null) {
      return facts.selectedBoundaryMeaning();
    }
    return resolvedEffectiveDateTo == null
        ? facts.emptyResultBoundaryMeaning()
        : facts.resolvedUpperBoundaryMeaning();
  }

  static List<String> optionNames(OperationId operationId) {
    return REQUEST_SURFACE.temporalScopeFor(operationId).optionNames();
  }
}
