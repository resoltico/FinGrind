package dev.erst.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Enforces FinGrind production-module boundaries and CLI responsibility direction. */
@NullMarked
@AnalyzeClasses(
    packages = ArchitectureSeamCatalog.PRODUCTION_PACKAGE,
    importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases")
final class FinGrindArchitectureTest {
  private static final String CRYPTOGRAPHIC_PRIMITIVE_TYPE_PATTERN =
      "java\\.security\\.(Signature|KeyPair|KeyPairGenerator|KeyFactory|MessageDigest|SecureRandom)"
          + "|java\\.security\\.spec\\.PKCS8EncodedKeySpec"
          + "|java\\.security(\\.interfaces)?\\..*Private.*Key.*";
  private static final Set<String> RAW_GENERIC_FAILURE_TYPES =
      Set.of(
          "java.lang.Throwable",
          "java.lang.Exception",
          "java.lang.RuntimeException",
          "java.lang.Error");

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
  static final ArchRule cliRenderersDoNotDependOnOtherResponsibilities =
      noClasses()
          .that()
          .resideInAPackage("dev.erst.fingrind.cli..")
          .and()
          .haveSimpleNameEndingWith("Renderer")
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
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLIC_PATH_HINT);

  @ArchTest
  static final ArchRule cryptographicPrimitivesAreConfinedToTheCryptoSeam =
      classes().should(dependOnCryptographicPrimitiveTypesOnlyInsideTheCryptoSeam());

  @ArchTest
  static final ArchRule privateKeysDoNotCrossPublicAdapterBoundaries =
      noClasses()
          .that()
          .resideInAnyPackage(
              "dev.erst.fingrind.contract..",
              "dev.erst.fingrind.cli..",
              "dev.erst.fingrind.report.pdf..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("java\\.security(\\.interfaces)?\\..*Private.*Key.*");

  @ArchTest
  static final ArchRule processStreamsAndEnvironmentAreConfinedToRuntimeIoSeams =
      classes().should(accessProcessStreamsAndEnvironmentOnlyInsideRuntimeIoSeams());

  @ArchTest
  static final ArchRule wallClockAccessIsConfinedToTheRuntimeClockSeam =
      classes().should(accessWallClockOnlyInsideTheRuntimeClockSeam());

  @ArchTest
  static final ArchRule durableMutationOwnersMustReachTheAttestationWrapper =
      classes().should(reachAttestationEvidenceFromEveryDurableMutationBoundary());

  @ArchTest
  static final ArchRule durableMutationWritersMustBeOwnedByAnAttestedBoundary =
      classes().should(attestEveryDurableMutationWriterCall());

  @ArchTest
  static final ArchRule journaledStagedProtectedBooksUseThePublicationTransactionPair =
      classes()
          .that()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.JOURNALED_STAGED_BACKUP_PAIR)
          .or()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.JOURNALED_STAGED_RESTORED_BOOK_PAIR)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_PAIR);

  @ArchTest
  static final ArchRule protectedBookPublicationPairUsesTheTransactionService =
      classes()
          .that()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_PAIR)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_SERVICE);

  @ArchTest
  static final ArchRule publicationTransactionCommitterUsesTheDurabilityRuntime =
      classes()
          .that()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_COMMITTER)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_RUNTIME);

  @ArchTest
  static final ArchRule publicationTransactionRuntimeUsesTheDirectoryDurabilitySeam =
      classes()
          .that()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_RUNTIME)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(
              ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_DIRECTORY_DURABILITY);

  @ArchTest
  static final ArchRule publicationTransactionDirectoryDurabilityUsesThePlatformDurabilityOwner =
      classes()
          .that()
          .haveFullyQualifiedName(
              ArchitectureSeamCatalog.PUBLICATION_TRANSACTION_DIRECTORY_DURABILITY)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(ArchitectureSeamCatalog.PRIVATE_OUTPUT_DIRECTORY_DURABILITY);

  @ArchTest
  static final ArchRule durableMutationCatalogReferencesAreTyped =
      classes().should(referenceTheTypedOperationCatalogAtEveryDurableMutationBoundary());

  @ArchTest
  static final ArchRule genericFailuresAreNeverConstructed =
      classes().should(notConstructRawGenericFailureTypes());

  @ArchTest
  static final ArchRule foreignMemoryIsConfinedToNativeInteropSeams =
      classes().should(dependOnForeignMemoryOnlyInsideNativeInteropSeams());

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

  private static ArchCondition<JavaClass>
      dependOnCryptographicPrimitiveTypesOnlyInsideTheCryptoSeam() {
    return new ArchCondition<>(
        "depend on cryptographic primitive types only inside the crypto seam") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (belongsToCryptographicPrimitiveSeam(source)) {
          return;
        }
        source.getDirectDependenciesFromSelf().stream()
            .filter(
                dependency ->
                    dependency
                        .getTargetClass()
                        .getName()
                        .matches(CRYPTOGRAPHIC_PRIMITIVE_TYPE_PATTERN))
            .forEach(
                dependency ->
                    events.add(
                        SimpleConditionEvent.violated(
                            source,
                            source.getName()
                                + " must not depend on cryptographic primitive type "
                                + dependency.getTargetClass().getName()
                                + ".")));
      }
    };
  }

  private static boolean belongsToCryptographicPrimitiveSeam(JavaClass source) {
    return ArchitectureSeamCatalog.CRYPTOGRAPHIC_PRIMITIVE_SEAM.stream()
        .anyMatch(
            owner -> source.getName().equals(owner) || source.getName().startsWith(owner + "$"));
  }

  private static ArchCondition<JavaClass>
      accessProcessStreamsAndEnvironmentOnlyInsideRuntimeIoSeams() {
    return new ArchCondition<>(
        "access process streams, console, or environment only inside runtime I/O seams") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (belongsToRuntimeIoSeam(source)) {
          return;
        }
        source.getFieldAccessesFromSelf().stream()
            .filter(access -> targetsSystemMember(access, Set.of("in", "out", "err")))
            .forEach(access -> reportRuntimeIoViolation(source, access, events));
        source.getMethodCallsFromSelf().stream()
            .filter(access -> targetsSystemMember(access, Set.of("console", "getenv")))
            .forEach(access -> reportRuntimeIoViolation(source, access, events));
      }
    };
  }

  private static ArchCondition<JavaClass> accessWallClockOnlyInsideTheRuntimeClockSeam() {
    return new ArchCondition<>("access the wall clock only inside the runtime clock seam") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (belongsToRuntimeClockSeam(source)) {
          return;
        }
        source.getMethodCallsFromSelf().stream()
            .filter(FinGrindArchitectureTest::isWallClockAccess)
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            source,
                            source.getName()
                                + " must receive time through an injected Clock rather than "
                                + access.getDescription()
                                + ".")));
      }
    };
  }

  private static ArchCondition<JavaClass>
      reachAttestationEvidenceFromEveryDurableMutationBoundary() {
    return new ArchCondition<>(
        "reach the attestation evidence wrapper from every durable mutation boundary") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (!ArchitectureSeamCatalog.MUTATION_ATTESTATION_BOUNDARIES.contains(source.getName())) {
          return;
        }
        if (!callsAttestationEvidenceStore(source)) {
          events.add(
              SimpleConditionEvent.violated(
                  source,
                  source.getName()
                      + " must directly call "
                      + ArchitectureSeamCatalog.ATTESTATION_EVIDENCE_STORE
                      + "."));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> attestEveryDurableMutationWriterCall() {
    return new ArchCondition<>(
        "invoke durable SQLite mutation writers only from an attestation evidence boundary") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (!callsDurableMutationWriter(source)
            || ArchitectureSeamCatalog.DURABLE_MUTATION_WRITERS.contains(source.getName())
            || ArchitectureSeamCatalog.DURABLE_MUTATION_WRITER_HELPERS.contains(source.getName())) {
          return;
        }
        if (!ArchitectureSeamCatalog.MUTATION_ATTESTATION_BOUNDARIES.contains(source.getName())) {
          events.add(
              SimpleConditionEvent.violated(
                  source,
                  source.getName()
                      + " directly invokes a durable SQLite mutation writer but is not an "
                      + "attestation mutation boundary."));
          return;
        }
        if (!callsAttestationEvidenceStore(source)) {
          events.add(
              SimpleConditionEvent.violated(
                  source,
                  source.getName()
                      + " directly invokes a durable SQLite mutation writer without directly "
                      + "calling "
                      + ArchitectureSeamCatalog.ATTESTATION_EVIDENCE_STORE
                      + "."));
        }
      }
    };
  }

  private static boolean callsDurableMutationWriter(JavaClass source) {
    return source.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                ArchitectureSeamCatalog.DURABLE_MUTATION_WRITERS.contains(
                    call.getTargetOwner().getName()));
  }

  private static boolean callsAttestationEvidenceStore(JavaClass source) {
    return source.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                ArchitectureSeamCatalog.ATTESTATION_EVIDENCE_STORE.equals(
                    call.getTargetOwner().getName()));
  }

  private static ArchCondition<JavaClass>
      referenceTheTypedOperationCatalogAtEveryDurableMutationBoundary() {
    return new ArchCondition<>(
        "reference the typed attestation operation catalog at every durable mutation boundary") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (!ArchitectureSeamCatalog.TYPED_OPERATION_CATALOG_BOUNDARIES.contains(
            source.getName())) {
          return;
        }
        boolean referencesOperationKind =
            source.getDirectDependenciesFromSelf().stream()
                .anyMatch(
                    dependency ->
                        ArchitectureSeamCatalog.ATTESTATION_OPERATION_KIND.equals(
                            dependency.getTargetClass().getName()));
        if (!referencesOperationKind) {
          events.add(
              SimpleConditionEvent.violated(
                  source,
                  source.getName()
                      + " must use "
                      + ArchitectureSeamCatalog.ATTESTATION_OPERATION_KIND
                      + " instead of a raw operation catalog literal."));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notConstructRawGenericFailureTypes() {
    return new ArchCondition<>("not construct raw generic failure types") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        source.getConstructorCallsFromSelf().stream()
            .filter(call -> RAW_GENERIC_FAILURE_TYPES.contains(call.getTargetOwner().getName()))
            .filter(call -> !isNamedDomainFailureSuperclassCall(source, call))
            .forEach(
                call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            source,
                            source.getName()
                                + " must use a domain-specific failure type rather than "
                                + call.getTargetOwner().getName()
                                + ".")));
      }
    };
  }

  private static boolean belongsToRuntimeIoSeam(JavaClass source) {
    return ArchitectureSeamCatalog.RUNTIME_IO_SEAM.stream()
        .anyMatch(
            owner -> source.getName().equals(owner) || source.getName().startsWith(owner + "$"));
  }

  private static boolean belongsToRuntimeClockSeam(JavaClass source) {
    return ArchitectureSeamCatalog.RUNTIME_CLOCK_SEAM.equals(source.getName())
        || source.getName().startsWith(ArchitectureSeamCatalog.RUNTIME_CLOCK_SEAM + "$");
  }

  private static boolean targetsSystemMember(JavaAccess<?> access, Set<String> memberNames) {
    return "java.lang.System".equals(access.getTargetOwner().getName())
        && memberNames.contains(access.getTarget().getName());
  }

  private static boolean isWallClockAccess(JavaMethodCall access) {
    String owner = access.getTargetOwner().getName();
    String member = access.getTarget().getName();
    return ("java.time.Clock".equals(owner) && member.startsWith("system"))
        || (Set.of(
                    "java.time.Instant",
                    "java.time.LocalDate",
                    "java.time.LocalDateTime",
                    "java.time.OffsetDateTime",
                    "java.time.ZonedDateTime")
                .contains(owner)
            && "now".equals(member)
            && hasNoArguments(access))
        || ("java.lang.System".equals(owner)
            && Set.of("currentTimeMillis", "nanoTime").contains(member));
  }

  private static boolean hasNoArguments(JavaMethodCall access) {
    return access
        .getTarget()
        .resolveMember()
        .map(method -> method.getRawParameterTypes().isEmpty())
        .orElseGet(() -> access.getTarget().getFullName().endsWith("()"));
  }

  private static boolean isNamedDomainFailureSuperclassCall(
      JavaClass source, JavaConstructorCall call) {
    return call.getOrigin().isConstructor()
        && (source.getSimpleName().endsWith("Exception")
            || source.getSimpleName().endsWith("Failure"));
  }

  private static void reportRuntimeIoViolation(
      JavaClass source, JavaAccess<?> access, ConditionEvents events) {
    events.add(
        SimpleConditionEvent.violated(
            source,
            source.getName()
                + " must receive process I/O through an explicit runtime seam rather than "
                + access.getDescription()
                + "."));
  }

  private static ArchCondition<JavaClass> dependOnForeignMemoryOnlyInsideNativeInteropSeams() {
    return new ArchCondition<>("depend on foreign-memory types only inside native interop seams") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (belongsToNativeInteropSeam(source)) {
          return;
        }
        source.getDirectDependenciesFromSelf().stream()
            .filter(
                dependency ->
                    dependency.getTargetClass().getPackageName().startsWith("java.lang.foreign"))
            .forEach(
                dependency ->
                    events.add(
                        SimpleConditionEvent.violated(
                            source,
                            source.getName()
                                + " must not depend on foreign-memory type "
                                + dependency.getTargetClass().getName()
                                + ".")));
      }
    };
  }

  private static boolean belongsToNativeInteropSeam(JavaClass source) {
    return source.getPackageName().startsWith("dev.erst.fingrind.sqlite")
        || ArchitectureSeamCatalog.PRIVATE_OUTPUT_DIRECTORY_NATIVE_INTEROP_SEAM.contains(
            source.getName())
        || source
            .getName()
            .startsWith(ArchitectureSeamCatalog.PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT + "$")
        || source
            .getName()
            .startsWith(ArchitectureSeamCatalog.WINDOWS_PRIVATE_OUTPUT_FILE_NATIVE_INTEROP_PREFIX)
        || ArchitectureSeamCatalog.WINDOWS_CURRENT_TOKEN_ACL_PRINCIPAL_MATCHER.equals(
            source.getName())
        || source
            .getName()
            .startsWith(ArchitectureSeamCatalog.WINDOWS_CURRENT_TOKEN_ACL_PRINCIPAL_MATCHER + "$")
        || ArchitectureSeamCatalog.WINDOWS_TRUSTED_ACL_PRINCIPAL_MATCHER.equals(source.getName())
        || source
            .getName()
            .startsWith(ArchitectureSeamCatalog.WINDOWS_TRUSTED_ACL_PRINCIPAL_MATCHER + "$")
        || ArchitectureSeamCatalog.WINDOWS_PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT.equals(
            source.getName())
        || source
            .getName()
            .startsWith(
                ArchitectureSeamCatalog.WINDOWS_PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT + "$");
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
