package dev.erst.fingrind.contract.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns the ordered filesystem evidence exposed by a protected-book pair-publication failure. */
final class ContractPairPublicationPaths {
  private ContractPairPublicationPaths() {}

  static ContractFailurePaths forPairPublication(
      ContractFailureDetails.PairPublication pairPublication) {
    ContractFailureDetails.PairPublication checkedPairPublication =
        Objects.requireNonNull(pairPublication, "pairPublication");
    List<Path> relatedPaths = new ArrayList<>();
    relatedPaths.add(checkedPairPublication.generatedSecretTarget().path());
    if (checkedPairPublication.pairPublicationRetention() != null) {
      relatedPaths.add(
          checkedPairPublication
              .pairPublicationRetention()
              .bookPublication()
              .retention()
              .retainedStagePath());
      relatedPaths.add(
          checkedPairPublication
              .pairPublicationRetention()
              .generatedSecretPublication()
              .retention()
              .retainedStagePath());
    }
    return new ContractFailurePaths(
        checkedPairPublication.bookTarget().path(), List.copyOf(relatedPaths));
  }
}
