package dev.erst.fingrind.contract.protocol;

/** Core-owned structured facts describing the FinGrind book model and its hard limits. */
public record BookModelFacts(
    String boundary,
    String entityScope,
    String filesystem,
    String credential,
    String initialization,
    String accountRegistry,
    String migration,
    String currencyScope) {}
