# Business Model: China Foreign-Related Survey (涉外调查) Permit & Project-Approval Compliance Service

## Classification

- Repository: `cloud-itonami-iso3166-chn-market-research`
- ISO 3166: `CHN` (People's Republic of China)
- ISIC Rev.5: `7320` (Market research and public opinion polling) —
  China-jurisdiction member
- Activity: 涉外调查 permit and project-approval compliance for a research
  operator conducting surveys in China on behalf of, with, or funded by a
  foreign party
- Social impact: [:respondent-protection :research-integrity
  :cross-border-friction-reduction]

## Customer

- a foreign brand, agency, university or think tank commissioning research
  in China, who discovers only late that 涉外调查管理办法 makes their
  *supplier's* permit and (for social research) a per-project approval a
  precondition of fielding at all
- a Chinese research agency (调查机构) holding a 涉外调查许可证 that needs an
  auditable per-project record of which surveys were foreign-related, which
  needed approval, and what was actually approved
- a multinational's insights team whose questionnaires routinely mix market
  and social items, and who needs the boundary called before fieldwork
  rather than after
- a `cloud-itonami-isic-7320` operator that needs the China jurisdiction
  specifically, with the permit/approval bifurcation modelled rather than
  assumed away

## Offer

- **foreign-related triage**: is this project 涉外调查 at all (第二条 — the
  commissioning, funding, co-conduct and results-provided-abroad limbs), and
  if so which obligations attach
- **market/social classification**: 第三条's boundary between 市场调查 and
  社会调查, which decides whether a per-project approval is required at all —
  and an explicit hold when a project fits neither, rather than a silent
  default to the lighter regime
- **permit lifecycle tracking**: the 涉外调查许可证 on file, its validity
  window against the project's own field start date, and a hard block on
  fielding past expiry
- **项目审批 application package**: the submission set 第二十二条 lists
  (申请书・许可证写し・委托/合作合同・调查方案・问卷/访谈提纲・背景资料),
  with the 第二十三条 timeline (20 working days, extendable by 10) as the
  planning input it actually is
- **prohibited-content screening** against the closed seven-item 第七条 set
- **PIPL checklist**: separate consent for 敏感个人信息, and a recognized
  第三十八条 basis for any provision of respondent data outside China
- **approved-scope monitoring**: planned sample against the sample the
  approval actually covers
- **compliance-audit export package** — the append-only ledger of every
  decision, hold and approval

## Revenue

- per-project compliance-review fee (triage + classification + checklist)
- 项目审批 application preparation fee for social surveys
- recurring permit-expiry and regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- **no foreign-related survey may be proposed for fieldwork without an
  independently verified, unexpired 涉外调查许可证**, and no foreign-related
  *social* survey without a recorded project-approval number. The governor
  re-derives both from the store, never from the advisor's own claim, and
  both are HARD holds a human approver cannot override.
- **the market/social boundary is never guessed.** An unrecognized survey
  kind is held, not read as 市场调查 — the permissive reading of an unknown
  is exactly the failure this check exists to prevent.
- **un-checkable is never treated as compliant.** An unrecorded approved
  sample size, or a validity/field-start date pair that is not both
  ISO-8601, produces its own HARD hold (`approved-sample-uncheckable` /
  `foreign-survey-permit-validity-unknown`) rather than falling through as
  "within scope" / "still valid".
- **prohibited content is checked at filing, not only at fieldwork.** A
  questionnaire carrying 第七条 content must not even be submitted for
  approval.
- any actual fieldwork or approval filing requires Survey-Integrity
  Compliance Governor clearance and always escalates to human sign-off —
  `:survey/field` is never automated at any phase, and that exclusion is
  asserted at load time.
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — it must be corrected
  against a cited official source first.
- this service does **not** provide legal advice, and never acts as or for a
  statistical authority; it never mints a permit or an approval number.
- every requirement cites the official regulation and an official URL that
  was actually fetched on the recorded `:retrieved-at`, never invented.

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-chn`**: public-procurement market entry — CCGP,
  USCC, domestic-entity posture. A different regulatory domain entirely.
- **`cloud-itonami-iso3166-chn-advertising`**: the sibling 7310 vertical —
  广告审查 and internet-advertising conformance. Research happens *before*
  the campaign; that blueprint governs the campaign itself. An operator
  doing both forks both.
- **`cloud-itonami-isic-7320`**: the jurisdiction-agnostic market-research
  actor (survey intake, unrepresentative-sample screening, findings
  publication). It has no notion of a survey *permit* or a per-project
  approval; the two compose.
- **`com-etzhayyim-ooyake`**: read-only civic-wayfinding mirror of
  government structure, non-commercial, barred from acting as or for the
  government. This blueprint is commercial and never claims to be an
  official channel — and specifically never claims to be the 国家统计局.
- **`legal-entity.etzhayyim.com`**: read-only aggregated company-registry
  data, no execution. This blueprint executes (gated) filings.
