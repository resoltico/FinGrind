package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Core-owned structured facts describing the FinGrind book model and its hard limits. */
public record BookModelFacts(
    BookBoundaryFact boundaryFact,
    BookEntityScopeFact entityScopeFact,
    BookFilesystemFact filesystemFact,
    BookCredentialFact credentialFact,
    BookInitializationFact initializationFact,
    BookAccountRegistryFact accountRegistryFact,
    BookCurrencyScopeFact currencyScopeFact) {
  /** Validates one book-model facts payload. */
  public BookModelFacts {
    boundaryFact = ContractDescriptorValidation.requireValue(boundaryFact, "boundaryFact");
    entityScopeFact = ContractDescriptorValidation.requireValue(entityScopeFact, "entityScopeFact");
    filesystemFact = ContractDescriptorValidation.requireValue(filesystemFact, "filesystemFact");
    credentialFact = ContractDescriptorValidation.requireValue(credentialFact, "credentialFact");
    initializationFact =
        ContractDescriptorValidation.requireValue(initializationFact, "initializationFact");
    accountRegistryFact =
        ContractDescriptorValidation.requireValue(accountRegistryFact, "accountRegistryFact");
    currencyScopeFact =
        ContractDescriptorValidation.requireValue(currencyScopeFact, "currencyScopeFact");
  }

  /** Convenience constructor for one inlined book-model fact set. */
  public BookModelFacts(
      String boundary,
      String entityScope,
      String filesystem,
      String credential,
      String initialization,
      String accountRegistry,
      String currencyScope) {
    this(
        new BookBoundaryFact(boundary),
        new BookEntityScopeFact(entityScope),
        new BookFilesystemFact(filesystem),
        new BookCredentialFact(credential),
        new BookInitializationFact(initialization),
        new BookAccountRegistryFact(accountRegistry),
        new BookCurrencyScopeFact(currencyScope));
  }

  /** Returns the canonical boundary wording for this book model. */
  public String boundary() {
    return boundaryFact.value();
  }

  /** Returns the canonical accounting-entity-scope wording for this book model. */
  public String entityScope() {
    return entityScopeFact.value();
  }

  /** Returns the canonical filesystem wording for this book model. */
  public String filesystem() {
    return filesystemFact.value();
  }

  /** Returns the canonical credential wording for this book model. */
  public String credential() {
    return credentialFact.value();
  }

  /** Returns the canonical initialization wording for this book model. */
  public String initialization() {
    return initializationFact.value();
  }

  /** Returns the canonical account-registry wording for this book model. */
  public String accountRegistry() {
    return accountRegistryFact.value();
  }

  /** Returns the canonical currency-scope wording for this book model. */
  public String currencyScope() {
    return currencyScopeFact.value();
  }
}
