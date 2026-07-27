(ns foreignsurvey.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Trust Controls implemented faithfully. The single invariant under
  test:

    ForeignSurvey-LLM never fields a survey the Survey-Integrity
    Compliance Governor would reject, `:project/file`/`:survey/field`
    NEVER auto-commit at any phase, `:survey/intake` MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [foreignsurvey.store :as store]
            [foreignsurvey.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :research-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :regime/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- last-basis [db] (-> (store/ledger db) last :basis))

;; ----------------------------- happy path -----------------------------

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                     {:op :survey/intake :subject "srv-1"
                      :patch {:id "srv-1" :operator "北京某调查有限公司"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "北京某调查有限公司" (:operator (store/survey db "srv-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest regime-assess-always-needs-approval
  (let [[db actor] (fresh)
        res (exec-op actor "t2" {:op :regime/assess :subject "srv-1"} operator)]
    (is (= :interrupted (:status res)))
    (let [r2 (approve! actor "t2")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (some? (store/assessment-of db "srv-1"))))))

(deftest clean-social-survey-fields-only-after-human-approval
  (let [[db actor] (fresh)
        _ (assess! actor "t3" "srv-1")
        res (exec-op actor "t3-fld" {:op :survey/field :subject "srv-1"} operator)]
    (is (= :interrupted (:status res)) "fieldwork is never auto-committed")
    (let [r2 (approve! actor "t3-fld")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (= 1 (count (store/fieldwork-history db))))
      (is (true? (:fielded? (store/survey db "srv-1")))))))

;; ----------------------------- spec-basis / evidence -----------------------------

(deftest fabricated-jurisdiction-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t4" {:op :regime/assess :subject "srv-1" :no-spec? true} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
    (is (nil? (store/assessment-of db "srv-1")) "no assessment written")))

(deftest fielding-without-assessment-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t5" {:op :survey/field :subject "srv-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:evidence-incomplete} (last-basis db)))))

;; ----------------------------- FLAGSHIP: permit -----------------------------

(deftest foreign-survey-permit-missing-is-held-and-unoverridable
  (testing "foreign-related survey with no 涉外调查许可证 -> HARD hold (flagship check)"
    (let [[db actor] (fresh)
          _ (assess! actor "t6" "srv-3")
          res (exec-op actor "t6-fld" {:op :survey/field :subject "srv-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)) "a human approver is never even offered the choice")
      (is (some #{:foreign-survey-permit-missing} (last-basis db)))
      (is (empty? (store/fieldwork-history db))))))

(deftest permit-is-required-at-filing-too-not-only-at-fieldwork
  (testing "第十条 conditions conducting a foreign-related survey -- filing without a permit is already outside the regime"
    (let [[db actor] (fresh)
          _ (assess! actor "t7" "srv-3")
          res (exec-op actor "t7-fil" {:op :project/file :subject "srv-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:foreign-survey-permit-missing} (last-basis db)))
      (is (empty? (store/filing-history db))))))

(deftest expired-permit-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t8" "srv-6")
        res (exec-op actor "t8-fld" {:op :survey/field :subject "srv-6"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:foreign-survey-permit-elapsed} (last-basis db)))))

(deftest permit-validity-unknown-is-not-treated-as-valid
  (let [[db actor] (fresh)
        _ (swap! (:a db) assoc-in [:surveys "srv-5" :permit-valid-until] "2028/01/01")
        _ (assess! actor "t9" "srv-5")
        res (exec-op actor "t9-fld" {:op :survey/field :subject "srv-5"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:foreign-survey-permit-validity-unknown} (last-basis db)))))

;; ----------------------------- FLAGSHIP: the market/social split -----------------------------

(deftest social-survey-without-project-approval-is-held
  (testing "涉外社会调查 needs per-project approval on top of the permit (第八条/第九条)"
    (let [[db actor] (fresh)
          _ (assess! actor "t10" "srv-4")
          res (exec-op actor "t10-fld" {:op :survey/field :subject "srv-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:social-survey-project-unapproved} (last-basis db)))
      (is (empty? (store/fieldwork-history db))))))

(deftest market-survey-does-NOT-need-a-project-approval
  (testing "the distinction this catalog exists to keep straight -- a permitted 涉外市场调查 proceeds"
    (let [[db actor] (fresh)
          _ (assess! actor "t11" "srv-5")
          res (exec-op actor "t11-fld" {:op :survey/field :subject "srv-5"} operator)]
      (is (= :interrupted (:status res))
          "escalates on actuation only -- NOT held for a missing project approval")
      (is (nil? (:project-approval-number (store/survey db "srv-5")))
          "and it genuinely has no approval number on file")
      (is (= :commit (get-in (approve! actor "t11-fld") [:state :disposition])))
      (is (= 1 (count (store/fieldwork-history db)))))))

(deftest unrecognized-survey-kind-is-held-not-read-as-market
  (testing "an unclassified survey must not inherit 市场调查's lighter obligations"
    (let [[db actor] (fresh)
          _ (assess! actor "t12" "srv-12")
          res (exec-op actor "t12-fld" {:op :survey/field :subject "srv-12"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:survey-kind-unrecognized} (last-basis db))))))

;; ----------------------------- prohibited content -----------------------------

(deftest prohibited-content-is-held-at-filing-and-at-fieldwork
  (doseq [[tid op] [["t13" :project/file] ["t14" :survey/field]]]
    (let [[db actor] (fresh)
          _ (assess! actor tid "srv-7")
          res (exec-op actor (str tid "-x") {:op op :subject "srv-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) (str op))
      (is (some #{:prohibited-content} (last-basis db)) (str op))
      (testing "the ledger records WHICH prohibition was hit"
        (is (= [:state-secrets]
               (-> (store/ledger db) last :violations first :flags)))))))

;; ----------------------------- PIPL -----------------------------

(deftest sensitive-personal-info-without-separate-consent-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t15" "srv-8")
        res (exec-op actor "t15-fld" {:op :survey/field :subject "srv-8"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:sensitive-pi-without-separate-consent} (last-basis db)))))

(deftest cross-border-transfer-without-a-recognized-basis-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t16" "srv-9")
        res (exec-op actor "t16-fld" {:op :survey/field :subject "srv-9"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:cross-border-transfer-without-lawful-basis} (last-basis db)))))

(deftest a-recognized-cross-border-basis-clears-the-check
  (let [[db actor] (fresh)
        _ (swap! (:a db) assoc-in [:surveys "srv-9" :cross-border-basis] :standard-contract)
        _ (assess! actor "t17" "srv-9")
        res (exec-op actor "t17-fld" {:op :survey/field :subject "srv-9"} operator)]
    (is (= :interrupted (:status res)) "escalates on actuation, not held on PIPL")))

;; ----------------------------- sample scope -----------------------------

(deftest planned-sample-beyond-approved-scope-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t18" "srv-10")
        res (exec-op actor "t18-fld" {:op :survey/field :subject "srv-10"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:planned-sample-exceeds-approved} (last-basis db)))))

(deftest unrecorded-approved-sample-is-held-only-where-an-approval-exists
  (testing "a social survey with no recorded approved sample size is un-checkable -> HARD hold"
    (let [[db actor] (fresh)
          _ (swap! (:a db) update-in [:surveys "srv-11"]
                   merge {:survey-kind :social
                          :project-approval-number "国统涉审字[2026]第018号"})
          _ (assess! actor "t19" "srv-11")
          res (exec-op actor "t19-fld" {:op :survey/field :subject "srv-11"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:approved-sample-uncheckable} (last-basis db)))))
  (testing "but a MARKET survey has no approval scope to exceed, so it is not held for lacking one"
    (let [[db actor] (fresh)
          _ (assess! actor "t20" "srv-11")
          res (exec-op actor "t20-fld" {:op :survey/field :subject "srv-11"} operator)]
      (is (= :interrupted (:status res)))
      (is (nil? (:approved-sample-size (store/survey db "srv-11")))
          "and it genuinely has no approved sample size recorded"))))

;; ----------------------------- double-actuation -----------------------------

(deftest double-filing-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t21" "srv-1")
        _ (exec-op actor "t21-fil" {:op :project/file :subject "srv-1"} operator)
        _ (approve! actor "t21-fil")
        res (exec-op actor "t21-again" {:op :project/file :subject "srv-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-filed} (last-basis db)))
    (is (= 1 (count (store/filing-history db))))))

(deftest double-fielding-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t22" "srv-1")
        _ (exec-op actor "t22-fld" {:op :survey/field :subject "srv-1"} operator)
        _ (approve! actor "t22-fld")
        res (exec-op actor "t22-again" {:op :survey/field :subject "srv-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-fielded} (last-basis db)))
    (is (= 1 (count (store/fieldwork-history db))))))

;; ----------------------------- ledger discipline -----------------------------

(deftest every-decision-leaves-exactly-one-ledger-fact
  (let [[db actor] (fresh)
        before (count (store/ledger db))
        _ (exec-op actor "t23" {:op :survey/field :subject "srv-1"} operator)]
    (is (= (inc before) (count (store/ledger db))))))
