(ns foreignsurvey.facts
  "Per-jurisdiction foreign-related survey (涉外调查) regulatory catalog
  for the China market-research actor. G2-style spec-basis table.

  Surface: 涉外调查管理办法 (Administrative Measures on Foreign-Related
  Surveys, 国家统计局令第7号, published and in force 2004-10-13,
  superseding the 1999 涉外社会调查活动管理暂行办法); 中华人民共和国个人
  信息保护法 (PIPL, passed 2021-08-20, in force 2021-11-01).

  Every entry cites an official source URL that was actually fetched and
  read on `:retrieved-at`; a summary states only what the cited source
  confirms. A jurisdiction not in `catalog` has NO spec-basis -- the
  advisor must not fabricate one, and the governor holds if it tries.
  Coverage is reported HONESTLY (see `coverage`).

  The single most important structural fact this catalog encodes: China
  treats a foreign-related MARKET survey and a foreign-related SOCIAL
  survey differently. Both require the conducting organization to hold a
  涉外调查许可证 (第十条); only social surveys additionally require
  per-project approval (第八条/第九条). Conflating the two would either
  block legitimate market research or wave through unapproved social
  research -- so `:per-project-approval-kinds` is a CLOSED set, not a
  boolean.")

(def catalog
  "iso3 -> requirement map."
  {"CHN"
   {:name "People's Republic of China"
    :owner-authority "国家统计局 (National Bureau of Statistics, NBS) 及び省・自治区・直辖市人民政府统计机构"
    :legal-basis "涉外调查管理办法 (国家统计局令第7号, 2004-10-13 公布・施行)"
    :national-spec "涉外调查许可证 (第十条)・涉外社会调查项目审批 (第八条/第九条)・禁止内容 (第七条)"
    :provenance "https://www.stats.gov.cn/zs/flfg/tjlydnfghflfg/202501/t20250117_1958351.html"
    :retrieved-at "2026-07-27"

    :required-evidence ["涉外调查许可证记录 (survey-permit-record)"
                        "委托・合作合同记录 (commission-contract-record)"
                        "调查方案・问卷记录 (methodology-and-questionnaire-record)"
                        "保密制度记录 (confidentiality-regime-record)"]

    ;; --- permit (第十条/第十一条) ---
    :permit-authority "国家统计局 / 省・自治区・直辖市人民政府统计机构 (第十条)"
    :permit-article "涉外调查管理办法第十条 (涉外调查を行う机构は涉外调查许可证を取得しなければならない)"
    :permit-qualification-article "涉外调查管理办法第十一条 (法人格・业务范围・熟悉法规の人员・调查能力・前年度3件または30万元以上の実績・保密制度・2年以内に重大违法なし)"

    ;; --- per-project approval (第八条/第九条) ---
    ;; CLOSED set: social surveys need per-project approval, market
    ;; surveys do not. Anything not in this set is NOT swept in.
    :per-project-approval-kinds #{:social}
    :approval-article "涉外调查管理办法第八条・第九条 (涉外社会调查项目は事前审批を要する; 涉外市场调查は许可证のみで项目审批は不要)"
    :approval-authority-article "涉外调查管理办法第二十三条 (跨省は国家统计局、省内は省级统计机构; 受理から20営業日以内に決定、10日延長可)"
    :approval-submission-article "涉外调查管理办法第二十二条 (申请书・许可证写し・委托/合作合同・调查方案・问卷/访谈提纲・背景资料)"

    ;; --- survey-kind definitions (第三条) ---
    :survey-kind-article "涉外调查管理办法第三条 (市场调查=商品・商业服务に関する情报の収集; 社会调查=问卷・访谈・观察により市场调查の範囲外の社会情报を収集)"
    :recognized-survey-kinds #{:market :social}

    ;; --- prohibited content (第七条) ---
    :prohibited-content-article "涉外调查管理办法第七条"
    :prohibited-content
    {:violates-constitution        "宪法确定的基本原则に违反する内容"
     :endangers-state-unity        "国家统一・主权・领土完整を危害する内容"
     :state-secrets                "窃取・刺探・收买・泄露国家秘密または情报、国家安全を危害し国家利益を损なう内容"
     :harms-ethnic-religious-unity "宗教政策に违反し、民族团结を损なう内容"
     :disrupts-social-order        "経済秩序・社会稳定を扰乱する内容"
     :promotes-cults               "邪教・迷信を宣扬する内容"
     :fraud-or-rights-harm         "诈骗を行い、または他人の合法権益を损なう内容"}

    ;; --- personal information (PIPL) ---
    :pipl-legal-basis "中华人民共和国个人信息保护法 (2021-08-20 通过, 2021-11-01 施行)"
    :pipl-provenance "https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm"
    :pipl-sensitive-article "敏感个人信息の处理には个人の単独同意 (separate consent) と必要性・権益影響の告知が必要"
    :pipl-cross-border-article "个人信息保护法第三十八条 (国外提供は①网信部门の安全评估 ②専門机构の个人信息保护认证 ③网信部门の标准合同 ④法令が定めるその他条件 のいずれかを満たすこと)"
    ;; CLOSED set of the four PIPL 第三十八条 bases. A survey declaring a
    ;; cross-border transfer on any other basis has no lawful basis on
    ;; file as far as this actor is concerned.
    :cross-border-bases #{:security-assessment :certification :standard-contract :other-statutory}

    ;; --- penalties (context only; this actor never assesses a fine) ---
    :penalty-article "涉外调查管理办法第三十一条"
    :penalty-note "无许可证での涉外调查等: 非経営性は500〜1000元、経営性は违法所得の1〜3倍(上限3万元)、违法所得なしは3000〜1万元。犯罪を构成する场合は刑事责任"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to field a survey
  on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-iso3166-chn-market-research R0: " (count catalog)
                 " jurisdiction seeded (the CHN member of the iso3166 "
                 "family). Extend `foreignsurvey.facts/catalog`, never "
                 "fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` satisfy every evidence item listed for `iso3`?
  Missing spec-basis -> never satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn requires-per-project-approval?
  "Does a survey of `kind` need per-project approval in `iso3`, on top of
  the conducting organization's permit? In China this is TRUE for
  :social and FALSE for :market (第八条/第九条 vs 第十条) -- the
  distinction this whole catalog exists to keep straight.

  An unknown jurisdiction or an unrecognized kind answers false here;
  `foreignsurvey.governor` independently refuses to field anything whose
  jurisdiction has no spec-basis and anything whose kind is not
  recognized, so neither can reach the field gate on this answer."
  [iso3 kind]
  (boolean (some-> (spec-basis iso3) :per-project-approval-kinds (contains? kind))))

(defn recognized-survey-kind?
  "Is `kind` a survey kind this jurisdiction's own regulation defines
  (第三条)? An unrecognized kind is not silently treated as a market
  survey -- that would be the permissive reading of an unknown."
  [iso3 kind]
  (boolean (some-> (spec-basis iso3) :recognized-survey-kinds (contains? kind))))

(defn prohibited-content
  "iso3's CLOSED prohibited-content map (flag -> the text that bans it),
  or nil."
  [iso3]
  (:prohibited-content (spec-basis iso3)))

(defn prohibited-flags
  "Which of `flags` are actually prohibited content in `iso3`. Returns a
  sorted vector so a hold's basis is deterministic."
  [iso3 flags]
  (let [banned (set (keys (prohibited-content iso3)))]
    (vec (sort (filter banned (set flags))))))

(defn lawful-cross-border-basis?
  "Is `basis` one of the four conditions PIPL 第三十八条 recognizes for
  providing personal information outside China?"
  [iso3 basis]
  (boolean (some-> (spec-basis iso3) :cross-border-bases (contains? basis))))
