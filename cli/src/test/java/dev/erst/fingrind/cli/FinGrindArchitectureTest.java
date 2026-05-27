package dev.erst.fingrind.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Enforces the primary FinGrind module boundaries and prevents cross-layer accumulation points. */
final class FinGrindArchitectureTest {
  @Test
  void coreDoesNotDependOnHigherLayers() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "dev.erst.fingrind.contract..",
                "dev.erst.fingrind.executor..",
                "dev.erst.fingrind.sqlite..",
                "dev.erst.fingrind.cli..",
                "dev.erst.fingrind.report.pdf.."));
  }

  @Test
  void contractDoesNotDependOnExecutionOrAdapters() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.contract..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "dev.erst.fingrind.executor..",
                "dev.erst.fingrind.sqlite..",
                "dev.erst.fingrind.cli..",
                "dev.erst.fingrind.report.pdf.."));
  }

  @Test
  void executorDoesNotDependOnAdapters() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.executor..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.erst.fingrind.sqlite..", "dev.erst.fingrind.cli.."));
  }

  @Test
  void sqliteDoesNotDependOnCliOrPdf() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.sqlite..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.erst.fingrind.cli..", "dev.erst.fingrind.report.pdf.."));
  }

  @Test
  void pdfDoesNotDependOnCliOrSqlite() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.report.pdf..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.erst.fingrind.cli..", "dev.erst.fingrind.sqlite.."));
  }

  @Test
  void cliRenderersDoNotReachIntoSqlite() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("Renderer")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.erst.fingrind.sqlite..", "dev.erst.fingrind.sqlite.secret.."));
  }

  @Test
  void primarySlicesAreFreeOfCycles() {
    check(slices().matching("dev.erst.fingrind.(*)..").should().beFreeOfCycles());
  }

  private static void check(ArchRule rule) {
    var classes =
        new com.tngtech.archunit.core.importer.ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.erst.fingrind");
    rule.check(classes);
  }
}
