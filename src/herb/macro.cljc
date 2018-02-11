(ns herb.macro
  #?(:clj
     (:require
       [garden.core :refer [css]]
       [clojure.string :as str]
       [garden.selectors :as s]
       [garden.stylesheet :refer [at-media at-keyframes]]))
  #?(:cljs
     (:require
       [garden.core :refer [css]]
       [clojure.string :as str]
       [garden.selectors :as s]
       [garden.stylesheet :refer [at-media at-keyframes]]
       [herb.runtime])))

(defn- cljs-env?
  "Take the &env from a macro, and tell whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))

(defn- convert-modes
  "Takes a map of modes and returns a formatted vector fed into garden using
  the :&:mode parent selector syntax"
  [modes]
  (map #(-> [(keyword (str "&" %)) (% modes)])
        (keys modes)))

(defn convert-media
  "Takes a vector of media queries i.e [{:screen true} {:color \"green\"}] and
  calls at-media for each query, and use garden's ancestor selector (:&) to
  target current classname."
  [media]
  (map (fn [[query style]]
         (at-media query [:& style]))
       media))

(defn resolve-styles
  "Calls each function provided in extend-meta to resolve style maps for each"
  [parsed-meta result]
  (if (empty? parsed-meta)
    result
    (let [input (first parsed-meta)]
      (if (fn? input)
        (recur (rest parsed-meta)
               (conj result (input)))
        (let [style-fn (first input)
              style-args (rest input)]
          (recur
           (rest parsed-meta)
           (conj result (apply style-fn style-args))))))))

(defn process-meta-xform
  "Return a transducer that pulls out a given meta type from a sequence"
  [meta-type]
  (comp
   (map (comp meta-type meta))
   (filter identity)))

(defn walk-ancestors
  "Recursivly go through each extend function provided in extend meta and resolve
  style for each until we have nothing left, then return a flat vector of the
  extend chain ready to be fed into garden"
  [extend-meta result]
  (cond

    (fn? extend-meta)
    (recur [extend-meta] result)

    (and (vector? extend-meta) (not (empty? extend-meta)))
    (let [styles (resolve-styles extend-meta [])
          new-meta (into [] (process-meta-xform :extend) styles)]
      (recur new-meta
             (into styles result)))
    :else result))

(defn extract-meta
  "Takes a group of ancestors and the root style fn meta and meta type. Pull out
  each meta obj and merge to prevent duplicates, finally convert to garden
  acceptable input and return"
  [ancestors# root-meta meta-type]
  (let [convert-fn (case meta-type
                     :media convert-media
                     :mode convert-modes)
        extracted (into [] (process-meta-xform meta-type) ancestors#)
        merged (apply merge {} (conj extracted (meta-type root-meta)))
        converted (convert-fn merged)]
    converted))

(defmacro with-style
  "Takes a function that returns a map and transform into CSS using garden, inject
  into DOM and return classname"
  [style-fn & args]
  (let [css (symbol "garden.core" "css")
        inject-style-fn (symbol "herb.runtime" "inject-style!")]
    `(do
       (assert (fn? ~style-fn) (str (pr-str ~style-fn) " is not a function. with-style only takes a function as its first argument"))
       (let [resolved# (~style-fn ~@args)
             fn-name# (-> #'~style-fn meta :name str)
             caller-ns# (-> #'~style-fn meta :ns str)
             fqn# (str caller-ns# "/" fn-name#)
             meta# (meta resolved#)
             ancestors# (walk-ancestors (:extend meta#) [])
             key# (if (keyword? (:key meta#))
                    (name (:key meta#))
                    (:key meta#))
             classname# (str (str/replace caller-ns# #"\." "_") "_" fn-name# (when key# (str "-" key#)))]
         (assert (map? resolved#) "with-style functions must return a map")
         (let [garden-data# [(str "." classname#)
                             (apply merge {} (into ancestors# resolved#))
                             (extract-meta ancestors# meta# :mode)
                             (extract-meta ancestors# meta# :media)]]
           (~inject-style-fn classname# garden-data# fqn#)
           classname#)))))
