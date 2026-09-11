(ns foreignsurvey.governor
  "Survey-Integrity Compliance Governor -- the independent compliance
  layer that earns the ForeignSurvey-LLM the right to commit. The LLM
  has no notion of whether the conducting organization actually holds a
  涉外调查许可证, whether THIS project needed its own approval (social
  surveys do, market surveys do not), whether the questionnaire touches
  content 第七条 forbids outright, whether PIPL's separate-consent and
  cross-border conditions are met, or when a plan stops being a plan and
  becomes real fieldwork with real respondents -- so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:survey-integrity-governor`, the
  same family keyword `cloud-itonami-isic-7320` uses; this is that
  governor's China-jurisdiction implementation.

  This blueprint's own text (docs/business-model.md Trust Controls)
  names exactly the checks below.

  Nine checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT -- but see
  `foreignsurvey.phase`: for `:stake :actuation/file-project`/
  `:actuation/field-survey` NO phase ever allows auto-commit either.

    1. Spec-basis                  -- did the proposal cite an OFFICIAL
                                      source, or invent one?
    2. Evidence incomplete         -- is the jurisdiction actually
                                      assessed with a full checklist?
    3. Prohibited content          -- 第七条. Checked at BOTH actuation
                                      ops, not just fieldwork: a project
                                      whose questionnaire is unlawful
                                      must not even be filed for
                                      approval.
    4. Survey kind unrecognized    -- 第三条 defines 市场调查 and
                                      社会调查. An unrecognized kind is
                                      NOT silently read as the more
                                      permissive one.
    5. Permit missing / elapsed    -- FLAGSHIP. 第十条. For a
                                      foreign-related survey, the
                                      conducting organization must hold
                                      a 涉外调查许可证 that is recorded
                                      AND unexpired at the field start
                                      date.
    6. Project approval missing    -- FLAGSHIP. 第八条/第九条. ONLY for
                                      the kinds this jurisdiction puts
                                      behind per-project approval
                                      (:social in CHN) -- a foreign-
                                      related MARKET survey is NOT held
                                      on this, which is the whole point
                                      of keeping the two apart.
    7. PIPL conditions             -- separate consent for sensitive
                                      personal information; a recognized
                                      第三十八条 basis for any
                                      cross-border transfer.
    8. Sample beyond approval      -- the fleet's MAXIMUM-ceiling family
                                      check, plus its own explicit
                                      un-checkable hold.
    9. Double-actuation guards     -- off dedicated `:filed?`/`:fielded?`
                                      facts (never a `:status` value)."
  (:require [kotoba.lang.text :as str]
            [foreignsurvey.facts :as facts]
            [foreignsurvey.registry :as registry]
            [foreignsurvey.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Filing a real approval application with a statistical authority and
  going to field with real respondents are the two real-world actuation
  events this actor performs."
  #{:actuation/file-project :actuation/field-survey})

(def ^:private actuation-ops #{:project/file :survey/field})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  [{:keys [op]} proposal]
  (when (contains? #{:regime/assess :project/file :survey/field} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式 spec-basis の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  [{:keys [op subject]} st]
  (when (contains? actuation-ops op)
    (let [v (store/survey st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction v) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(涉外调查许可证/委托合同/调查方案・问卷/保密制度)が充足していない状態での提案"}]))))

(defn- prohibited-content-violations
  "第七条. Deliberately checked at BOTH actuation ops: a questionnaire
  carrying prohibited content must not even be filed for approval, let
  alone fielded."
  [{:keys [op subject]} st]
  (when (contains? actuation-ops op)
    (let [v (store/survey st subject)
          hits (facts/prohibited-flags (:jurisdiction v) (:content-flags v))]
      (when (seq hits)
        (let [texts (facts/prohibited-content (:jurisdiction v))]
          [{:rule :prohibited-content
            :flags hits
            :detail (str subject " の调查内容に涉外调查管理办法第七条の禁止事项が含まれる: "
                         (str/join "、" (map #(get texts %) hits)))}])))))

(defn- survey-kind-violations
  "第三条 defines exactly two kinds. An unrecognized kind must NOT be
  read as the more permissive one (:market, which needs no per-project
  approval) -- that would let an unclassified social survey into the
  field on a market survey's lighter obligations."
  [{:keys [op subject]} st]
  (when (contains? actuation-ops op)
    (let [v (store/survey st subject)]
      (when-not (facts/recognized-survey-kind? (:jurisdiction v) (:survey-kind v))
        [{:rule :survey-kind-unrecognized
          :detail (str subject " の调查种别(" (pr-str (:survey-kind v))
                       ")が当該法域の规定する种别(市场调查/社会调查)に該当しない"
                       " -- 不明な种别を市场调查として扱わない [第三条]")}]))))

(defn- permit-violations
  "FLAGSHIP (第十条). For a foreign-related survey the conducting
  organization must hold a 涉外调查许可证 that is recorded AND unexpired
  at the field start date. CONDITIONAL on the survey's own
  `:foreign-related?` ground truth -- a purely domestic survey is not
  failed by a foreign-related rule.

  Checked at BOTH actuation ops: 第十条 conditions the act of conducting
  a foreign-related survey on holding the permit, and preparing a
  project application without one is already outside the regime."
  [{:keys [op subject]} st]
  (when (contains? actuation-ops op)
    (let [v (store/survey st subject)]
      (when (true? (:foreign-related? v))
        (cond
          (not (registry/permit-on-file? v))
          [{:rule :foreign-survey-permit-missing
            :detail (str subject " は涉外调查だが涉外调查许可证が未記録"
                         " -- 実施提案は進められない [涉外调查管理办法第十条]")}]

          (not (registry/permit-elapsed-checkable? v))
          [{:rule :foreign-survey-permit-validity-unknown
            :detail (str subject " の许可证有効期(" (pr-str (:permit-valid-until v))
                         ")または実施開始日(" (pr-str (:field-start-date v))
                         ")が ISO-8601 で記録されておらず失効判定不能 -- 判定不能は有効ではない")}]

          (registry/permit-elapsed? v)
          [{:rule :foreign-survey-permit-elapsed
            :detail (str subject " の涉外调查许可证は " (:permit-valid-until v)
                         " に失効しており実施開始日 " (:field-start-date v) " には無効")}]

          :else nil)))))

(defn- project-approval-violations
  "FLAGSHIP (第八条/第九条). ONLY for the survey kinds this jurisdiction
  puts behind per-project approval. In China that is 社会调查 and NOT
  市场调查 -- a foreign-related market survey needs the permit but no
  project approval, and holding it on this check would block legitimate
  research."
  [{:keys [op subject]} st]
  (when (= op :survey/field)
    (let [v (store/survey st subject)]
      (when (and (true? (:foreign-related? v))
                 (facts/requires-per-project-approval? (:jurisdiction v) (:survey-kind v))
                 (not (registry/project-approval-on-file? v)))
        [{:rule :social-survey-project-unapproved
          :detail (str subject " は涉外社会调查だが项目审批番号が未記録"
                       " -- 実施提案は進められない [涉外调查管理办法第八条・第九条]")}]))))

(defn- pipl-violations
  "PIPL. Sensitive personal information requires the individual's
  SEPARATE consent; any cross-border provision requires one of the four
  bases 第三十八条 recognizes. Both CONDITIONAL on the survey's own
  ground truth."
  [{:keys [op subject]} st]
  (when (= op :survey/field)
    (let [v (store/survey st subject)]
      (seq
       (cond-> []
         (and (true? (:collects-sensitive-personal-info? v))
              (not (true? (:separate-consent-obtained? v))))
         (conj {:rule :sensitive-pi-without-separate-consent
                :detail (str subject " は敏感个人信息を収集するが単独同意(separate consent)が未取得"
                             " [个人信息保护法]")})

         (and (true? (:cross-border-transfer? v))
              (not (facts/lawful-cross-border-basis?
                    (:jurisdiction v) (:cross-border-basis v))))
         (conj {:rule :cross-border-transfer-without-lawful-basis
                :detail (str subject " は个人信息の国外提供を行うが根拠("
                             (pr-str (:cross-border-basis v))
                             ")が第三十八条の認める4類型(安全评估/保护认证/标准合同/法定その他)に該当しない")}))))))

(defn- sample-size-violations
  "The survey may not plan to field beyond the scope its own approval
  covers. An un-checkable comparison is its own HARD violation."
  [{:keys [op subject]} st]
  (when (= op :survey/field)
    (let [v (store/survey st subject)]
      ;; Only meaningful where an approval scope exists at all: a
      ;; foreign-related MARKET survey has no per-project approval to
      ;; exceed, so it is not held for lacking an approved sample size.
      (when (and (true? (:foreign-related? v))
                 (facts/requires-per-project-approval? (:jurisdiction v) (:survey-kind v)))
        (cond
          (not (registry/planned-sample-exceeds-approved-checkable? v))
          [{:rule :approved-sample-uncheckable
            :detail (str subject " は実施予定標本数(" (pr-str (:planned-sample-size v))
                         ")または承認標本数(" (pr-str (:approved-sample-size v))
                         ")が未記録で範囲判定不能 -- 判定不能は承認範囲内ではない")}]

          (registry/planned-sample-exceeds-approved? v)
          [{:rule :planned-sample-exceeds-approved
            :detail (str subject " の実施予定標本数(" (:planned-sample-size v)
                         ")が项目审批の承認標本数(" (:approved-sample-size v) ")を超過")}]

          :else nil)))))

(defn- already-filed-violations
  [{:keys [op subject]} st]
  (when (= op :project/file)
    (when (store/survey-already-filed? st subject)
      [{:rule :already-filed
        :detail (str subject " は既に项目审批申请提出済み")}])))

(defn- already-fielded-violations
  [{:keys [op subject]} st]
  (when (= op :survey/field)
    (when (store/survey-already-fielded? st subject)
      [{:rule :already-fielded
        :detail (str subject " は既に実施済み")}])))

(defn check
  "Censors a ForeignSurvey-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (prohibited-content-violations request st)
                           (survey-kind-violations request st)
                           (permit-violations request st)
                           (project-approval-violations request st)
                           (pipl-violations request st)
                           (sample-size-violations request st)
                           (already-filed-violations request st)
                           (already-fielded-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
