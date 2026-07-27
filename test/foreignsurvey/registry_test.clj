(ns foreignsurvey.registry-test
  "The pure record layer: it never mints a 许可证 or an approval number,
  its ceiling and validity checks distinguish 'within scope' from
  'un-checkable', and its dates order correctly without a date library."
  (:require [clojure.test :refer [deftest is testing]]
            [foreignsurvey.registry :as registry]))

;; ----------------------------- ceiling -----------------------------

(deftest planned-sample-ceiling
  (is (true?  (registry/planned-sample-exceeds-approved?
               {:planned-sample-size 5000 :approved-sample-size 1000})))
  (is (false? (registry/planned-sample-exceeds-approved?
               {:planned-sample-size 1000 :approved-sample-size 1000}))
      "equal to the approved scope is within it"))

(deftest an-unrecorded-approved-sample-is-uncheckable-not-within-scope
  (testing "the predicate alone reads as 'not over' -- which is why callers must ask checkable? first"
    (is (false? (registry/planned-sample-exceeds-approved?
                 {:planned-sample-size 700 :approved-sample-size nil}))))
  (is (false? (registry/planned-sample-exceeds-approved-checkable?
               {:planned-sample-size 700 :approved-sample-size nil})))
  (is (false? (registry/planned-sample-exceeds-approved-checkable?
               {:planned-sample-size nil :approved-sample-size 1000})))
  (is (true?  (registry/planned-sample-exceeds-approved-checkable?
               {:planned-sample-size 700 :approved-sample-size 1000}))))

;; ----------------------------- permit / approval numbers -----------------------------

(deftest numbers-on-file
  (is (true?  (registry/permit-on-file? {:permit-number "涉外调查许可证第京0001号"})))
  (is (false? (registry/permit-on-file? {:permit-number ""})))
  (is (false? (registry/permit-on-file? {:permit-number "   "})) "whitespace is not a permit")
  (is (false? (registry/permit-on-file? {:permit-number nil})))
  (is (false? (registry/permit-on-file? {})))
  (is (true?  (registry/project-approval-on-file? {:project-approval-number "国统涉审字[2026]第015号"})))
  (is (false? (registry/project-approval-on-file? {:project-approval-number ""})))
  (is (false? (registry/project-approval-on-file? {}))))

(deftest permit-expiry-orders-correctly
  (is (true?  (registry/permit-elapsed? {:permit-valid-until "2026-06-30"
                                         :field-start-date "2026-09-01"})))
  (is (false? (registry/permit-elapsed? {:permit-valid-until "2028-01-01"
                                         :field-start-date "2026-09-01"})))
  (testing "valid through the last day -- expiry is strictly before the field start"
    (is (false? (registry/permit-elapsed? {:permit-valid-until "2026-09-01"
                                           :field-start-date "2026-09-01"}))))
  (testing "year boundaries order correctly under string comparison"
    (is (true? (registry/permit-elapsed? {:permit-valid-until "2025-12-31"
                                          :field-start-date "2026-01-01"})))))

(deftest a-non-iso-date-is-uncheckable-not-still-valid
  (doseq [bad ["2026/09/01" "1 Sep 2026" "2026-9-1" "" nil 20260901]]
    (is (false? (registry/permit-elapsed-checkable?
                 {:permit-valid-until bad :field-start-date "2026-09-01"}))
        (str "valid-until " (pr-str bad)))
    (is (false? (registry/permit-elapsed? {:permit-valid-until bad
                                           :field-start-date "2026-09-01"}))
        "and the predicate alone reads as 'not elapsed' -- hence the separate checkable? gate"))
  (is (true? (registry/permit-elapsed-checkable?
              {:permit-valid-until "2026-06-30" :field-start-date "2026-09-01"}))))

(deftest iso-date-recogniser
  (is (true?  (registry/iso-date? "2026-07-27")))
  (is (false? (registry/iso-date? "2026-07-27T00:00:00Z")))
  (is (false? (registry/iso-date? 20260727))))

;; ----------------------------- records -----------------------------

(deftest project-filing-record-is-an-unsigned-draft
  (let [r (registry/register-project-filing "srv-1" "CHN" 0)]
    (is (= "CHN-APL-000000" (get r "filing_number")))
    (is (= "foreign-survey-project-filing-draft" (get-in r ["record" "kind"])))
    (is (true? (get-in r ["record" "immutable"])))
    (testing "the actor never claims a registry issued this"
      (is (nil? (get-in r ["certificate" "proof"])))
      (is (false? (get-in r ["certificate" "issued_by_registry"])))
      (is (= "draft-unsigned" (get-in r ["certificate" "status"]))))
    (testing "it does NOT mint an approval number -- only the authority issues one"
      (is (nil? (get-in r ["record" "project_approval_number"]))))))

(deftest fieldwork-record-carries-the-survey-kind
  (let [r (registry/register-fieldwork "srv-1" "CHN" 3 :social)]
    (is (= "CHN-FLD-000003" (get r "fieldwork_number")))
    (is (= "social" (get-in r ["record" "survey_kind"])) "stored as a string, not a keyword")
    (testing "the 3-arity omits the field rather than inventing one"
      (is (nil? (get-in (registry/register-fieldwork "srv-1" "CHN" 3) ["record" "survey_kind"]))))))

(deftest records-validate-their-required-fields
  (doseq [f [registry/register-project-filing registry/register-fieldwork]]
    (is (thrown? clojure.lang.ExceptionInfo (f "" "CHN" 0)))
    (is (thrown? clojure.lang.ExceptionInfo (f "srv-1" "" 0)))
    (is (thrown? clojure.lang.ExceptionInfo (f "srv-1" "CHN" -1)))))

(deftest append-is-append-only
  (let [r1 (registry/register-project-filing "srv-1" "CHN" 0)
        r2 (registry/register-project-filing "srv-2" "CHN" 1)
        h (-> [] (registry/append r1) (registry/append r2))]
    (is (= 2 (count h)))
    (is (= "CHN-APL-000000" (get (first h) "record_id")))
    (is (= "CHN-APL-000001" (get (second h) "record_id")))))
