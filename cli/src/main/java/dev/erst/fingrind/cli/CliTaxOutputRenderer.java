package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;

/** Renders tax-context success payloads for text and CSV output modes. */
final class CliTaxOutputRenderer {
  private CliTaxOutputRenderer() {}

  static String renderTaxRegistrationMutationText(
      String outcome, DeclaredTaxRegistration registration) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationMutationText(
        outcome, registration);
  }

  static String renderTaxRegistrationListText(TaxRegistrationPage page, boolean withContext) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationListText(page, withContext);
  }

  static String renderTaxRegistrationListCsv(TaxRegistrationPage page) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationListCsv(page);
  }
}
