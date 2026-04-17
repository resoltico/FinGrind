package dev.erst.fingrind.contract.protocol;

/** Core-owned structured facts describing currency behavior in posting requests. */
public record CurrencyFacts(String scope, String multiCurrencyStatus, String description) {}
