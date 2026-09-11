(ns foreignsurvey.facts-test
  "The non-fabrication discipline as executable tests, plus the one
  distinction this catalog exists to keep straight: 涉外社会调查 needs
  per-project approval, 涉外市场调查 does not."
  (:require [clojure.test :refer [deftest is testing]]
            [foreignsurvey.facts :as facts]))

(deftest chn-has-a-cited-spec-basis
  (let [sb (facts/spec-basis "CHN")]
    (is (some? sb))
    (is (re-find #"国家统计局" (:owner-authority sb)))
    (testing "every citation carries a real, fetched official URL"
      (is (re-find #"^https://www\.stats\.gov\.cn/" (:provenance sb)))
      (is (re-find #"^https://www\.cac\.gov\.cn/" (:pipl-provenance sb))))
    (testing "the retrieval date is recorded, so a stale citation is visible"
      (is (= "2026-07-27" (:retrieved-at sb))))))

(deftest uncovered-jurisdiction-has-no-spec-basis
  (is (nil? (facts/spec-basis "ATL")))
  (is (= [] (facts/evidence-checklist "ATL")))
  (is (nil? (facts/required-evidence-satisfied? "ATL" ["anything"])))
  (is (nil? (facts/prohibited-content "ATL")))
  (is (= [] (facts/prohibited-flags "ATL" [:state-secrets])))
  (is (false? (facts/requires-per-project-approval? "ATL" :social)))
  (is (false? (facts/recognized-survey-kind? "ATL" :market)))
  (is (false? (facts/lawful-cross-border-basis? "ATL" :standard-contract))))

(deftest coverage-is-reported-honestly
  (let [c (facts/coverage ["CHN" "JPN" "ATL"])]
    (is (= 3 (:requested c)))
    (is (= 1 (:covered c)))
    (is (= ["CHN"] (:covered-jurisdictions c)))
    (is (= ["ATL" "JPN"] (:missing-jurisdictions c)))))

(deftest evidence-checklist-must-be-fully-satisfied
  (let [checklist (facts/evidence-checklist "CHN")]
    (is (= 4 (count checklist)))
    (is (true? (facts/required-evidence-satisfied? "CHN" checklist)))
    (is (false? (facts/required-evidence-satisfied? "CHN" (butlast checklist))))))

(deftest the-market-social-split-is-the-point
  (testing "社会调查 needs per-project approval (第八条/第九条)"
    (is (true? (facts/requires-per-project-approval? "CHN" :social))))
  (testing "市场调查 needs the permit only -- holding it on an approval would block legitimate research"
    (is (false? (facts/requires-per-project-approval? "CHN" :market))))
  (testing "an unrecognized kind is not swept into either"
    (is (false? (facts/requires-per-project-approval? "CHN" :ethnographic)))
    (is (false? (facts/requires-per-project-approval? "CHN" nil)))))

(deftest recognized-survey-kinds-are-exactly-the-two-the-regulation-defines
  (is (true?  (facts/recognized-survey-kind? "CHN" :market)))
  (is (true?  (facts/recognized-survey-kind? "CHN" :social)))
  (is (false? (facts/recognized-survey-kind? "CHN" :ethnographic)))
  (is (false? (facts/recognized-survey-kind? "CHN" :omnibus)))
  (is (false? (facts/recognized-survey-kind? "CHN" nil))))

(deftest every-per-project-approval-kind-is-a-recognized-kind
  (let [{:keys [per-project-approval-kinds recognized-survey-kinds]} (facts/spec-basis "CHN")]
    (is (every? recognized-survey-kinds per-project-approval-kinds)
        "a kind cannot need approval without being a kind the regulation defines")))

(deftest prohibited-content-is-a-closed-cited-set
  (let [pc (facts/prohibited-content "CHN")]
    (is (= 7 (count pc)) "第七条 enumerates seven prohibitions")
    (is (every? string? (vals pc)) "each carries the text that bans it"))
  (testing "flags are matched against that closed set, and the result is deterministic"
    (is (= [:endangers-state-unity :state-secrets]
           (facts/prohibited-flags "CHN" [:state-secrets :endangers-state-unity])))
    (is (= [] (facts/prohibited-flags "CHN" [:asks-about-brand-preference])))
    (is (= [] (facts/prohibited-flags "CHN" [])))))

(deftest cross-border-bases-are-exactly-the-four-pipl-recognizes
  (doseq [b [:security-assessment :certification :standard-contract :other-statutory]]
    (is (true? (facts/lawful-cross-border-basis? "CHN" b)) (str b)))
  (doseq [b [:handshake-agreement :client-said-it-was-fine nil]]
    (is (false? (facts/lawful-cross-border-basis? "CHN" b)) (str b))))
