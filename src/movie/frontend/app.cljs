(ns movie.frontend.app
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [day8.re-frame.http-fx]
            [movie.frontend.alphabet :as alphabet]
            [movie.frontend.nav :as nav]
            [reitit.coercion.spec :as rss]
            [reitit.frontend :as rfe]
            [reitit.frontend.easy :as rfez]
            [reitit.frontend.controllers :as rfc]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [re-frame.core :as rf]))

;; -- Development
(enable-console-print!)

;; -- Utilities --
(defn includes-ignore-case?
  [string sub]
  (not (nil? (.match string (re-pattern (str "(?i)" sub))))))

(defn classes [entries]
  (->> entries
       (filter (fn [entry]
                 (or (string? entry)
                     (= (count entry) 1)
                     (second entry))))
       (map (fn [entry]
              (if (string? entry)
                entry
                (first entry))))))

(defn href
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfez/href k params query)))

;; -- Event Handlers --

(rf/reg-event-db
 :initialize
 (fn [db _]
   (merge db
          {:current-route nil
           :status :loading
           :page-number nil
           :movie-letter nil
           :movie-letter-input-type :full
           :movies nil
           :movie nil})))

(rf/reg-event-fx
 :push-state
  (fn [_ [_ & route]]
    {:push-state route}))

(rf/reg-event-db
 :navigated
  (fn [db [_ new-match]]
    (let [old-match (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      (assoc db :current-route (assoc new-match :controllers controllers)))))

;; Fetching movies
(defn movies-request []
  {:method :get
   :uri "/api/movies"
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success [:process-movies]
   :on-failure [:handle-failure]})

(rf/reg-event-fx
 :fetch-movies
 (fn [{db :db} _]
   {:http-xhrio (movies-request)
    :db (assoc db :status :loading)}))

(rf/reg-event-db
 :process-movies
 (fn [db [_ response]]
   (-> db
       (merge {:status :loaded
               :page-number 1
               :movie-letter nil
               :movies (js->clj response)}))))

;; Fetching a movie
(defn movie-request [uuid]
  {:method :get
   :uri (str "/api/movies/" uuid)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success [:process-movie]
   :on-failure [:handle-failure]})

(rf/reg-event-fx
 :fetch-movie
 (fn [{db :db} [_ uuid]]
   {:http-xhrio (movie-request uuid)
    :db (assoc db :status :loading)}))

(rf/reg-event-fx
 :refresh-movie
 (fn [{db :db} [_ uuid]]
   {:http-xhrio (movie-request uuid)}))

(rf/reg-event-db
 :process-movie
 (fn [db [_ response]]
   (assoc db :status :loaded :movie (js->clj response))))

;; Rating a movie
(defn rate-movie-request [uuid rating]
  {:method :post
   :params {:rating rating}
   :uri (str "/api/movies/" uuid)
   :format (ajax/json-request-format {:keywords? true})
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success [:refresh-movie uuid]
   :on-failure [:handle-failure]})

(rf/reg-event-fx
 :rate-movie
 (fn [{db :db} [_ uuid rating]]
   {:http-xhrio (rate-movie-request uuid rating)}))

;; Movie pagination
(rf/reg-event-db
 :next-page
 (fn [db _]
  (update db :page-number inc)))

(rf/reg-event-db
 :previous-page
 (fn [db _]
   (update db :page-number dec)))

(rf/reg-event-db
 :to-page
 (fn [db [_ page-number]]
   (assoc db :page-number page-number)))

;; Movie letters
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

;; Movie filter
(rf/reg-event-db
 :movie-filter-change
 (fn [db [_ text]]
   (assoc db :movie-filter-text text :page-number 1)))

(rf/reg-event-db
 :toggle-movie-letter-input-type
 (fn [db _]
   (update db :movie-letter-input-type
           #(case %
              :full :skinny
              :skinny :full))))

(rf/reg-event-db
 :handle-failure
 (fn [db [_ response]]
   (pprint response)
   (merge db {:status :error
              :error-message (:status-text response)})))

(rf/reg-event-fx
  :navigate
  (fn [_ [_ & route]]
    {:navigate! route}))

;; -- Effects --
(rf/reg-fx :push-state
  (fn [route]
    (apply rfez/push-state route)))

;; -- Subscriptions --

(rf/reg-sub
  :current-route
  (fn [db]
    (:current-route db)))

(rf/reg-sub
  :movies
  (fn [db _]
    (:movies db)))

(rf/reg-sub
  :movie
  (fn [db _]
    (:movie db)))

(rf/reg-sub
  :state
  (fn [db _]
    (select-keys db [:status :error-message])))

(rf/reg-sub
  :movie-count
  (fn [{:keys [movies]} _]
    (when movies
      (count movies))))

(def page-size 12)

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

;; -- Views --

(defn movie-item
  [{:keys [movie-path]}]
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
  (let [{:keys [movie-letter]} @(rf/subscribe [:movie-letter-state])]
    [:div
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
  (let [{:keys [page-number page-count filtered-movie-count]} @(rf/subscribe [:page])]
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

(defn ellipsis
  [length string]
  (if (> (count string) length)
    (str (str/trim (str/join (take length string))) "...")
    string))

(defn movies
  []
  (let [movies (:page-movies @(rf/subscribe [:page]))]
    (if (empty? movies)
      [:div.text-center.pt-5.pb-5
       [:h3 "No movies found."]]
      [:div.row.row-cols-1.row-cols-md-3.g-4
       (for [{:keys [title
                     uuid
                     overview
                     tmdb-backdrop-path
                     release-date]} movies]
         [:div.col {:key uuid}
          [:div.card.mb-3
           [:img.card-img-top
            {:src (if tmdb-backdrop-path
                    (str "http://image.tmdb.org/t/p/w300" tmdb-backdrop-path)
                    "http://via.placeholder.com/300x169")
             :style {"display" "block"
                     "height" "auth"}
             :alt title}]
           [:div.card-body
            [:h5.card-title
             {:style {"display" "inline"}}
             [:a {:href (href :movie {:uuid uuid})} title]]
            [:h6.card-subtitle.text-muted
             {:style {"display" "inline" "marginLeft" "0.25em"}}
             release-date]
            [:p.card-text (ellipsis 150 overview)]]]])])))

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

(defn rating-text [rating] (if rating (str rating) ""))

(defn rating-form [{:keys [uuid rating]}]
  (let [rating-atom (r/atom (rating-text rating))]
    (fn []
      (let [new-rating @rating-atom
            changed? (= (rating-text rating) new-rating)
            disabled? (and (not= new-rating "") (js/Number.isNaN (js/parseFloat new-rating)))]
        [:form.mb-3.w-25
         [:div.input-group
          [:input.form-control {:type "number"
                                :value new-rating
                                :on-change #(reset! rating-atom (-> % .-target .-value))}]
          [:button.btn.btn-primary {:disabled disabled?
                                    :on-click #(rf/dispatch [:rate-movie uuid (js/parseFloat new-rating)])}
           "Rate"]]]))))

(defn movie-page
  []
  (let [{:keys [title overview tmdb-poster-path tmdb-id imdb-id release-date runtime rating uuid id] :as movie} @(rf/subscribe [:movie])]
    [:<>
     [:p "This is a movie."]
     [:h2 title]
     [:div.row
      [:div.col-md-4
       [:img.img-fluid.mb-3
        {:src (str "http://image.tmdb.org/t/p/w780" tmdb-poster-path)}]]
      [:div.col-md-8
       [:h6 "Overview"]
       [:blockquote.blockquote overview]
       [:h6 "Rating"]
       [rating-form movie]
       [:h6 "Info"]
       [:table.table.table-bordered
        [:tbody
         [:tr
          [:th {:scope "row"} "Released"]
          [:td release-date]]
         [:tr
          [:th {:scope "row"} "Runtime"]
          [:td runtime]]
         [:tr
          [:th {:scope "row"} "UUID"]
          [:td uuid]]
         [:tr
          [:th {:scope "row"} "ID"]
          [:td id]]
         [:tr
          [:th {:scope "row"} "Links"]
          [:td
           [:a {:href (str "https://www.themoviedb.org/movie/" tmdb-id)
                :style {"marginRight" "0.5em"}}
            "TMDB"]
           [:a {:href (str "https://www.imdb.org/title/" imdb-id)}
            "IMDB"]]]]]]]]))

(defn home-page []
  [:<>
   [:p "These are my movies."]
   [bottom]])

(defn app []
  (let [{:keys [status error-message]} @(rf/subscribe [:state])
        current-route @(rf/subscribe [:current-route])]
    [:div.container
     {:style {"marginTop" "1em"}}
     [:h1 "Movies"]
     (case status
       :loading [:p "Loading..."]
       :error [:p (str "Error: " error-message)]
       (when current-route
         [(-> current-route :data :view)]))]))

;; -- Routes --

(def routes
  ["/"
   [""
    {:name :home
     :view home-page
     :link-text "Home"
     :controllers
     [{:start (fn []
                (println "Entering home page")
                (rf/dispatch [:fetch-movies]))
       :stop (fn []
               (println "Leaving home page"))}]}]

   ["movies/:uuid"
    {:name :movie
     :view movie-page
     :link-text "Movie"
     :params {:path [:map [:uuid string?]]}
     :controllers
     [{:parameters {:path [:uuid]}
       :start (fn [{{:uuid uuid} :path}]
                (println "Entering movie page for uuid" uuid)
                (rf/dispatch [:fetch-movie uuid]))
       :stop (fn []
               (println "Leaving movie page"))}]}]])


(defn on-navigate [new-match]
  (when new-match
    (rf/dispatch [:navigated new-match])))

(def router
  (rfe/router
    routes
    {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (rfez/start!
    router
    on-navigate
    {:use-fragment true}))

;; -- Entry Point--

(defn init []
  (println "Initializing")
  (rf/dispatch-sync [:initialize])
  (init-routes!)
  (rd/render [app] (js/document.getElementById "app")))
