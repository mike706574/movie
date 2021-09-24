(ns movie.frontend.app
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [day8.re-frame.http-fx]
            [movie.frontend.alphabet :as alphabet]
            [movie.frontend.nav :as nav]
            [reagent.dom :as rd]
            [re-frame.core :as rf]))

;; -- Development --------------------------------------------------------------
(enable-console-print!)

(defn includes-ignore-case?
  [string sub]
  (not (nil? (.match string (re-pattern (str "(?i)" sub))))))


;; -- Event Dispatch -----------------------------------------------------------


;; -- Event Handlers -----------------------------------------------------------

(def page-size 12)

(defn movies-request []
  {:method          :get
   :uri             "/api/movies"
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      [:process-movies]
   :on-failure      [:handle-movies-failure]})

(defn initialize [_ _]
  (println "Initializing.")
  {:http-xhrio (movies-request)
   :db {:movie-status :loading
        :page-number nil
        :movie-letter nil
        :movie-letter-input-type :full
        :movies nil}})

(rf/reg-event-fx :initialize initialize)

(defn fetch-movies
  [{db :db} _]
  {:http-xhrio (movies-request)
   :db (assoc db
              :movies nil
              :movie-status :loading)})

(rf/reg-event-fx :fetch-movies fetch-movies)

(rf/reg-event-db
 :process-movies
 (fn [db [_ response]]
   (let [movies (js->clj response)
         new-db (-> db
                    (merge {:movie-status :loaded
                            :page-number 1
                            :movie-letter nil
                            :movies movies}))]
     new-db)))

;; paging
(defn previous-page
  [db _]
  (update db :page-number dec))

(defn next-page
  [db _]
  (update db :page-number inc))

(def to-page
 (fn [db [_ page-number]]
   (assoc db :page-number page-number)))

(rf/reg-event-db :next-page next-page)
(rf/reg-event-db :previous-page previous-page)
(rf/reg-event-db :to-page to-page)

(rf/reg-event-db
 :previous-letter
 (fn [db _]
   (-> db
       (update :movie-letter alphabet/previous)
       (assoc :page-number 1))))

(rf/reg-event-db
 :next-letter
 (fn [db _]
   (-> db
       (update :movie-letter alphabet/next)
       (assoc :page-number 1))))

(rf/reg-event-db
 :to-letter
 (fn [db [_ new-letter]]
   (assoc db :movie-letter new-letter :page-number 1)))

(rf/reg-event-db
 :movie-filter-change
 (fn [db [_ text]]
   (assoc db :movie-filter-text text :page-number 1)))

(rf/reg-event-db
 :toggle-movie-letter-input-type
 (fn [db [_ text]]
   (update db :movie-letter-input-type
           #(case %
              :full :skinny
              :skinny :full))))

(rf/reg-event-db
 :handle-movies-failure
 (fn [db [_ response]]
   (merge db {:movie-status :error
              :error-message (:status-text response)})))


;; -- Query  -------------------------------------------------------------------

(rf/reg-sub
  :movies
  (fn [db _]
    (:movies db)))

(rf/reg-sub
  :movie-state
  (fn [db _]
    (select-keys db [:movie-status :error-message])))

(rf/reg-sub
  :movie-count
  (fn [{:keys [page-number movies] :as db} _]
    (when movies
      (count movies))))

(defn filter-movies
  [{:keys [movie-letter movie-filter-text movies page-number]}]
  (let [letter-movies (if movie-letter
                        (filter #(= (:letter %) movie-letter) movies)
                        movies)
        filtered-movies (if (str/blank? movie-filter-text)
                          (into [] letter-movies)
                          (into [] (filter #(includes-ignore-case? (:title %) movie-filter-text) letter-movies)))
        movie-count (count filtered-movies)
        page-count (max 1 (quot movie-count page-size))
        page-index (if page-number
                     (* (dec page-number) page-size)
                     0)
        end-index (min movie-count (+ page-index page-size))
        page-movies (subvec filtered-movies page-index end-index)]
    {:filtered-movie-count (count filtered-movies)
     :page-count page-count
     :page-number page-number
     :page-movies page-movies}))

(rf/reg-sub
  :page
  (fn [db _]
    (filter-movies db)))

(rf/reg-sub
  :movie-filter-text
  (fn [db _]
    (:movie-filter-text db)))

(rf/reg-sub
  :movie-letter-state
  (fn [db _]
    (select-keys db [:movie-letter
                     :movie-letter-input-type])))

;; -- View Functions -----------------------------------------------------------

(defn button
  [label on-click]
  [:input.btn.btn-default
   {:type "button"
    :value label
    :on-click  on-click}])

(defn movie-item
  [{:keys [status movie-path letter category] :as movie}]
  [:li {:key movie-path} movie-path])

(defn movie-filter-input
  []
  [:div.pb-3
   [:input.form-control
    {:id "movie-filter-input"
     :placeholder "Title"
     :type "text"
     :value @(rf/subscribe [:movie-filter-text])
     :on-change #(rf/dispatch [:movie-filter-change (-> % .-target .-value)])}]])

(defn movie-letter-input
  []
  (let [{:keys [movie-letter
                movie-letter-input-type]} @(rf/subscribe [:movie-letter-state])]
    [:div
;;     [button "Toggle Input Type" #(rf/dispatch [:toggle-movie-letter-input-type])]
     (comment [:ul.pagination
               (for [item alphabet/alphabet-vector]
                 (if (= item movie-letter)
                   (nav/active-page-link item)
                   (nav/page-link item #(rf/dispatch [:to-letter item]))))])
     (let [defaulted-movie-letter (or movie-letter "A")
           [before-previous previous] (alphabet/take-before 2 defaulted-movie-letter)
           [next after-next] (alphabet/take-after 2 defaulted-movie-letter)]
       [:ul.pagination
        (nav/previous-link #(rf/dispatch [:previous-letter]))
        (nav/page-link before-previous #(rf/dispatch [:to-letter before-previous]))
        (nav/page-link previous #(rf/dispatch [:previous-letter]))
        (if movie-letter
          (nav/active-page-link movie-letter)
          (nav/page-link defaulted-movie-letter #(rf/dispatch [:to-letter defaulted-movie-letter])))
        (nav/page-link next #(rf/dispatch [:next-letter]))
        (nav/page-link after-next #(rf/dispatch [:to-letter after-next]))
        (nav/next-link #(rf/dispatch [:next-letter]))])]))

(defn movie-pagination
  []
  (let [{:keys [page-number page-count filtered-movie-count] :as response} @(rf/subscribe [:page])]
    (when-not (zero? filtered-movie-count)
      [:ul.pagination
       (if (= 1 page-number)
         (nav/disabled-previous-link)
         (nav/previous-link #(rf/dispatch [:previous-page])))
       (for [index (range 1 (inc page-count))]
         (if (= index page-number)
           (nav/active-page-link index)
           (nav/page-link index #(rf/dispatch [:to-page index]))))
       (if (= page-number page-count)
         (nav/disabled-next-link)
         (nav/next-link #(rf/dispatch [:next-page])))])))

(defn movies
  []
  (let [movies (:page-movies @(rf/subscribe [:page]))]
    (if (empty? movies)
      [:div.text-center.pt-5.pb-5
       [:h3 "No movies found."]]
      [:div.row.row-cols-1.row-cols-md-3.g-4
       (for [{:keys [moviedb-id
                     imdb-id
                     title
                     uuid
                     overview
                     backdrop-path
                     release-date] :as movie} movies]
         [:div.col
          [:div.card {:key title
                      :style {"marginBottom" "1em"}}
           [:img.card-img-top
            {:src (if backdrop-path
                    (str "http://image.tmdb.org/t/p/w300" backdrop-path)
                    "http://via.placeholder.com/300x169")
             :style {"display" "block"
                     "height" "auth"}
             :alt title}]
           [:div.card-body
            [:h4.card-title
             [:a {:href (str "/movies/" uuid)} title]]]]])])))

(defn bottom
  []
  [:<>
   [:nav
    [movie-letter-input]
    [movie-filter-input]
    [movie-pagination]]
   [movies]
   [:nav
    [movie-pagination]]])

(defn ui
  []
  (let [{:keys [movie-status error-message]} @(rf/subscribe [:movie-state])]
    [:div.container
     {:style {"marginTop" "1em"}}
     [:h1 "Movies"]
     (case movie-status
       :loading [:p "Loading movies..."]
       :error [:p (str "Error: " error-message)]
       [:<>
        [:p "These are my movies."]
        [bottom]])]))

;; -- Entry Point -------------------------------------------------------------

(defn init []
  (rf/dispatch-sync [:initialize])
  (rd/render [ui] (js/document.getElementById "app")))
