package dev.erst.fingrind.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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
            .resideInAnyPackage("dev.erst.fingrind.sqlite.."));
  }

  @Test
  void cliParsersDoNotDependOnRenderersResponseWritersOrCommandExecutors() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("Parser")
            .should()
            .dependOnClassesThat(
                simpleNameStartsWithAny(
                    "CliDiscoveryOutputRenderer",
                    "CliFailureOutputRenderer",
                    "CliMutationOutputRenderer",
                    "CliPostingOutputRenderer",
                    "CliReportOutputRenderer",
                    "CliBookInspectionOutputRenderer",
                    "CliAccountBalanceOutputRenderer",
                    "CliAccountPageOutputRenderer",
                    "CliFailureResponseWriter",
                    "CliDiscoveryResponseWriter",
                    "CliMutationResponseWriter",
                    "CliBookReadResponseWriter",
                    "CliReportResponseWriter",
                    "CliPlanResponseWriter",
                    "CliAdministrativeCommandExecutor",
                    "CliDiscoveryCommandExecutor",
                    "CliMutationCommandExecutor",
                    "CliQueryCommandExecutor",
                    "CliReportCommandExecutor")));
  }

  @Test
  void cliResponseWritersDoNotDependOnParsersOrCommandExecutors() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("ResponseWriter")
            .should()
            .dependOnClassesThat(
                simpleNameStartsWithAny(
                    "CliAccountingEvidenceRequestParser",
                    "CliBookkeepingEntryRequestParser",
                    "CliDeclareAccountRequestParser",
                    "CliLedgerPlanParser",
                    "CliPostEntryRequestParser",
                    "CliPostingRequestParser",
                    "CliAdministrativeCommandExecutor",
                    "CliDiscoveryCommandExecutor",
                    "CliMutationCommandExecutor",
                    "CliQueryCommandExecutor",
                    "CliReportCommandExecutor")));
  }

  @Test
  void cliCommandExecutorsDoNotDependOnOtherCommandExecutors() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("CommandExecutor")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("CommandExecutor"));
  }

  @Test
  void cliCommandExecutorsDoNotDependOnRenderersOrParsers() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("CommandExecutor")
            .should()
            .dependOnClassesThat(
                simpleNameStartsWithAny(
                    "CliAccountingEvidenceRequestParser",
                    "CliBookkeepingEntryRequestParser",
                    "CliDeclareAccountRequestParser",
                    "CliLedgerPlanParser",
                    "CliPostEntryRequestParser",
                    "CliPostingRequestParser",
                    "CliAccountBalanceOutputRenderer",
                    "CliAccountPageOutputRenderer",
                    "CliBookInspectionOutputRenderer",
                    "CliDiscoveryOutputRenderer",
                    "CliFailureOutputRenderer",
                    "CliMutationOutputRenderer",
                    "CliPostingOutputRenderer",
                    "CliReportOutputRenderer")));
  }

  @Test
  void cliOutputRenderersDoNotDependOnParsersResponseWritersOrCommandExecutors() {
    check(
        noClasses()
            .that()
            .resideInAPackage("dev.erst.fingrind.cli..")
            .and()
            .haveSimpleNameEndingWith("OutputRenderer")
            .should()
            .dependOnClassesThat(
                simpleNameStartsWithAny(
                    "CliAccountingEvidenceRequestParser",
                    "CliBookkeepingEntryRequestParser",
                    "CliDeclareAccountRequestParser",
                    "CliLedgerPlanParser",
                    "CliPostEntryRequestParser",
                    "CliPostingRequestParser",
                    "CliFailureResponseWriter",
                    "CliDiscoveryResponseWriter",
                    "CliMutationResponseWriter",
                    "CliBookReadResponseWriter",
                    "CliReportResponseWriter",
                    "CliPlanResponseWriter",
                    "CliAdministrativeCommandExecutor",
                    "CliDiscoveryCommandExecutor",
                    "CliMutationCommandExecutor",
                    "CliQueryCommandExecutor",
                    "CliReportCommandExecutor")));
  }

  @Test
  void primarySlicesAreFreeOfCycles() {
    check(slices().matching("dev.erst.fingrind.(*)..").should().beFreeOfCycles());
  }

  private static DescribedPredicate<JavaClass> simpleNameStartsWithAny(String... prefixes) {
    return new DescribedPredicate<>("have one of the forbidden responsibility families") {
      @Override
      public boolean test(JavaClass input) {
        String simpleName = input.getSimpleName();
        for (String prefix : prefixes) {
          if (simpleName.startsWith(prefix)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  private static void check(ArchRule rule) {
    var classes =
        new com.tngtech.archunit.core.importer.ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.erst.fingrind");
    rule.check(classes);
  }
}
