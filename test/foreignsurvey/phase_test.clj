(ns foreignsurvey.phase-test
  "The rollout invariant as executable tests: fieldwork is never
  auto-eligible at any phase, and a governor HOLD survives every phase."
  (:require [clojure.test :refer [deftest is testing]]
            [foreignsurvey.phase :as phase]))

(deftest actuation-is-never-auto-at-any-phase
  (doseq [ph (keys phase/phases)
          op phase/never-auto]
    (is (not (contains? (:auto (get phase/phases ph)) op))
        (str "phase " ph " must never auto-commit " op))
    (is (not (contains? (phase/auto-eligible-ops ph) op)))))

(deftest clean-fieldwork-still-escalates-at-the-highest-phase
  (let [{:keys [disposition reason]} (phase/gate 3 {:op :survey/field} :commit)]
    (is (= :escalate disposition))
    (is (= :phase-approval reason))))

(deftest intake-auto-commits-only-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :survey/intake} :commit))))
  (doseq [ph [1 2]]
    (is (= :escalate (:disposition (phase/gate ph {:op :survey/intake} :commit)))
        (str "phase " ph))))

(deftest a-write-not-enabled-in-this-phase-is-held
  (let [{:keys [disposition reason]} (phase/gate 0 {:op :survey/intake} :commit)]
    (is (= :hold disposition))
    (is (= :phase-disabled reason))))

(deftest governor-hold-survives-every-phase
  (testing "compliance wins -- no phase can turn a HOLD into anything else"
    (doseq [ph (keys phase/phases)
            op phase/write-ops]
      (is (= :hold (:disposition (phase/gate ph {:op op} :hold)))
          (str "phase " ph " / " op)))))

(deftest unknown-phase-falls-back-to-the-default-not-to-permissive
  (is (= :escalate (:disposition (phase/gate 99 {:op :survey/field} :commit)))
      "an unknown phase must not auto-field"))

(deftest verdict-mapping
  (is (= :hold     (phase/verdict->disposition {:hard? true :escalate? true})))
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
  (is (= :commit   (phase/verdict->disposition {:hard? false :escalate? false}))))
