package dev.erst.fingrind.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Enforces FinGrind production-module boundaries and CLI responsibility direction. */
@NullMarked
@AnalyzeClasses(
    packages = {
      "dev.erst.fingrind.core",
      "dev.erst.fingrind.contract",
      "dev.erst.fingrind.executor",
      "dev.erst.fingrind.sqlite",
      "dev.erst.fingrind.report.pdf",
      "dev.erst.fingrind.cli"
    },
    importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases")
final class FinGrindArchitectureTest {
  private FinGrindArchitectureTest() {}

  @ArchTest
  static final ArchRule productionLayers =
      layeredArchitecture()
          .consideringOnlyDependenciesInAnyPackage("dev.erst.fingrind..")
          .ensureAllClassesAreContainedInArchitecture()
          .layer("Core")
          .definedBy("dev.erst.fingrind.core..")
          .layer("Contract")
          .definedBy("dev.erst.fingrind.contract..")
          .layer("Executor")
          .definedBy("dev.erst.fingrind.executor..")
          .layer("SQLite")
          .definedBy("dev.erst.fingrind.sqlite..")
          .layer("Report PDF")
          .definedBy("dev.erst.fingrind.report.pdf..")
          .layer("CLI")
          .definedBy("dev.erst.fingrind.cli..")
          .whereLayer("Core")
          .mayNotAccessAnyLayer()
          .whereLayer("Contract")
          .mayOnlyAccessLayers("Core")
          .whereLayer("Executor")
          .mayOnlyAccessLayers("Core", "Contract")
          .whereLayer("SQLite")
          .mayOnlyAccessLayers("Core", "Contract", "Executor")
          .whereLayer("Report PDF")
          .mayOnlyAccessLayers("Core", "Contract", "Executor")
          .whereLayer("CLI")
          .mayOnlyAccessLayers("Core", "Contract", "Executor", "SQLite", "Report PDF");

  @ArchTest
  static final ArchRule cliRenderersDoNotReachIntoSqlite =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("Renderer")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.erst.fingrind.sqlite..");

  @ArchTest
  static final ArchRule cliParsersDoNotDependOnResponsibilityOutputs =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("Parser")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Renderer")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("ResponseWriter")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("CommandExecutor");

  @ArchTest
  static final ArchRule cliResponseWritersDoNotDependOnParsersOrExecutors =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("ResponseWriter")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Parser")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("CommandExecutor");

  @ArchTest
  static final ArchRule cliCommandExecutorsDoNotDependOnOtherResponsibilities =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("CommandExecutor")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("CommandExecutor")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Renderer")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Parser");

  @ArchTest
  static final ArchRule cliOutputRenderersDoNotDependOnOtherResponsibilities =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("OutputRenderer")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Parser")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("ResponseWriter")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("CommandExecutor");

  @ArchTest
  static final ArchRule cliJsonDoesNotExposeRuntimePathHints =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli.json..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("dev.erst.fingrind.contract.runtime.PublicPathHint");

  @ArchTest
  static final ArchRule primarySlicesAreFreeOfCycles =
      slices().matching("dev.erst.fingrind.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule bookkeepingContextsDoNotReachIntoEachOther =
      classes()
          .that()
          .resideInAPackage("dev.erst.fingrind.executor.bookkeeping..")
          .should(notDependOnAnotherBookkeepingContext());

  @ArchTest
  static final ArchRule bookkeepingContextsAreFreeOfCycles =
      slices().assignedFrom(bookkeepingContexts()).should().beFreeOfCycles();

  private static ArchCondition<JavaClass> notDependOnAnotherBookkeepingContext() {
    return new ArchCondition<>("depend only on its own bookkeeping context or shared support") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        bookkeepingContext(source)
            .ifPresent(
                sourceContext ->
                    source
                        .getDirectDependenciesFromSelf()
                        .forEach(
                            dependency ->
                                bookkeepingContext(dependency.getTargetClass())
                                    .filter(targetContext -> targetContext != sourceContext)
                                    .ifPresent(
                                        targetContext ->
                                            events.add(
                                                SimpleConditionEvent.violated(
                                                    source,
                                                    source.getName()
                                                        + " in "
                                                        + sourceContext.description()
                                                        + " must not depend on "
                                                        + dependency.getTargetClass().getName()
                                                        + " in "
                                                        + targetContext.description()
                                                        + ".")))));
      }
    };
  }

  private static SliceAssignment bookkeepingContexts() {
    return new SliceAssignment() {
      @Override
      public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
        return bookkeepingContext(javaClass)
            .map(context -> SliceIdentifier.of(context.description()))
            .orElseGet(SliceIdentifier::ignore);
      }

      @Override
      public String getDescription() {
        return "bookkeeping bounded contexts";
      }
    };
  }

  private static Optional<BookkeepingContext> bookkeepingContext(JavaClass javaClass) {
    return BookkeepingContext.from(javaClass);
  }

  /** Names the owned bookkeeping executor contexts protected by the direct dependency boundary. */
  private enum BookkeepingContext {
    ACCRUAL_CUTOFF("accrual cutoff", "AccrualCutoff", ""),
    FIXED_ASSETS("fixed assets", "FixedAsset", ""),
    FINANCING("financing", "Financing", ""),
    REALIZED_FOREIGN_EXCHANGE(
        "realized foreign exchange", "ForeignCurrency", "RealizedForeignExchange"),
    LATVIAN_PAYROLL("Latvian payroll", "LatvianPayroll", "");

    private static final String BOOKKEEPING_PACKAGE = "dev.erst.fingrind.executor.bookkeeping";
    private final String description;
    private final String primaryClassNameFragment;
    private final String alternateClassNameFragment;

    BookkeepingContext(
        String description, String primaryClassNameFragment, String alternateClassNameFragment) {
      this.description = description;
      this.primaryClassNameFragment = primaryClassNameFragment;
      this.alternateClassNameFragment = alternateClassNameFragment;
    }

    static Optional<BookkeepingContext> from(JavaClass javaClass) {
      if (!javaClass.getPackageName().startsWith(BOOKKEEPING_PACKAGE)) {
        return Optional.empty();
      }
      return java.util.Arrays.stream(values())
          .filter(context -> context.matches(javaClass.getSimpleName()))
          .findFirst();
    }

    String description() {
      return description;
    }

    private boolean matches(String className) {
      return className.contains(primaryClassNameFragment)
          || (!alternateClassNameFragment.isEmpty()
              && className.contains(alternateClassNameFragment));
    }
  }
}
