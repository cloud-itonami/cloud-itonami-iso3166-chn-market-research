# ADR-0001: Architecture — Survey-Integrity Compliance Governor for the 涉外调查 regime

**Status**: accepted
**Date**: 2026-07-27
**Supersedes**: nothing. Mirrors the superproject ADR
`2607277000-cloud-itonami-chn-marketing-vertical` (com-junkawasaki/root).

## Context

`cloud-itonami-isic-7320` models market research generically: survey
intake, professional-standards evidence, unrepresentative-sample
screening, findings publication. None of that touches the structural fact
that dominates research in China for any foreign-connected client:
涉外调查管理办法 (国家统计局令第7号) makes the *conducting organization's*
涉外调查许可证 a precondition of conducting a foreign-related survey at all
(第十条), and puts foreign-related **social** surveys — but not market
surveys — behind an additional per-project approval (第八条/第九条).

The failure mode is symmetric and both directions are expensive:

- treat every foreign-related survey as needing project approval, and you
  block legitimate market research on a requirement that does not apply;
- treat an unclassified survey as a market survey, and you field
  unapproved social research.

## Decision

Fork the `marketentry` actor shape from `cloud-itonami-iso3166-chn` into a
`foreignsurvey` actor in a new thematic sibling repo, with the
market/social split expressed as **data with a closed domain** rather than
as a boolean or a default.

- `:per-project-approval-kinds` is a set (`#{:social}` for CHN), not
  `:needs-approval? true/false`. A jurisdiction that puts a third kind
  behind approval adds it to the set; nothing else changes.
- `:recognized-survey-kinds` is a separate closed set, and
  `survey-kind-unrecognized` is its own HARD violation. **An unknown kind
  is never resolved to the more permissive known one.** A `facts_test`
  asserts every approval-requiring kind is also a recognized kind.
- `foreignsurvey.facts/prohibited-content` is a map from flag to the text
  that bans it, so a hold can say *which* 第七条 limb was hit rather than
  just "prohibited".

Structure otherwise mirrors every sibling actor (facts / advisor /
governor / phase / registry / store / operation), so each seam is a swap
rather than a rewrite.

## Consequences

- Two flagship HARD checks are new to this fleet:
  `foreign-survey-permit-missing` (a *permit held by the operator*, not a
  property of the work product) and `social-survey-project-unapproved` (a
  conditional approval whose applicability is itself derived from a closed
  set).
- **The permit is checked at both actuation ops, the project approval only
  at fieldwork.** 第十条 conditions *conducting* a foreign-related survey
  on holding the permit, so preparing an application without one is
  already outside the regime; the project approval, by contrast, is the
  thing being applied for, so requiring it at filing time would be
  circular.
- Likewise prohibited content is checked at **both** ops: a questionnaire
  carrying 第七条 content must not be submitted for approval either.
- **Un-checkable is a distinct outcome, not a pass**, for both the permit
  expiry and the approved sample scope. The sample-scope check is further
  scoped to surveys that actually have an approval — a market survey is
  not held for lacking an approved sample size it was never required to
  have.
- Dates are compared as ISO-8601 `YYYY-MM-DD` strings under lexicographic
  ordering — portable across JVM and JS with no date library and no
  timezone to get wrong.
- `content-flags` is persisted as an EDN blob rather than a
  cardinality-many attribute, so the recorded order survives the Datomic
  round trip; a store-contract test asserts it.
- 51 tests / 217 assertions green; `clojure -M:lint` clean.

## Alternatives considered

- **Add "CHN" to `cloud-itonami-isic-7320`'s catalog.** A catalog row can
  carry citations but not a conditional gate whose applicability depends
  on a per-entity classification. Still worth doing additively so the
  generic actor stops reporting China as uncovered — a follow-up on 7320,
  not a blocker here.
- **One combined `cloud-itonami-iso3166-chn-marketing` repo** with the
  advertising vertical. Rejected: different authority (国家统计局 vs SAMR),
  different statute, different governor keyword, and the fleet's
  convention is one actor per regulatory regime.
- **A boolean `:needs-project-approval?` on the jurisdiction.** Rejected:
  it cannot express "which kinds", which is precisely the distinction the
  regulation draws, and it invites a default for unknown kinds.
