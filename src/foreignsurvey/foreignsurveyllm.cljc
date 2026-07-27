(ns foreignsurvey.foreignsurveyllm
  "ForeignSurvey-LLM client -- the *contained intelligence node* for the
  Chinese foreign-related survey compliance actor.

  It normalizes survey intake, drafts a per-jurisdiction regulatory
  checklist, drafts the 涉外社会调查项目审批 filing action, and drafts the
  fieldwork action. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never a
  committed record and never real fieldwork. Every output is censored
  downstream by `foreignsurvey.governor` before anything touches the
  SSoT, and `:project/file`/`:survey/field` proposals NEVER auto-commit
  at any phase -- see README Actuation.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end."
  (:require [foreignsurvey.facts :as facts]
            [foreignsurvey.store :as store]))

(defn- normalize-intake
  [_db {:keys [patch]}]
  {:summary    (str "调查项目記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :survey/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-regime
  "Per-jurisdiction foreign-survey regime checklist draft. `:no-spec?`
  injects the failure mode we must defend against: proposing a checklist
  for a jurisdiction with NO official spec-basis."
  [db {:keys [subject no-spec?]}]
  (let [v (store/survey db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction v))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式 spec-basis が見つかりません")
       :rationale  "foreignsurvey.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [needs-approval? (facts/requires-per-project-approval? iso3 (:survey-kind v))]
        {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                          (count (:required-evidence sb)) " 件"
                          (if needs-approval?
                            " + 当該种别は项目审批の対象"
                            " (当該种别は许可证のみで项目审批は不要)")
                          " を提案")
         :rationale  (str "公式ソース: " (:provenance sb)
                          " / 法的根拠: " (:legal-basis sb)
                          " / 许可: " (:permit-article sb)
                          (when needs-approval? (str " / 审批: " (:approval-article sb)))
                          (when (or (true? (:collects-sensitive-personal-info? v))
                                    (true? (:cross-border-transfer? v)))
                            (str " / 个人信息: " (:pipl-legal-basis sb)
                                 " (" (:pipl-provenance sb) ")")))
         :cites      (cond-> [(:legal-basis sb) (:provenance sb)]
                       (or (true? (:collects-sensitive-personal-info? v))
                           (true? (:cross-border-transfer? v)))
                       (conj (:pipl-legal-basis sb) (:pipl-provenance sb)))
         :effect     :assessment/set
         :value      {:jurisdiction iso3
                      :checklist (:required-evidence sb)
                      :spec-basis (:provenance sb)
                      :legal-basis (:legal-basis sb)
                      :requires-per-project-approval? needs-approval?
                      :permit-authority (:permit-authority sb)}
         :stake      nil
         :confidence 0.9}))))

(defn- propose-project-filing
  "Draft the actual 项目审批 FILING action. ALWAYS `:stake
  :actuation/file-project`."
  [db {:keys [subject]}]
  (let [v (store/survey db subject)]
    {:summary    (str subject " 向け涉外社会调查项目审批申请提案"
                      (when v (str " (operator=" (:operator v) ")")))
     :rationale  (if v
                   (str "jurisdiction=" (:jurisdiction v)
                        " survey-kind=" (pr-str (:survey-kind v))
                        " client=" (:client v))
                   "survey が見つかりません")
     :cites      (if v [subject] [])
     :effect     :survey/mark-filed
     :value      {:survey-id subject}
     :stake      :actuation/file-project
     :confidence (if v 0.9 0.3)}))

(defn- propose-fieldwork
  "Draft the actual FIELDWORK action. ALWAYS `:stake
  :actuation/field-survey` -- real respondents."
  [db {:keys [subject]}]
  (let [v (store/survey db subject)
        needs-approval? (and v (facts/requires-per-project-approval?
                                (:jurisdiction v) (:survey-kind v)))]
    {:summary    (str subject " 向け実施(fieldwork)提案"
                      (when v (str " (planned-n=" (:planned-sample-size v) ")")))
     :rationale  (if v
                   (str "foreign-related?=" (:foreign-related? v)
                        " permit=" (pr-str (:permit-number v))
                        " requires-project-approval?=" (boolean needs-approval?)
                        " project-approval=" (pr-str (:project-approval-number v)))
                   "survey が見つかりません")
     :cites      (if v [subject] [])
     :effect     :survey/mark-fielded
     :value      {:survey-id subject}
     :stake      :actuation/field-survey
     ;; The advisor's own confidence is a HINT, never the gate: the
     ;; governor re-derives every one of these conditions from the store
     ;; independently. It is lowered here only so the audit trail shows
     ;; the advisor itself was unsure.
     :confidence (if (and v
                          (or (not (:foreign-related? v))
                              (seq (str (:permit-number v)))))
                   0.9 0.3)}))

(defprotocol Advisor
  (-advise [this db request] "Return a proposal map for `request`."))

(defrecord MockAdvisor []
  Advisor
  (-advise [_ db {:keys [op] :as request}]
    (case op
      :survey/intake  (normalize-intake db request)
      :regime/assess  (assess-regime db request)
      :project/file   (propose-project-filing db request)
      :survey/field   (propose-fieldwork db request)
      {:summary "unknown op" :rationale "unsupported" :cites []
       :effect :noop :value {} :stake nil :confidence 0.0})))

(defn mock-advisor [] (->MockAdvisor))

(defn trace [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :summary (:summary proposal)
   :confidence (:confidence proposal)
   :stake (:stake proposal)})
