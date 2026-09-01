package dev.erst.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/** Proves every named architecture seam is present in the analyzed production graph. */
@NullMarked
class ArchitectureSeamCatalogTest {
  @Test
  void catalogEntriesResolveAgainstTheProductionArchitectureGraph() {
    Set<String> importedClassNames =
        new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ArchitectureSeamCatalog.PRODUCTION_PACKAGE)
                .stream()
                .map(javaClass -> javaClass.getName())
                .collect(Collectors.toUnmodifiableSet());
    Set<String> missingExactClasses =
        ArchitectureSeamCatalog.exactArchitectureClasses().stream()
            .filter(className -> !importedClassNames.contains(className))
            .collect(Collectors.toUnmodifiableSet());
    Set<String> unmatchedPrefixes =
        ArchitectureSeamCatalog.PREFIX_ARCHITECTURE_SEAMS.stream()
            .filter(
                prefix ->
                    importedClassNames.stream()
                        .noneMatch(className -> className.startsWith(prefix)))
            .collect(Collectors.toUnmodifiableSet());

    assertTrue(
        missingExactClasses.isEmpty(), () -> "Missing architecture seams: " + missingExactClasses);
    assertTrue(
        unmatchedPrefixes.isEmpty(), () -> "Unmatched architecture seams: " + unmatchedPrefixes);
  }
}
