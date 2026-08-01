package dev.erst.fingrind.core;

/** Durable relationship that constrains an account-registry lifecycle action. */
public enum AccountRegistryDependency implements WireValue {
  /** One historical posting references the account. */
  POSTINGS,
  /** One live tax registration binds the account. */
  TAX_REGISTRATIONS,
  /** One child account names the account as its parent. */
  CHILD_ACCOUNTS,
  /** One declared contra account reduces the account. */
  CONTRA_ACCOUNTS;

  /** Returns the stable public wire value for this dependency kind. */
  @Override
  public String wireValue() {
    return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }
}
