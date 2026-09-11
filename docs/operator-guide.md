# Operator guide

## Run it

```bash
kbb -M:dev:test    # 51 tests, 217 assertions
kbb -M:dev:run     # the demo: a clean social survey, a market survey that
                       # correctly needs NO project approval, and every HARD hold
kbb -M:lint        # clj-kondo, errors fail CI
```

Inside the monorepo the `:local/root` deps resolve as-is. A standalone fork
should replace them with git coordinates (see `deps.edn`).

## The operation lifecycle

```
:survey/intake   -> normalize the project record        (auto at phase 3)
:regime/assess   -> which obligations attach            (always human)
:project/file    -> prepare the 项目审批 application     (always human)
:survey/field    -> go to field with real respondents   (always human)
```

`:project/file` is only meaningful for surveys that actually need a
per-project approval — a foreign-related **market** survey goes straight from
assessment to fieldwork on its permit alone. The governor does not require a
filing that the regulation does not require.

## Classifying a survey

This is the decision the whole repo turns on. Record `:survey-kind`
deliberately:

- `:market` — 商品・商业服务に関する情报の収集 (第三条)
- `:social` — 问卷・访谈・观察 by which information outside the market-survey
  scope is collected (第三条)

**If a project genuinely fits neither, leave the kind as-is and let the
governor hold it** (`survey-kind-unrecognized`). Do not "round down" to
`:market` to get past the gate — that is the exact failure the check exists
to catch, and a test asserts it.

Set `:foreign-related?` from 第二条's limbs: commissioned or funded by a
foreign organization/individual, conducted jointly with one, conducted by a
foreign organization's China branch, or with results provided abroad. A
purely domestic survey is not failed by any foreign-related rule.

## Recording a survey

Fields the governor reads directly from the store (it never takes the
advisor's word for any of them):

| Field | Meaning |
|---|---|
| `:foreign-related?` | gates every 涉外调查 check |
| `:survey-kind` | must be recognized; decides whether approval is required |
| `:permit-number` | the 涉外调查许可证. Blank/whitespace = not on file |
| `:permit-valid-until` | ISO-8601 `YYYY-MM-DD`. Anything else → `…-validity-unknown` |
| `:field-start-date` | ISO-8601 `YYYY-MM-DD` |
| `:project-approval-number` | required for `:social` only |
| `:content-flags` | matched against the closed 第七条 set |
| `:collects-sensitive-personal-info?` / `:separate-consent-obtained?` | PIPL |
| `:cross-border-transfer?` / `:cross-border-basis` | PIPL 第三十八条 — one of `:security-assessment` `:certification` `:standard-contract` `:other-statutory` |
| `:planned-sample-size` / `:approved-sample-size` | both required where an approval exists |

**Leave a field nil rather than filling it with a placeholder.** A nil
approved sample produces `approved-sample-uncheckable` (a hold you can see
and fix); a placeholder `0` produces a silent pass. The `DatomicStore` round
trip deliberately preserves nil for exactly this reason, and tests assert it
for both the sample size and the permit number.

## Planning around 第二十三条

Project approval is decided within **20 working days** of acceptance,
extendable by 10. That is a scheduling fact, not a compliance check — the
actor does not model it as a deadline, but any fieldwork date you record
should sit beyond it for a survey still awaiting approval.

## Adding a jurisdiction

Add one map to `foreignsurvey.facts/catalog`:

1. Fetch the jurisdiction's **official** survey/statistics regulation and
   its personal-data law. Read them.
2. Record `:legal-basis`, `:provenance` (the URL you actually fetched) and
   `:retrieved-at`.
3. Set `:recognized-survey-kinds` and `:per-project-approval-kinds` from
   what that jurisdiction's own regulation defines — not from China's.
4. Set `:prohibited-content` as a flag→text map, quoting the prohibition.
5. Set `:cross-border-bases` from that jurisdiction's own transfer regime.

`coverage` reports missing jurisdictions honestly; an uncovered jurisdiction
reaching the field gate is held on `no-spec-basis`, which is correct.

## Rollout phases

Start at phase 1 and move up as the operator's confidence grows. Phase 3
auto-commits only `:survey/intake`. `:project/file` and `:survey/field` are
excluded from every phase's `:auto` set, and `foreignsurvey.phase` throws at
load time if that is ever edited away.
