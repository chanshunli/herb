(ns herb-demo.components.text
  (:require [garden.units :refer [rem em px]]
            [herb.core :refer-macros [<class]]
            [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn break]]
            [clojure.string :as str]
            [reagent.core :as r]))

(def mappings
  {:title :h4
   :heading :h5
   :subtitle :h5
   :a :span
   :subheading :h3
   :body :p})

(defn colors
  [c]
  (c {:black {:color "#333"}
      :white {:color "white"}}))

(defn px->rem
  [size]
  (let [html-font-size 16
        font-size 14
        coef (/ font-size 14)]
    (str (* (/ size html-font-size) coef) "rem")))

(defn variants
  [variant]
  (let [v {:heading {:font-size (px->rem 24)
                     :margin [[(em 1.2) 0 (em 1.2) 0]]
                     :font-weight 400
                     :font-family ["Raleway" "sans-serif"]}
           :title {:font-size (px->rem 34)
                   :font-weight 400
                   :font-family ["Raleway" "sans-serif"]}
           :subtitle {:font-size (px->rem 24)
                      :font-family ["Raleway" "sans-serif"]
                      :margin [[(em 1.2) 0 (em 1.2) 0]]
                      :font-weight 300}
           :a {:font-size (px->rem 14)
               :font-family ["Open Sans" "sans-serif"]}
           :subheading {:font-size (px->rem 16)
                        :font-weight 400
                        :font-family ["Open Sans" "sans-serif"]}
           :body {:font-size (px->rem 14)
                  :font-family ["Open Sans" "sans-serif"]}}]
    (variant v)))

(defn aligns
  [align]
  (align {:left {:text-align "left"}
          :right {:text-align "right"}
          :center {:text-align "center"}
          :justify {:text-align "justify"}}))

(defn text-style
  [variant color align]
  (let [k (str/join "-" [(name variant) (name color) (name align)])]
    (with-meta
      (merge
       (variants variant)
       (aligns align)
       (colors color))
      {:key k})))

(defn replace-code-ticks
  [child]
  (if (string? child)
    (let [re #"`.*?`"
          matches (re-seq re child)
          text (str/split child #"`")]
      (map (fn [t i]
             (if (seq (filter #(str/includes? % t) matches))
               ^{:key (hash (str t i))}
               [:code t]
               t))
           text
           (range)))
    child))

(defn text
  [{:keys [variant class color align id]
    :or {variant :body
         color :black
         align :left}}]
  (let [children (r/children (r/current-component))
        class* (<class text-style variant color align)]
    (into
     [(variant mappings) {:id id
                          :class (if class
                                   (str/join " " [class* class])
                                   class*)}]
     (mapv replace-code-ticks children)
     )))
