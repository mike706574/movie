(ns movie.frontend.table)

(defn get-path [row path]
  (if (seq? path)
    (get-in row path)
    (get row path)))

(defn get-value [idx row col]
  (let [{:keys [render path format]} col]
    (cond
      render (render {:idx idx :row row})
      path (let [raw-value (get-path row path)
                 value (if format
                         (format raw-value)
                         raw-value)]
             value)
      :else (throw (ex-info "Invalid column" col)))))

(defn responsive-table [{:keys [cols rows row-key style]}]
  [:div.table-responsive
   {:style (merge {"padding" "1rem"
                   "border" "1px solid #dee2e6"}
                  style)}
   [:table.table
    [:thead
     [:tr
      (for [{:keys [key label] :as col} cols]
        [:th
         {:key key
          :scope "col"}
         label])]]
    [:tbody
     (for [[idx row] (map-indexed vector rows)]
       [:tr
        {:key (get-path row row-key)}
        (for [{key :key :as col} cols]
          [:td
           {:key key}
           (get-value idx row col)])])]]])
