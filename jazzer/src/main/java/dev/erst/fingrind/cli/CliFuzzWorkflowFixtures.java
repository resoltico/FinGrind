package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.AttestationMutationAuthorization;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Workflow and lifecycle fixtures shared by Jazzer harnesses. */
public final class CliFuzzWorkflowFixtures {
  private static final UUID ATTESTATION_PRINCIPAL_ID =
      UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final String ATTESTATION_PASSPHRASE = "fingrind-jazzer-attestation-passphrase";
  private static final FuzzAttestationCredential ATTESTATION_CREDENTIAL =
      createAttestationCredential();

  private CliFuzzWorkflowFixtures() {}

  record FuzzAttestationCredential(
      AttestationFounderInput founderInput, AttestationCredentialSource credentialSource) {
    FuzzAttestationCredential {
      Objects.requireNonNull(founderInput, "founderInput must not be null");
      Objects.requireNonNull(credentialSource, "credentialSource must not be null");
    }
  }

  /** Creates the private temporary directory holding one fuzz attestation credential. */
  @FunctionalInterface
  interface AttestationWorkspaceCreator {
    /** Creates the private credential workspace. */
    Path create() throws IOException;
  }

  /** Returns the canonical book identity used by Jazzer lifecycle setup. */
  public static BookIdentity bookIdentity() {
    return bookIdentity(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical book identity used by Jazzer lifecycle setup for one currency. */
  public static BookIdentity bookIdentity(CurrencyUnit functionalCurrency) {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        Objects.requireNonNull(functionalCurrency, "functionalCurrency"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup. */
  public static OpenBookCommand openBookCommand() {
    return openBookCommand(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup for one currency. */
  public static OpenBookCommand openBookCommand(CurrencyUnit functionalCurrency) {
    return new OpenBookCommand(
        bookIdentity(functionalCurrency), List.of(ATTESTATION_CREDENTIAL.founderInput()));
  }

  /** Returns the credential source that authorizes mutations in one fuzzed protected book. */
  public static List<AttestationCredentialSource> attestationCredentialSources() {
    return List.of(ATTESTATION_CREDENTIAL.credentialSource());
  }

  /** Runs one fuzz fixture mutation through the production custody-confined authorization seam. */
  public static <T> T withAttestationAuthorization(
      Function<AttestationOperationAuthorizer, T> action) {
    return AttestationMutationAuthorization.withAuthorizer(attestationCredentialSources(), action);
  }

  /** Creates the fixed-clock administration service used by lifecycle-aware harnesses. */
  public static <T extends BookLifecycleReader & BookAdministrationStore & AccountCatalogStore>
      BookAdministrationService administrationService(T bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    return new BookAdministrationService(
        bookStore, bookStore, bookStore, CliFuzzFixtures.fixedClock());
  }

  /** Creates the fixed-clock posting service used by workflow fuzz harnesses and replay. */
  public static PostingApplicationService postingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(validationStore, "validationStore must not be null");
    Objects.requireNonNull(commitStore, "commitStore must not be null");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator must not be null");
    return new PostingApplicationService(
        validationStore, commitStore, postingIdGenerator, CliFuzzFixtures.fixedClock());
  }

  /** Opens one book and fails fast if lifecycle setup drifts unexpectedly. */
  public static void openBook(BookAdministrationService administrationService) {
    openBook(administrationService, CurrencyUnit.of("EUR"));
  }

  /** Opens one book in the supplied functional currency and fails fast on lifecycle drift. */
  public static void openBook(
      BookAdministrationService administrationService, CurrencyUnit functionalCurrency) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    OpenBookResult result =
        BookkeepingPublishedLanguageTranslator.toPublished(
            administrationService.openAttestedBook(
                bookIdentity(functionalCurrency),
                AttestationGenesisFactory.create(
                    bookIdentity(functionalCurrency),
                    CliFuzzFixtures.fixedClock().instant(),
                    List.of(ATTESTATION_CREDENTIAL.founderInput()))));
    OpenBookResult.Opened opened =
        switch (result) {
          case OpenBookResult.Opened accepted -> accepted;
          case OpenBookResult.Rejected _ ->
              throw new IllegalStateException("Lifecycle setup failed to initialize the book.");
        };
    if (!opened.initializedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Lifecycle setup used an unexpected initialized-at instant.");
    }
  }

  /** Runs one posting preflight through the internal bookkeeping translation boundary. */
  public static PreflightEntryResult preflight(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.preflight(command);
  }

  /** Runs one posting commit through the internal bookkeeping translation boundary. */
  public static CommitEntryResult commit(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return withAttestationAuthorization(
        attestationAuthorizer -> applicationService.commit(command, attestationAuthorizer));
  }

  private static FuzzAttestationCredential createAttestationCredential() {
    return createAttestationCredential(
        () -> Files.createTempDirectory("fingrind-jazzer-attestation-"));
  }

  static FuzzAttestationCredential createAttestationCredential(
      AttestationWorkspaceCreator workspaceCreator) {
    Objects.requireNonNull(workspaceCreator, "workspaceCreator must not be null");
    try {
      Path root = workspaceCreator.create();
      Path keyFile = root.resolve("founder.fgatk");
      Path passphraseFile = root.resolve("founder.passphrase");
      root.toFile().deleteOnExit();
      keyFile.toFile().deleteOnExit();
      passphraseFile.toFile().deleteOnExit();
      Files.writeString(passphraseFile, ATTESTATION_PASSPHRASE, StandardCharsets.UTF_8);
      char[] passphrase = ATTESTATION_PASSPHRASE.toCharArray();
      try {
        AttestationKeyFiles.create(keyFile, passphrase);
      } finally {
        Arrays.fill(passphrase, '\0');
      }
      return new FuzzAttestationCredential(
          new AttestationFounderInput(
              dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
              ATTESTATION_PRINCIPAL_ID,
              keyFile,
              passphraseFile),
          new AttestationCredentialSource(
              dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
              ATTESTATION_PRINCIPAL_ID,
              keyFile,
              passphraseFile));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not prepare the Jazzer attestation credential.", exception);
    }
  }

  /**
   * Translates an optional stored posting from the bookkeeping model into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      Optional<StoredRequestPosting> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    return storedPosting
        .map(StoredRequestPosting::postingFact)
        .map(BookkeepingPublishedLanguageTranslator::toPublished);
  }

  /** Loads one stored posting and translates it into the public fact shape. */
  public static Optional<PostingFact> publishedStoredPosting(
      PostingLookupStore bookStore, dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return publishedStoredPosting(bookStore.findExistingPosting(idempotencyKey));
  }
}
