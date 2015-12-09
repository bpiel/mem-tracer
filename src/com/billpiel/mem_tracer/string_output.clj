(ns com.billpiel.mem-tracer.string-output
  (:require [puget.printer :as puget]))

(def pprint-str #(puget.printer/cprint-str %
                                           {:color-scheme { ; syntax elements
                                                           :delimiter [:red]
                                                           :tag       [:red]
                                        ; primitive values
                                                           :nil       [:white]
                                                           :boolean   [:green]
                                                           :number    [:cyan]
                                                           :string    [:magenta]
                                                           :character [:magenta]
                                                           :keyword   [:yellow]
                                                           :symbol    nil

                                        ; special types
                                                           :function-symbol [:cyan]
                                                           :class-delimiter [:cyan]
                                                           :class-name      [:cyan]}}))

(defn base-indent-slinky
  [& {:keys [override underride]
      :or {override [] underride []}}]
  (concat override
          [:start " "
           :end " "
           :default (fn [i]
                      {:fg* i :text "|"})]
          underride))

(defn arg-indent-slinky
  [& {:keys [start end]
      :or {start " "
           end " "}}]
  (base-indent-slinky :override [:start start
                                 -1 "-"
                                 :end end]))

(defn fn-header-indent-slinky
  []
  (base-indent-slinky :override [-1 (fn [i]
                                      {:fg* i :text "v"})]))

(defn fn-footer-indent-slinky
  []
  (base-indent-slinky :override [-1 (fn [i]
                                      {:fg* i :text "^"})]))

(defn apply-color-palette
  [n]
  (nth [1 3 2 6 4 5]
       (mod n 6)))

(defn color-code
  [& {:keys [fg bg fg* bg* bold]}]
  (let [ansi #(conj % (+ %2 (mod %3 10)))]
    (->> (cond-> []
           (#{true 1} bold) (conj 1)
           fg (ansi 30 fg)
           bg (ansi 40 bg)
           fg* (ansi 30 (apply-color-palette fg*))
           bg* (ansi 40 (apply-color-palette bg*)))
         (clojure.string/join ";")
         (format "\33[%sm"))))

(defn slicky-match
  [i len sl]
  (when-not (-> sl first #{:start :end})
    (let [[fi se] sl
          [lo hi] (cond (sequential? fi) fi
                        (= :default fi) [java.lang.Integer/MIN_VALUE java.lang.Integer/MAX_VALUE]
                        (number? fi) [fi fi])]
      (when (or (<= lo i hi)
                (<= lo (- i len) hi))
        se))))

(defn slinky-first-match
  [sl i len]
  (some (partial slicky-match i len)
        (partition 2 sl)))

(defn slinky-map->str
  [m]
  (str (->> (dissoc m :text)
            (mapcat identity)
            (apply color-code))
       (:text m)))

(defn slinky-part->str
  [p n]
  (cond
    (string? p) p
    (fn? p) (recur (p n) n)
    (map? p) (slinky-map->str p)))

(defn slinky->str
  [sl n]
  (let [[& {:keys [start end]}] sl]
    (->> [[(slinky-part->str start nil)]
          (map #(slinky-part->str
                 (slinky-first-match sl
                                     %
                                     n)
                 %)
               (range 0 n))
          [(slinky-part->str end nil)]]
         (apply concat)
         (apply str))))

(def reset-color-code (color-code))

(defn indent
  [depth & rest]
  (slinky->str (apply base-indent-slinky rest)
               depth))

(defn indent-line-breaks
  [s depth & rest]
  (clojure.string/join ""
                       (mapcat (fn [line] [(apply indent depth rest)
                                           line
                                           reset-color-code
                                           "\n"])
                               (clojure.string/split-lines s))))

(defn header->string
  [entry]
  (let [{:keys [depth name]} entry]
    (if name
      (clojure.string/join "" [(slinky->str #spy/d (fn-header-indent-slinky) depth)
                               (color-code :fg* (dec depth) :bg 0 :bold false)
                               (:name entry)
                               "  "
                               (color-code :fg 7)
                               (:id entry)
                               reset-color-code]))))

(defn args-str
  [entry]
  (when-let [args (:args entry)]
    (indent-line-breaks (clojure.string/join "\n"
                                             (map pprint-str
                                                  args))
                        (:depth entry)
                        :end "   ")))

(defn return-str
  [entry]
  (when-let [return (:return entry)]
    (let [s (pprint-str return)
          mline (some #{\newline} s)]
      (str (indent (:depth entry))
           "return => "
           (if mline
             (str "\n"
                  (indent-line-breaks (str s
                                           "\n")
                                      (:depth entry)
                                      :end "   "))
             (str s))
           "\n"))))

(defn throw-str
  [entry]
  (when-let [thrown (:throw entry)]
    (str (indent (:depth entry))
         (color-code :fg 7 :bg 1 :bold true)
         "THROW"
         reset-color-code
         " => "
         (pprint-str (:cause thrown))
         "\n"
         (indent-line-breaks
          (->> thrown
               :via
               (mapv (fn [v]
                       (let [at (:at v)
                             [c f l] ((juxt :class-name
                                            :file-name
                                            :line-number)
                                      at)]
                         (format "%s %s:%s" c f l))))
               pprint-str)
          (:depth entry))
         "\n")))

(defn entry->string
  [entry & {:keys [post-head pre-args post-args pre-ret post-ret pre-ex post-ex children]
            :or {post-head true pre-args true post-args true pre-ret true post-ret true pre-ex true post-ex true children true}}]
  (let [has-children (some-> entry
                             :children
                             not-empty)]
    (->> [[(header->string entry) "\n"]
          (when pre-args
            (args-str entry))
          (when pre-ret
            (return-str entry))
          (when pre-ex
            (throw-str entry))
          (when has-children
            [(mapcat entry->string
                     (:children entry))
             (when post-head
               [(header->string entry) "\n"])
             (when pre-args
               (args-str entry))
             (when post-ret
               (return-str entry))
             (when post-ex
               (throw-str entry))])
          (slinky->str (fn-footer-indent-slinky)
                       (:depth entry))
          "\n"]
         flatten
         (remove nil?)
         (clojure.string/join ""))))

(defn print-entry
  [entry]
  (-> entry
      entry->string
      print))
