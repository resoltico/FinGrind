package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks the canonical machine location facts carried by deterministic failures. */
class ContractFailurePathsTest {
  @Test
  void canonicalizesPrimaryAndRelatedPathsAndRejectsAmbiguousLists() {
    Path primary = Path.of("books", "entity.sqlite");
    Path related = Path.of("keys", "entity.key");

    ContractFailurePaths paths = new ContractFailurePaths(primary, List.of(related));

    assertEquals(primary.toAbsolutePath().normalize(), paths.path());
    assertEquals(List.of(related.toAbsolutePath().normalize()), paths.relatedPaths());
    assertThrows(
        IllegalArgumentException.class, () -> new ContractFailurePaths(primary, List.of(primary)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractFailurePaths(primary, List.of(related, related)));
  }

  @Test
  void primaryFactoryAndDescriptorFactoryCarryOneCanonicalLocation() {
    Path primary = Path.of("books", "entity.sqlite");

    ContractFailurePaths paths = ContractFailurePaths.primary(primary);
    var failure =
        ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.failureAt(
            primary, "The protected-book path is invalid.", null, null);

    assertEquals(primary.toAbsolutePath().normalize(), paths.path());
    assertEquals(List.of(), paths.relatedPaths());
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH, failure.descriptor());
    assertEquals(paths, failure.paths());
  }
}
