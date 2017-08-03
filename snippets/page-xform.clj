(defn page-xform
  [{page-number :page-number page-size :page-size :as options}]
  (cond
    (and page-number page-size) (comp (drop (* page-size (dec page-number)))
                                      (take page-size))
    (or page-number page-size) (throw (ex-info "Both page-number and page-size are required for pagination." options))))


(def date-comparator #(compare (:movie/time %1) (:movie/time %2)))
