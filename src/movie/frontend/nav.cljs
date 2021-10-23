(ns movie.frontend.nav
  (:require [movie.frontend.util :as util]))

(defn link [{:keys [key label disabled active on-click]}]
  (if active
    [:li.page-item.active
     {:key key}
     [:span.page-link label]]
    [:li.page-item
     {:key key
      :classes (util/classes ["page-item" ["disabled" disabled]])}
     [:a.page-link
      {:style (if disabled {} {"cursor" "pointer"})
       :on-click (when-not disabled on-click)}
      label]]))

(defn previous-link [props]
  (link (merge {:key "previous" :label "‹"} props)))

(defn next-link [props]
  (link (merge {:key "next" :label "›"} props)))

(defn last-link [props]
  (link (merge {:key "last" :label "»"} props)))
