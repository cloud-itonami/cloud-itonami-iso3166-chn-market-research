(ns foreignsurvey.phase
  "Phase 0->3 staged rollout for the China foreign-related survey actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- survey intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds regime assessment writes, still
                                 approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:survey/intake` (no respondent-facing
                                 risk yet) may auto-commit.
                                 `:project/file`/`:survey/field` NEVER
                                 auto-commit, at any phase.

  `:project/file`/`:survey/field` are deliberately ABSENT from every
  phase's `:auto` set, including phase 3 -- a permanent structural fact,
  not a rollout milestone still to come. Filing a real approval
  application with a statistical authority and going to field with real
  respondents are the two real-world acts this actor performs; both are
  always a human research operator's call.
  `foreignsurvey.governor`'s `:actuation/file-project`/
  `:actuation/field-survey` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this."
  (:require [clojure.set :as set]))

(def read-ops  #{})
(def write-ops #{:survey/intake :regime/assess :project/file :survey/field})

(def never-auto
  "The ops no phase may ever auto-commit. `phases` is checked against
  this at load time, so adding one to an `:auto` set fails loudly
  instead of silently weakening the actuation invariant."
  #{:project/file :survey/field})

(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                :auto #{}}
   1 {:label "assisted-intake" :writes #{:survey/intake}                  :auto #{}}
   2 {:label "assisted-assess" :writes #{:survey/intake :regime/assess}   :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:survey/intake}}})

(def default-phase 3)

;; Load-time assertion of the actuation invariant. A future edit that
;; drops `:survey/field` into phase 3's `:auto` set does not get to be a
;; quiet one-word diff.
(let [leaked (->> (vals phases)
                  (mapcat (comp seq :auto))
                  (filter never-auto)
                  set)]
  (when (seq leaked)
    (throw (ex-info "foreignsurvey.phase: actuation op placed in an :auto set"
                    {:leaked leaked}))))

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Survey-Integrity Compliance Governor verdict to a base
  disposition before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))

(defn auto-eligible-ops
  "The ops this phase may auto-commit -- always disjoint from
  `never-auto`."
  [phase]
  (set/difference (:auto (get phases phase (get phases default-phase))) never-auto))
