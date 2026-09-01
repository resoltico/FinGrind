#!/usr/bin/env bash
# Guard the dedicated mutation-check entrypoint and its build/CI ownership boundaries.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly mutation_check="${repo_root}/check_mutation.sh"
readonly mutation_plugin="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindMutationConventionsPlugin.kt"
readonly mutation_scope_task="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/VerifyMutationScopeTask.kt"
readonly mutation_workflow="${repo_root}/.github/workflows/mutation.yml"
readonly ci_workflow="${repo_root}/.github/workflows/ci.yml"
readonly mutation_scope_catalog="${repo_root}/gradle/mutation-scope.properties"
readonly executor_build="${repo_root}/executor/build.gradle.kts"

assert_exact_mutation_scope() {
    local build_file="$1"
    local scope_property="$2"
    shift 2

    local actual_scope
    actual_scope="$(
        awk -F= -v scope_property="${scope_property}" '
            $1 ~ "^" scope_property "\\.[0-9]+$" {
                ordinal = $1
                sub("^" scope_property "\\.", "", ordinal)
                print ordinal "\t" $2
            }
        ' "${build_file}" | sort -n -k1,1 | cut -f2-
    )"

    local expected_scope
    expected_scope="$(printf '%s\n' "$@")"
    [[ "${actual_scope}" == "${expected_scope}" ]] ||
        die "$(basename "${build_file}") ${scope_property} no longer matches the reviewed mutation scope"
}

[[ -x "${mutation_check}" ]] || die "check_mutation.sh must be executable"
bash -n "${mutation_check}"
"${mutation_check}" --help | grep -Fq 'mutationCheck' ||
    die "mutation help no longer names the fixed Gradle task"
grep -Fq 'mutationCheck' "${mutation_plugin}" ||
    die "mutation convention no longer owns the aggregate task"
grep -Fq 'PitestPluginExtension' "${mutation_plugin}" ||
    die "mutation convention no longer owns PIT configuration"
grep -Fq 'mutators.set(setOf("DEFAULTS", "EXPERIMENTAL_SWITCH"))' "${mutation_plugin}" ||
    die "mutation convention no longer pins the reviewed production mutator set"
grep -Fq 'mutationThreshold.set(100)' "${mutation_plugin}" ||
    die "mutation convention no longer requires a perfect mutation score"
grep -Fq 'coverageThreshold.set(95)' "${mutation_plugin}" ||
    die "mutation convention no longer enforces the measured scoped coverage floor"
grep -Fq 'testStrengthThreshold.set(100)' "${mutation_plugin}" ||
    die "mutation convention no longer requires perfect test strength"
grep -Fq 'maxSurviving.set(0)' "${mutation_plugin}" ||
    die "mutation convention no longer rejects every surviving mutant"
grep -Fq 'cleanPitestReport' "${mutation_plugin}" ||
    die "mutation convention no longer cleans stale reports"
grep -Fq 'outputs.cacheIf { false }' "${mutation_plugin}" ||
    die "mutation convention no longer forces PIT to regenerate evidence"
grep -Fq 'verifyMutationEvidence' "${mutation_plugin}" ||
    die "mutation convention no longer verifies completed PIT evidence"
grep -Fq 'verifyMutationScope' "${mutation_plugin}" ||
    die "mutation convention no longer verifies deterministic-rule scope admission"
grep -Fq 'excludedProductionClasses' "${mutation_scope_task}" ||
    die "mutation scope verification no longer requires reviewed exclusions"
grep -Fq 'PIT scope admission is missing' "${mutation_scope_task}" ||
    die "mutation scope verification no longer rejects unclassified deterministic rules"
grep -Fq 'expectedMutationCounts.putAll(scope.expectedMutationCounts)' "${mutation_plugin}" ||
    die "mutation convention no longer enforces the reviewed per-class mutation inventory"
grep -Fq 'core.expectedMutation.227=dev.erst.fingrind.core.ExactDecimalTextSupport|55' "${mutation_scope_catalog}" ||
    die "core mutation inventory no longer protects the exact-decimal rule"
grep -Fq 'executor.expectedMutation.120=dev.erst.fingrind.executor.bookkeeping.InventoryCostingStateSupport|25' "${mutation_scope_catalog}" ||
    die "executor mutation inventory no longer protects the inventory-state rule"
grep -Fq 'executor.expectedMutation.150=dev.erst.fingrind.executor.TaxPostingResolution|60' "${mutation_scope_catalog}" ||
    die "executor mutation inventory no longer protects tax resolution"
grep -Fq 'executor.expectedMutation.160=dev.erst.fingrind.executor.bookkeeping.BookkeepingTaxSemanticsViolations|7' "${mutation_scope_catalog}" ||
    die "executor mutation inventory no longer protects tax-semantics translation"
assert_exact_mutation_scope "${mutation_scope_catalog}" core.targetClass \
    'dev.erst.fingrind.core.AccountCodePolicy' \
    'dev.erst.fingrind.core.AccountStructureDoctrine' \
    'dev.erst.fingrind.core.AccountTaxonomyDoctrine' \
    'dev.erst.fingrind.core.BalanceMath' \
    'dev.erst.fingrind.core.BookDoctrine' \
    'dev.erst.fingrind.core.CurrencyBalance' \
    'dev.erst.fingrind.core.EffectiveDateRange*' \
    'dev.erst.fingrind.core.EffectiveDateHorizonPolicy*' \
    'dev.erst.fingrind.core.FiscalYearStart' \
    'dev.erst.fingrind.core.InventoryCostingDoctrine' \
    'dev.erst.fingrind.core.JournalEntry*' \
    'dev.erst.fingrind.core.Money*' \
    'dev.erst.fingrind.core.SignedMoney' \
    'dev.erst.fingrind.core.ExactDecimalTextSupport' \
    'dev.erst.fingrind.core.PositiveMoney' \
    'dev.erst.fingrind.core.ProfitAndLossAccountDoctrine' \
    'dev.erst.fingrind.core.Quantity*' \
    'dev.erst.fingrind.core.ReportingPeriod' \
    'dev.erst.fingrind.core.WeightedAverageCostingMath*' \
    'dev.erst.fingrind.core.JournalClassifier*' \
    'dev.erst.fingrind.core.CanonicalDisplayText'
assert_exact_mutation_scope "${mutation_scope_catalog}" core.targetTest \
    'dev.erst.fingrind.core.AccountCodePolicyTest' \
    'dev.erst.fingrind.core.AccountStructureDoctrineTest' \
    'dev.erst.fingrind.core.AccountTaxonomyDoctrineTest' \
    'dev.erst.fingrind.core.BalanceMathTest' \
    'dev.erst.fingrind.core.BookDoctrineTest' \
    'dev.erst.fingrind.core.CurrencyBalanceTest' \
    'dev.erst.fingrind.core.EffectiveDateRangeTest' \
    'dev.erst.fingrind.core.EffectiveDateHorizonPolicyTest' \
    'dev.erst.fingrind.core.FiscalYearStartTest' \
    'dev.erst.fingrind.core.InventoryCostingDoctrineTest' \
    'dev.erst.fingrind.core.JournalEntryTest' \
    'dev.erst.fingrind.core.MoneyTest' \
    'dev.erst.fingrind.core.SignedMoneyTest' \
    'dev.erst.fingrind.core.PositiveMoneyTest' \
    'dev.erst.fingrind.core.ProfitAndLossAccountDoctrineTest' \
    'dev.erst.fingrind.core.QuantityTest' \
    'dev.erst.fingrind.core.ReportingPeriodTest' \
    'dev.erst.fingrind.core.WeightedAverageCostingMathTest' \
    'dev.erst.fingrind.core.JournalClassifierTotalityTest' \
    'dev.erst.fingrind.core.ExactDecimalTextSupportTest' \
    'dev.erst.fingrind.core.QuantityTextSupportTest' \
    'dev.erst.fingrind.core.CoreTextValueObjectsTest'
assert_exact_mutation_scope "${mutation_scope_catalog}" executor.targetClass \
    'dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolver' \
    'dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner' \
    'dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseValidator' \
    'dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy*' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.CashFlowPostingMovementClassifier*' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.CashFlowSectionAccumulator' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.InventoryValuationCalculator' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.PeriodComparativeRangeSupport' \
    'dev.erst.fingrind.executor.TaxValidationSupport' \
    'dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationSupport' \
    'dev.erst.fingrind.executor.bookkeeping.InventoryCostingStateSupport' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.FinancialPositionEquationSupport' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.ReportingBalanceSupport' \
    'dev.erst.fingrind.executor.TaxPostingResolution' \
    'dev.erst.fingrind.executor.bookkeeping.BookkeepingTaxSemanticsViolations' \
    'dev.erst.fingrind.executor.TaxReadService' \
    'dev.erst.fingrind.executor.bookkeeping.LedgerAggregateMoneyRangePolicy'
assert_exact_mutation_scope "${mutation_scope_catalog}" executor.targetTest \
    'dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolverTest' \
    'dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlannerTest' \
    'dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseValidatorTest' \
    'dev.erst.fingrind.executor.PostingAcceptancePolicyTest' \
    'dev.erst.fingrind.executor.PostingAcceptancePolicyInventoryAdmissionTest' \
    'dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicyInternalTest' \
    'dev.erst.fingrind.executor.PostingRouteReachabilityContractTest' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.CashFlowPostingMovementClassifierTest' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.CashFlowSectionAccumulatorTest' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.InventoryValuationCalculatorTest' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.PeriodComparativeRangeSupportTest' \
    'dev.erst.fingrind.executor.TaxValidationSupportTest' \
    'dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationSupportTest' \
    'dev.erst.fingrind.executor.bookkeeping.InventoryCostingStateSupportTest' \
    'dev.erst.fingrind.executor.BookReadServiceStatementQueryTest' \
    'dev.erst.fingrind.executor.bookkeeping.reporting.ReportingBalanceSupportTest' \
    'dev.erst.fingrind.executor.TaxPostingResolutionTest' \
    'dev.erst.fingrind.executor.PostEntryResolutionSupportTest' \
    'dev.erst.fingrind.executor.bookkeeping.BookkeepingTaxSemanticsViolationsTest' \
    'dev.erst.fingrind.executor.TaxReadServiceTest' \
    'dev.erst.fingrind.executor.LedgerAggregateMoneyRangePolicyTest'
grep -Fq 'mustRunAfter(":core:pitest")' "${executor_build}" ||
    die "executor PIT no longer follows core PIT"
grep -Fq -- '--no-parallel' "${mutation_check}" ||
    die "mutation wrapper no longer serializes module PIT tasks"
grep -Fq 'repo-verification-lock-support.sh' "${mutation_check}" ||
    die "mutation wrapper no longer shares the repository verification lock"
if "${mutation_check}" clean >/dev/null 2>&1; then
    die "mutation wrapper accepted an additional positional Gradle task"
fi
if "${mutation_check}" --dry-run >/dev/null 2>&1; then
    die "mutation wrapper accepted a dry-run that can skip all mutation evidence"
fi
if "${mutation_check}" -Ppitest.skip=true >/dev/null 2>&1; then
    die "mutation wrapper accepted an arbitrary project-property override"
fi
if "${mutation_check}" --project-dir=/tmp >/dev/null 2>&1; then
    die "mutation wrapper accepted a project-location override"
fi
if "${mutation_check}" -Dfingrind.gradle.project-build-root=/tmp >/dev/null 2>&1; then
    die "mutation wrapper accepted a build-layout override"
fi

# shellcheck source=/dev/null
source "${repo_root}/scripts/ci-release-surface-workflow-assertions-support.sh"
assert_exact_zulu_toolchain_contract 'mutation surveillance workflow' "${mutation_workflow}" 1 printf
assert_exact_zulu_toolchain_contract 'release-blocking CI workflow' "${ci_workflow}" 3 echo
grep -Fq 'schedule:' "${mutation_workflow}" ||
    die "mutation surveillance workflow no longer runs on schedule"
grep -Fq 'workflow_dispatch:' "${mutation_workflow}" ||
    die "mutation surveillance workflow no longer supports manual runs"
mutation_job_preamble="$(
    awk '
        $0 == "  mutation:" { active = 1 }
        active && $0 == "    steps:" { exit }
        active { print }
    ' "${mutation_workflow}"
)"
if grep -Fq 'runner.temp' <<< "${mutation_job_preamble}"; then
    die "mutation workflow evaluates the runner context at job scope, where GitHub Actions does not provide it"
fi
mutation_run_step="$(
    awk '
        $0 == "      - name: Run release-critical mutation scopes" { active = 1 }
        active {
            if ($0 ~ /^      - name: / && $0 != "      - name: Run release-critical mutation scopes") {
                exit
            }
            print
        }
    ' "${mutation_workflow}"
)"
grep -Fq 'FINGRIND_GRADLE_PROJECT_BUILD_ROOT: ${{ runner.temp }}/fingrind-mutation-build' <<< "${mutation_run_step}" ||
    die "mutation workflow no longer supplies one predictable external report root to the mutation step"
grep -Fq 'if: always()' "${mutation_workflow}" ||
    die "mutation reports are no longer retained on failure"
grep -Fq 'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a' "${mutation_workflow}" ||
    die "mutation report upload action is not pinned"
grep -Fq 'fingrind-mutation-build/core/reports/pitest' "${mutation_workflow}" ||
    die "core mutation report is not retained"
grep -Fq 'fingrind-mutation-build/executor/reports/pitest' "${mutation_workflow}" ||
    die "executor mutation report is not retained"
grep -Fq 'retention-days: 30' "${mutation_workflow}" ||
    die "mutation report retention drifted"
grep -Fq 'if-no-files-found: error' "${mutation_workflow}" ||
    die "mutation surveillance reports no longer fail closed when evidence is missing"

ci_mutation_job="$(workflow_job_block_from "${ci_workflow}" 'mutation')"
ci_gate_job="$(workflow_job_block_from "${ci_workflow}" 'gate')"
[[ -n "${ci_mutation_job}" ]] || die "CI no longer defines release-critical mutation execution"
grep -Fq 'name: Critical accounting mutation scopes' <<< "${ci_mutation_job}" ||
    die "CI mutation job no longer has its release-contract display name"
grep -Fq 'timeout-minutes: 45' <<< "${ci_mutation_job}" ||
    die "CI mutation job no longer has its bounded production budget"
grep -Fq 'run: ./check_mutation.sh' <<< "${ci_mutation_job}" ||
    die "CI mutation job no longer runs the fixed mutation wrapper"
grep -Fq 'cache-read-only: ${{ github.event_name == '\''pull_request'\'' }}' <<< "${ci_mutation_job}" ||
    die "CI mutation pull requests can poison the Gradle cache"
grep -Fq 'if-no-files-found: error' <<< "${ci_mutation_job}" ||
    die "CI mutation reports no longer fail closed when evidence is missing"
grep -Eq '^[[:space:]]*-[[:space:]]+mutation$' <<< "${ci_gate_job}" ||
    die "aggregate Gate no longer requires the mutation job"

printf 'mutation check contract regression: success\n'
