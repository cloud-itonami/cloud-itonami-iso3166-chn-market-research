# cloud-itonami-iso3166-chn-market-research

Open ISO 3166 Blueprint for **CHN**: People's Republic of China — market-research
vertical — **`:implemented`**.

Independent **foreign-related survey (涉外调查) permit and project-approval
compliance** service for a market-research operator running surveys in China
that are commissioned, funded or co-conducted by a foreign party.

A thematic (`market-research`) sibling of
[`cloud-itonami-iso3166-chn`](https://github.com/cloud-itonami/cloud-itonami-iso3166-chn)
(public-procurement market entry) and of
[`cloud-itonami-iso3166-chn-advertising`](https://github.com/cloud-itonami/cloud-itonami-iso3166-chn-advertising),
and the China-jurisdiction counterpart of the jurisdiction-agnostic
[`cloud-itonami-isic-7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320)
market-research actor.

## Implementation (R0)

| Piece | Location |
|---|---|
| Actor | `src/foreignsurvey/*` |
| Governor | `:survey-integrity-governor` |
| Flagship HARD | `foreign-survey-permit-missing` · `social-survey-project-unapproved` |
| Tests | `kbb -M:dev:test` — 51 tests, 217 assertions |
| Demo | `kbb -M:dev:run` |
| Lint | `kbb -M:lint` |

## The distinction this repo exists to keep straight

China treats a foreign-related **market** survey and a foreign-related
**social** survey differently:

| | 涉外调查许可证 (permit) | 项目审批 (per-project approval) |
|---|---|---|
| 涉外市场调查 | **required** (第十条) | not required |
| 涉外社会调查 | **required** (第十条) | **required** (第八条・第九条) |

Conflating them fails in both directions: hold market surveys on a missing
approval and you block legitimate research; wave social surveys through on a
market survey's lighter obligations and you field unapproved research. So
`:per-project-approval-kinds` is a **closed set**, not a boolean — and an
*unrecognized* survey kind is held (`survey-kind-unrecognized`) rather than
read as the more permissive one.

## What the governor actually checks

Every one of these is a **HARD** hold: a human approver cannot override it.

| Rule | Grounded in |
|---|---|
| `no-spec-basis` | the jurisdiction is not in `foreignsurvey.facts/catalog` at all |
| `evidence-incomplete` | the jurisdiction's own evidence checklist is unsatisfied |
| `prohibited-content` | 涉外调查管理办法第七条 — closed seven-item set; held at **filing** as well as fieldwork |
| `survey-kind-unrecognized` | 第三条 — 市场调查 / 社会调查 are the only defined kinds |
| **`foreign-survey-permit-missing`** | 第十条 — no 涉外调查许可证 on file |
| **`foreign-survey-permit-elapsed`** | permit expired before the field start date |
| **`foreign-survey-permit-validity-unknown`** | dates unrecorded/unorderable — *un-checkable is not "still valid"* |
| **`social-survey-project-unapproved`** | 第八条・第九条 — social survey with no 项目审批 number |
| `sensitive-pi-without-separate-consent` | PIPL — 敏感个人信息 requires separate consent |
| `cross-border-transfer-without-lawful-basis` | PIPL 第三十八条 — closed four-item set of bases |
| `planned-sample-exceeds-approved` | fielding beyond the approved scope |
| `approved-sample-uncheckable` | scope unrecorded — again, *un-checkable is not within scope* |
| `already-filed` / `already-fielded` | double-actuation guards |

## Actuation

`:project/file` (filing a real approval application with a statistical
authority) and `:survey/field` (going to field with real respondents) are the
two real-world acts this actor performs. **Neither auto-commits at any
phase**, including phase 3. Two independent layers enforce this:

- `foreignsurvey.governor` treats both as `high-stakes` → always escalate; and
- `foreignsurvey.phase` excludes them from every phase's `:auto` set —
  asserted at **load time**, so a future one-word diff that adds
  `:survey/field` to phase 3 throws instead of silently weakening the
  invariant.

This actor never mints a 涉外调查许可证 or a project-approval number. Only the
国家统计局 or a provincial statistical bureau issues one.

## What this is NOT

- **Not the 国家统计局** and not a provincial statistical bureau. Commercial
  compliance tooling only — it never claims to be an official channel and
  never issues a permit or an approval.
- **Not legal advice.** Characterization and filing beyond checklist/draft
  assistance routes to Chinese-licensed counsel or a registered agent.
- Not a survey-fielding platform. It builds the compliance **record** an
  operator keeps; questionnaire authoring and panel management are out of
  scope.

## Sources

Every citation in `foreignsurvey.facts` was fetched and read on
`:retrieved-at` (2026-07-27):

- 涉外调查管理办法（国家统计局令第7号，2004-10-13 公布・施行）— <https://www.stats.gov.cn/zs/flfg/tjlydnfghflfg/202501/t20250117_1958351.html>
- 中华人民共和国个人信息保护法（2021-11-01 施行）— <https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm>

An item not in `foreignsurvey.facts/catalog` has no spec-basis — never
fabricate one.

## License

AGPL-3.0-or-later.
