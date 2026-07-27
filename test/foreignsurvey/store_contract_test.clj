(ns foreignsurvey.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [foreignsurvey.store :as store]))

(defn- exercise [s]
  (store/commit-record! s {:effect :survey/upsert
                           :value {:id "srv-x" :operator "X 调查公司" :client "Foreign Co"
                                   :jurisdiction "CHN" :survey-kind :social
                                   :foreign-related? true
                                   :permit-number "涉外调查许可证第京9999号"
                                   :permit-valid-until "2028-01-01"
                                   :project-approval-number "国统涉审字[2026]第099号"
                                   :field-start-date "2026-09-01"
                                   :planned-sample-size 100 :approved-sample-size 200
                                   :content-flags [:state-secrets]
                                   :collects-sensitive-personal-info? false
                                   :separate-consent-obtained? false
                                   :cross-border-transfer? false :cross-border-basis nil
                                   :filed? false :fielded? false :status :intake}})
  (store/commit-record! s {:effect :assessment/set
                           :path ["srv-x"]
                           :payload {:jurisdiction "CHN" :checklist ["a"] :spec-basis "x"}})
  (store/commit-record! s {:effect :survey/mark-filed :path ["srv-x"]})
  (store/commit-record! s {:effect :survey/mark-fielded :path ["srv-x"]})
  (store/append-ledger! s {:t :committed :op :test})
  {:survey (store/survey s "srv-x")
   :assessment (store/assessment-of s "srv-x")
   :filings (store/filing-history s)
   :fieldworks (store/fieldwork-history s)
   :ledger (store/ledger s)
   :filed? (store/survey-already-filed? s "srv-x")
   :fielded? (store/survey-already-fielded? s "srv-x")})

(defn- empty-mem []
  (store/->MemStore (atom {:surveys {} :assessments {} :ledger []
                           :filing-sequences {} :filing-records []
                           :fieldwork-sequences {} :fieldwork-records []})))

(deftest mem-and-datomic-parity
  (let [m (exercise (empty-mem))
        d (exercise (store/datomic-store {}))]
    (is (= (:operator (:survey m)) (:operator (:survey d))))
    (is (= (:survey-kind (:survey m)) (:survey-kind (:survey d))))
    (is (true? (:filed? m)))
    (is (true? (:filed? d)))
    (is (true? (:fielded? m)))
    (is (true? (:fielded? d)))
    (is (= 1 (count (:filings m))))
    (is (= 1 (count (:filings d))))
    (is (= 1 (count (:fieldworks m))))
    (is (= 1 (count (:fieldworks d))))
    (is (= 1 (count (:ledger m))))
    (is (= 1 (count (:ledger d))))
    (is (= (:assessment m) (:assessment d)))
    (testing "the fieldwork record carries the survey kind on both backends"
      (is (= "social" (get (first (:fieldworks m)) "survey_kind")))
      (is (= "social" (get (first (:fieldworks d)) "survey_kind"))))
    (testing "content flags survive the round trip in order on both backends"
      (is (= [:state-secrets] (vec (:content-flags (:survey m)))))
      (is (= [:state-secrets] (vec (:content-flags (:survey d))))))))

(deftest an-unrecorded-approved-sample-survives-the-datomic-round-trip-as-nil
  (testing "a nil approved sample must NOT come back as 0 -- that would silently pass the scope check"
    (let [s (store/datomic-store {})]
      (store/commit-record! s {:effect :survey/upsert
                               :value {:id "srv-nolimit" :jurisdiction "CHN"
                                       :survey-kind :social :foreign-related? true
                                       :planned-sample-size 700 :approved-sample-size nil
                                       :status :intake}})
      (is (nil? (:approved-sample-size (store/survey s "srv-nolimit")))))))

(deftest an-unrecorded-permit-survives-the-datomic-round-trip-as-nil
  (let [s (store/datomic-store {})]
    (store/commit-record! s {:effect :survey/upsert
                             :value {:id "srv-nopermit" :jurisdiction "CHN"
                                     :survey-kind :market :foreign-related? true
                                     :permit-number nil :status :intake}})
    (is (nil? (:permit-number (store/survey s "srv-nopermit")))
        "not \"\" and not a placeholder -- the permit check must see it as absent")))

(deftest sequences-are-per-jurisdiction-and-monotonic
  (let [s (empty-mem)]
    (doseq [id ["a" "b"]]
      (store/commit-record! s {:effect :survey/upsert
                               :value {:id id :jurisdiction "CHN" :survey-kind :market
                                       :status :intake}})
      (store/commit-record! s {:effect :survey/mark-fielded :path [id]}))
    (is (= ["CHN-FLD-000000" "CHN-FLD-000001"]
           (mapv #(get % "record_id") (store/fieldwork-history s))))))
