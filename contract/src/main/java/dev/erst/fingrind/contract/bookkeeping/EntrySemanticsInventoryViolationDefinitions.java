package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Inventory and inventory-adjacent entry-semantics violation definitions. */
final class EntrySemanticsInventoryViolationDefinitions {
  private EntrySemanticsInventoryViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "inventory-quantity-incompatible-with-unit-of-measure",
            "inventory-quantity",
            "One inventory quantity field is incompatible with the selected inventory account's declared unitOfMeasure and exact quantityScale.",
            "Use quantity text admitted by the selected inventory account's declared unitOfMeasure scale, or declare an inventory account whose unitOfMeasure matches the intended quantity precision."),
        definition(
            "inventory-acquisition-cost-not-exact",
            "inventory-acquisition",
            "One inventory acquisition cannot compose an exact carrying-cost amount at the currency minor-unit boundary from the supplied quantity and unitCost.",
            "Adjust quantity or unitCost so quantity multiplied by unitCost resolves to one exact functional-currency minor-unit amount."),
        definition(
            "inventory-acquisition-breaches-minor-unit-floor",
            "inventory-acquisition",
            "One inventory acquisition would leave a positive carrying-cost pool below the minimum minor-unit floor required to preserve zero-to-zero disposal truth.",
            "Increase the carrying cost for the selected quantity, or use a coarser inventory unitOfMeasure scale so the resulting positive pool remains above the minor-unit floor."),
        definition(
            "inventory-acquisition-foreign-exchange-functional-amount-mismatch",
            "foreign-exchange",
            "One inventory acquisition foreignExchange.functionalAmount contradicts the exact functional-currency pre-tax acquisition cost resolved from quantity and unitCost.",
            "Use a foreignExchange.functionalAmount equal to the exact pre-tax acquisition cost resolved from the supplied quantity and unitCost."),
        definition(
            "evidence-class-conflict",
            "evidence-class",
            "The retained evidence class contradicts the event class resolved from the supplied journal.",
            "Use evidence whose source-document types match the resolved event class."),
        definition(
            "raw-journal-shadows-typed-event",
            "raw-journal-admission",
            "The supplied raw journal resolves to one published typed business event and therefore must not be admitted through the raw direct-journal path.",
            "Submit the matching typed business-event command instead of the raw direct-journal path."),
        definition(
            "raw-journal-bundles-operational-events",
            "raw-journal-admission",
            "The supplied raw journal bundles multiple operational business events into one posting.",
            "Split the request into the separate typed business events named by the violation message."),
        definition(
            "raw-journal-requires-cash-line",
            "raw-journal-admission",
            "The supplied raw journal is an adjustment on a cash-basis book, but no journal line resolves to a declared cash account.",
            "Add at least one declared cash account line, or use an accrual-basis book for this adjustment."),
        definition(
            "raw-journal-touches-inventory",
            "raw-journal-admission",
            "The supplied raw journal contains an inventory-account line even though raw journals do not own exact inventory quantity truth.",
            "Remove the inventory line, or restate the request as one supported quantity-aware inventory command."),
        definition(
            "opening-window-account-not-permitted",
            "opening-window",
            "The supplied opening-position request references an account that is not permitted during the adoption opening window.",
            "Use only opening-window-permitted balance-sheet and equity accounts in openingBalances[].accountCode."),
        definition(
            "opening-inventory-requires-quantity",
            "inventory-opening",
            "One inventory opening balance omits the exact quantity required to establish its carrying-cost pool.",
            "Supply openingBalances[].quantity for every inventory account, using quantity text admitted by that account's unitOfMeasure."),
        definition(
            "opening-quantity-requires-inventory",
            "inventory-opening",
            "One non-inventory opening balance carries quantity even though exact quantity belongs only to inventory accounts.",
            "Remove openingBalances[].quantity from non-inventory accounts, or use an inventory account when the opening balance represents stock on hand."),
        definition(
            "inventory-capitalization-requires-quantity-on-hand",
            "inventory-capitalization",
            "A cost-only inventory capitalization requires existing inventory quantity so the carrying-cost pool remains exact and non-zero together with quantity.",
            "Record or correct the inventory quantity first, then capitalize the directly attributable carrying cost."),
        definition(
            "inventory-opening-carrying-cost-invalid",
            "inventory-opening",
            "One inventory opening quantity and carrying cost cannot establish a valid exact inventory pool.",
            "Use a positive carrying cost that is sufficient for the supplied exact quantity and the inventory account's quantityScale."),
        definition(
            "inventory-opening-must-be-first-movement",
            "inventory-opening",
            "An inventory opening balance is valid only as the first durable movement for that inventory account.",
            "Use an opening position before any inventory movement, or correct the established inventory history through a typed compensating event."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
