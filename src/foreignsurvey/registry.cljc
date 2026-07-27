(ns foreignsurvey.registry
  "Pure-function 涉外社会调查项目审批 filing-draft + fieldwork record
  construction -- an append-only market-research book-of-record draft.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a foreign-related survey filing -- the
  国家统计局 and each provincial statistical bureau assign their own
  许可证 and 审批 numbers. This namespace does NOT invent one; it builds
  a jurisdiction-scoped sequence number for the operator's OWN book of
  record and validates the record's required fields. A real 涉外调查许可证
  or project-approval number only ever arrives from the statistical
  authority -- `permit-on-file?` / `project-approval-on-file?` check that
  the operator recorded one, they do NOT mint one.

  `planned-sample-exceeds-approved?` is a reapplication of this fleet's
  MAXIMUM-ceiling check family, comparing a survey's planned sample size
  against the sample size its own approval actually covers -- fielding
  beyond the approved scope is fielding outside the approval.

  `permit-elapsed?` is the analogous MINIMUM-validity check for the
  许可证's own expiry. Dates are compared as ISO-8601 `YYYY-MM-DD`
  strings, which order lexicographically -- portable across JVM and JS
  with no date library and no timezone to get wrong.

  This namespace is pure data + pure functions -- no I/O, no network call
  to any real statistical authority. It builds the RECORD a research
  operator would keep, not the act of going to field (that is
  `foreignsurvey.operation`'s `:survey/field`, always human-gated -- see
  README Actuation)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  research operator's own act, not this actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- ceiling / validity -----------------------------

(defn planned-sample-exceeds-approved?
  "Does `survey`'s own `:planned-sample-size` exceed the
  `:approved-sample-size` its approval actually covers?"
  [{:keys [planned-sample-size approved-sample-size]}]
  (and (number? planned-sample-size) (number? approved-sample-size)
       (> planned-sample-size approved-sample-size)))

(defn planned-sample-exceeds-approved-checkable?
  "Are both sides actually recorded? The predicate above answers only
  `over` / `not over`, so an unrecorded figure would fall through as
  `not over`. Callers must ask this first: un-checkable is not within
  the approved scope."
  [{:keys [planned-sample-size approved-sample-size]}]
  (boolean (and (number? planned-sample-size) (number? approved-sample-size))))

(def ^:private iso-date-re #"^\d{4}-\d{2}-\d{2}$")

(defn iso-date?
  "Is `s` an ISO-8601 `YYYY-MM-DD` date string? Only these compare
  correctly under lexicographic ordering, so anything else is refused
  rather than silently mis-ordered."
  [s]
  (boolean (and (string? s) (re-matches iso-date-re s))))

(defn- on-file?
  [v]
  (boolean (and (string? v) (not (str/blank? v)))))

(defn permit-on-file?
  "Did the operator actually record a 涉外调查许可证 number? A blank or
  whitespace-only string is not a permit on file."
  [{:keys [permit-number]}]
  (on-file? permit-number))

(defn project-approval-on-file?
  "Did the operator actually record a 涉外社会调查项目审批 number?"
  [{:keys [project-approval-number]}]
  (on-file? project-approval-number))

(defn permit-elapsed?
  "Has the recorded 涉外调查许可证 expired as of the survey's own
  `:field-start-date`?"
  [{:keys [permit-valid-until field-start-date]}]
  (and (iso-date? permit-valid-until) (iso-date? field-start-date)
       (neg? (compare permit-valid-until field-start-date))))

(defn permit-elapsed-checkable?
  "Are both dates recorded, and in a form that orders correctly?
  Un-checkable is not 'still valid'."
  [{:keys [permit-valid-until field-start-date]}]
  (boolean (and (iso-date? permit-valid-until) (iso-date? field-start-date))))

;; ----------------------------- records -----------------------------

(defn register-project-filing
  "Validate + construct the 涉外社会调查项目审批 FILING DRAFT -- the
  operator's own act of preparing an approval application for the
  statistical authority. Pure function -- does not touch any real
  portal, and never mints an approval number."
  [survey-id jurisdiction sequence]
  (when-not (and survey-id (not= survey-id ""))
    (throw (ex-info "project-filing: survey_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "project-filing: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "project-filing: sequence must be >= 0" {})))
  (let [filing-number (str (str/upper-case jurisdiction) "-APL-" (zero-pad sequence 6))
        record {"record_id" filing-number
                "kind" "foreign-survey-project-filing-draft"
                "survey_id" survey-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "filing_number" filing-number
     "certificate" (unsigned-certificate "ForeignSurveyProjectFiling" filing-number filing-number)}))

(defn register-fieldwork
  "Validate + construct the FIELDWORK record -- the operator's own act of
  actually going to field with real respondents (always human-gated
  upstream). The 4-arity records WHICH survey kind was fielded; the
  fieldwork NUMBER stays jurisdiction-scoped, because the sequence is
  the operator's own book-of-record counter and re-scoping it per kind
  would silently renumber every fieldwork already recorded."
  ([survey-id jurisdiction sequence]
   (register-fieldwork survey-id jurisdiction sequence nil))
  ([survey-id jurisdiction sequence survey-kind]
   (when-not (and survey-id (not= survey-id ""))
     (throw (ex-info "fieldwork: survey_id required" {})))
   (when-not (and jurisdiction (not= jurisdiction ""))
     (throw (ex-info "fieldwork: jurisdiction required" {})))
   (when (< sequence 0)
     (throw (ex-info "fieldwork: sequence must be >= 0" {})))
   (let [fieldwork-number (str (str/upper-case jurisdiction) "-FLD-" (zero-pad sequence 6))
         record (cond-> {"record_id" fieldwork-number
                         "kind" "foreign-survey-fieldwork"
                         "survey_id" survey-id
                         "jurisdiction" jurisdiction
                         "immutable" true}
                  survey-kind (assoc "survey_kind" (name survey-kind)))]
     {"record" record "fieldwork_number" fieldwork-number
      "certificate" (unsigned-certificate "ForeignSurveyFieldwork" fieldwork-number fieldwork-number)})))

(defn append [history result]
  (conj (vec history) (get result "record")))
