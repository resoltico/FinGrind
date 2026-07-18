---
afad: "5.0.1"
version: "0.61.0"
domain: PRIMARY_SOURCES
updated: "2026-07-17"
route:
  keywords: [fingrind, primary sources, legislation, Latvia, tax, payroll, VAT, foreign exchange, ECB, source verification]
  questions: ["where do FinGrind jurisdiction examples get their legal inputs", "which official sources govern Latvian payroll", "how should I verify a tax example", "where should I obtain foreign-exchange quote evidence"]
---

# Primary Sources And Statutory Inputs

FinGrind's bookkeeping kernel is jurisdiction-neutral unless a published bounded context says otherwise. An example using `jurisdiction: "LV"`, a tax label such as VAT, or an EUR amount does not itself establish a statutory rate, filing obligation, registration threshold, employment classification, or legal-compliance outcome.

Before using a jurisdiction-specific example or payroll command in production, verify the applicable law and current administrative guidance for the entity, period, worker or transaction classification, and filing status. FinGrind preserves accounting facts; it does not replace legal, tax, employment, or payroll advice.

## Source Policy

- Link directly to the legislation database or responsible authority, not to a blog, search result, or secondary summary.
- For Latvian legislation, use the current Latvian-language consolidated text on Likumi.lv as the controlling legal text. An English translation is a reading aid only and may lag amendments; it must not be treated as the current legal text.
- Record a source's jurisdiction, scope, and retrieval date whenever executable parameters derive from it.
- Treat authority guidance as operational interpretation and legislation as the controlling source where they differ.
- Reject a request when its statutory treatment is outside the bounded context rather than substituting a plausible generic rate or classification.
- Recheck time-sensitive inputs before every payroll period and before any tax filing. Published values can change during a software release cycle.

## Accounting Standards References

FinGrind's fixed-asset, financing, and realized-foreign-exchange contexts are narrow bookkeeping
boundaries, not a declaration of compliance with IFRS Accounting Standards or any local reporting
regime. Use the governing accounting framework, jurisdictional law, and entity accounting policy
to determine recognition, measurement, presentation, disclosure, and rate-selection requirements.

- [IFRS Foundation: IAS 16 Property, Plant and Equipment](https://www.ifrs.org/issued-standards/list-of-standards/ias-16-property-plant-and-equipment/) is the primary standards reference for the broader fixed-assets domain, including recognition, carrying amount, and depreciation concepts.
- [IFRS Foundation: IFRS 9 Financial Instruments](https://www.ifrs.org/issued-standards/list-of-standards/ifrs-9-financial-instruments/) is the primary standards reference for the broader financing and financial-instruments domain.
- [IFRS Foundation: IAS 21 The Effects of Changes in Foreign Exchange Rates](https://www.ifrs.org/issued-standards/list-of-standards/ias-21-the-effects-of-changes-in-foreign-exchange-rates/) is the primary standards reference for the broader foreign-exchange domain.

The published FinGrind boundaries and exclusions for these contexts are [Fixed Assets](./ADR_FIXED_ASSETS.md), [Financing](./ADR_FINANCING.md), and [Realized Foreign Exchange](./ADR_REALIZED_FOREIGN_EXCHANGE.md). The official references establish broader standards context; they do not expand a FinGrind command's accepted facts or make a command compliant by itself.

## Latvia

### VAT examples

The tax-setup and VAT-labelled examples in this repository are structural examples only. They show how to declare accounts and a tax registration; they do not set a Latvian VAT rate or prove that a registration, deduction, invoice, place-of-supply, or filing treatment is correct.

- [Likumi.lv: Pievienotās vērtības nodokļa likums](https://likumi.lv/ta/id/253451-pievienotas-vertibas-nodokla-likums) is the controlling current Latvian legislative text.
- [State Revenue Service: Value Added Tax](https://www.vid.gov.lv/en/value-added-tax) is the operational authority source for current registration, return, and administration guidance.

### Monthly employment payroll

The Latvia monthly-payroll context is deliberately narrower than Latvian payroll law. Its exact supported assumptions, effective period, calculation, exclusions, and publication boundary are defined in [ADR_LATVIAN_PAYROLL.md](./ADR_LATVIAN_PAYROLL.md) and [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md).

- [Likumi.lv: Darba likums](https://likumi.lv/ta/id/26019-darba-likums) is the controlling current Latvian legislative text for employment-law pay and payslip duties.
- [Likumi.lv: Par valsts sociālo apdrošināšanu](https://likumi.lv/ta/id/45466) is the controlling current Latvian legislative text for the social-insurance framework.
- [Likumi.lv: Par iedzīvotāju ienākuma nodokli](https://likumi.lv/ta/id/56880-par-iedzivotaju-ienakuma-nodokli) is the controlling current Latvian legislative text for personal-income-tax treatment.
- [Likumi.lv: Cabinet Regulation No. 827](https://likumi.lv/ta/id/217642-noteikumi-par-valsts-socialas-apdrosinasanas-obligato-iemaksu-veiceju-registraciju-un-zinojumiem-par-valsts-socialas-apdrosinas) is the controlling current Latvian text for employee registration and social-insurance/personal-income-tax reporting.
- [State Revenue Service: Mandatory State Social Insurance Contributions](https://www.vid.gov.lv/en/mandatory-state-social-insurance-contributions) publishes current operational contribution rates, reporting, and payment guidance.
- [State Revenue Service: Personal Income Tax](https://www.vid.gov.lv/en/personal-income-tax) publishes current personal-income-tax administration and allowance guidance.
- [State Revenue Service: Personal Income Tax Rates](https://www.vid.gov.lv/en/personal-income-tax-rates) publishes current rate and threshold guidance.
- [State Revenue Service: 2026 non-taxable minimum](https://www.vid.gov.lv/lv/neapliekamais-minimums) states the current monthly amount and illustrates the payroll-tax-book and dependant inputs that determine payroll withholding treatment.
- [State Revenue Service: 2026 mandatory social-insurance contributions](https://www.vid.gov.lv/lv/biezak-uzdotie-jautajumi-katalogs/valsts-socialas-apdrosinasanas-obligatas-iemaksas-vsaoi-darba-nemeju-un-darba-devēju-socialo-iemaksu-veiksanas-kartiba) is the authority's current Latvian-language operational guidance for employer and employee social-contribution treatment.

The context does not generate an EDS return, employee-registration notice, or statutory payslip. Operators remain responsible for those official submissions and for confirming that the supported worker assumptions apply.

## Foreign-Exchange Quote Evidence

FinGrind accepts a caller-retained `foreignExchange.quotedRate` as accounting evidence. It does not fetch a rate, assert that a rate is legally required, or decide which rate source, rate date, tax treatment, or accounting policy applies to a transaction. Retain the selected source, quoted date, transaction and functional currency amounts, and the reason the rate is appropriate for the entity's policy and transaction.

- [European Central Bank: Euro foreign exchange reference rates](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html) is an official primary data source for euro reference rates. The ECB publishes the rates for information purposes and explicitly discourages transaction use, so a reference rate is not automatically an appropriate transaction-rate source.
- Where a jurisdiction, contract, tax rule, payment provider, or accounting policy requires a different rate, use that controlling primary source and preserve it in the request's `quoteSource`; do not relabel an arbitrary market quote as an ECB rate.

The foreign-exchange request contract remains jurisdiction-neutral. Consult the governing legislation and authority guidance for the relevant entity and tax treatment before posting; the ECB data page is not a tax or financial-reporting rule.
