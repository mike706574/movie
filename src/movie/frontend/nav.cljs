(ns movie.frontend.nav)

(defn page-link
  [label f]
  [:li.page-item
   {:key label}
   [:a.page-link
    {:style {"cursor" "pointer"
             "color" "#0275d8"}
     :on-click f}
    label]])

(defn previous-link
  [f]
  [:li.page-item
   {:key "previous"}
   [:a.page-link
    {:aria-label "Previous"
     :style {"cursor" "pointer"
             "color" "#0275d8"}
     :on-click f}
    [:span {:aria-hidden "true"} "«"]]])

(defn disabled-previous-link
  []
  [:li.page-item.disabled
   {:key "previous"}
   [:a.page-link
    {:aria-label "Previous"
     :style {"cursor" "pointer"
             "color" "#0275d8"}}
    [:span {:aria-hidden "true"} "«"]]])

(defn next-link
  [f]
  [:li.page-item
   {:key "next"}
   [:a.page-link
    {:aria-label "Next"
     :style {"cursor" "pointer"
             "color" "#0275d8"}
     :on-click f}
    [:span {:aria-hidden "true"} "»"]]])

(defn disabled-next-link
  []
  [:li.page-item.disabled
   {:key "next"}
   [:a.page-link
    {:aria-label "Next"
     :style {"cursor" "pointer"
             "color" "#0275d8"}}
    [:span {:aria-hidden "true"} "»"]]])

(defn disabled-page-link
  [label]
  [:li.page-item.disabled
   {:key label}
   [:span.page-link label]])

(defn active-page-link
  [label]
  [:li.page-item.active
   {:key label}
   [:span.page-link label]])
