(ns movie.frontend.app
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]
            [day8.re-frame.http-fx]
            [movie.frontend.alphabet :as alphabet]
            [movie.frontend.icons :as icons]
            [movie.frontend.storage :as storage]
            [movie.frontend.nav :as nav]
            [movie.frontend.request :as req]
            [movie.frontend.routing :as routing]
            [movie.frontend.table :as table]
            [movie.frontend.util :as util]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [re-frame.core :as rf]))

;; -- Development
(enable-console-print!)

;; -- Event Handlers --
(rf/reg-event-db
 :initialize
 (fn [db [_ account]]
   (merge db
          {:current-route nil
           :account account
           :loading false
           :error nil
           :page-number nil
           :category nil
           :table false
           :watched nil
           :owned nil
           :movies nil
           :movie nil})))

;; Login
(defn login-request [params]
  (req/post-json-request {:uri "/api/tokens"
                          :params params
                          :on-success [:process-login]
                          :on-failure [:handle-login-failure]}))

(rf/reg-event-fx
 :login
 (fn [_ [_ params]]
   {:http-xhrio (login-request params)}))

(rf/reg-event-fx
 :process-login
 (fn [{db :db} [_ response]]
   (let [account (js->clj response)]
     {:db (assoc db :account account)
      :storage [[:set :account account]]
      :push-route [:home]})))

(rf/reg-event-db
 :handle-login-failure
 (fn [db [_ {:keys [status response] :as result}]]
   (if (= status 401)
     (assoc db :login-error (:error response))
     (assoc db :error result))))

;; Register
(defn register-request [params]
  (req/post-json-request {:uri "/api/accounts"
                          :params params
                          :on-success [:process-register]
                          :on-failure [:handle-register-failure]}))

(rf/reg-event-fx
 :register
 (fn [_ [_ params]]
   {:http-xhrio (register-request params)}))

(rf/reg-event-fx
 :process-register
 (fn [_ _]
   {:push-route [:login]}))

(rf/reg-event-db
 :handle-register-failure
 (fn [db [_ {:keys [status response] :as result}]]
   (if (= status 400)
     (assoc db :register-error (:error response))
     (assoc db :error result))))

;; Logout
(rf/reg-event-fx
 :logout
 (fn [{db :db} _]
   {:db (dissoc db :account)
    :storage [[:remove :account]]}))

;; Fetching movies
(defn movies-request []
  (req/get-json-request {:uri "/api/movies"
                         :on-success [:process-movies]
                         :on-failure [:handle-failure]}))

(rf/reg-event-fx
 :fetch-movies
 (fn [{db :db} _]
   {:db (assoc db :loading true)
    :http-xhrio (movies-request)}))

(rf/reg-event-db
 :process-movies
 (fn [db [_ response]]
   (-> db
       (merge {:loading false
               :page-number 1
               :category nil
               :movies (js->clj response)}))))

;; Fetching a movie
(defn movie-request [uuid]
  (req/get-json-request {:method :get
                         :uri (str "/api/movies/" uuid)
                         :on-success [:process-movie]}))

(rf/reg-event-fx
 :fetch-movie
 (fn [{db :db} [_ uuid]]
   {:http-xhrio (movie-request uuid)
    :db (assoc db :loading true)}))

(rf/reg-event-fx
 :refresh-movie
 (fn [_ [_ uuid]]
   {:http-xhrio (movie-request uuid)}))

(defn update-movie [new-movie movie]
  (if (= (:uuid movie) (:uuid new-movie))
    new-movie
    movie))

(rf/reg-event-db
 :process-movie
 (fn [db [_ response]]
   (let [movie (js->clj response)
         movies (map (partial update-movie movie) (:movies db))]
     (assoc db :loading false :movie movie :movies movies))))

;; Updating a movie
(defn update-movie-request [uuid params]
  (req/post-json-request {:uri (str "/api/movies/" uuid)
                          :params params
                          :on-success [:refresh-movie uuid]}))

(rf/reg-event-fx
 :update-movie
 (fn [_ [_ uuid fields]]
   {:http-xhrio (update-movie-request uuid fields)}))

;; Movie pagination
(rf/reg-event-db
 :to-page
 (fn [db [_ page-number]]
   (assoc db :page-number page-number)))

;; Movie category
(rf/reg-event-db
 :to-category
 (fn [db [_ new-category]]
   (assoc db :category new-category :page-number 1)))

;; Movie category
(rf/reg-event-db
 :set-table
 (fn [db [_ new-table]]
   (assoc db :table new-table)))

;; Watched
(rf/reg-event-db
 :set-watched
 (fn [db [_ new-watched]]
   (assoc db :watched new-watched :page-number 1)))

;; Owned
(rf/reg-event-db
 :set-owned
 (fn [db [_ new-owned]]
   (assoc db :owned new-owned :page-number 1)))

;; Movie filter
(rf/reg-event-db
 :movie-filter-change
 (fn [db [_ text]]
   (assoc db :movie-filter-text text :page-number 1)))

;; Error handling
(rf/reg-event-db
 :handle-failure
 (fn [db [_ response]]
   (assoc db :error response :loading false)))

;; Routing
(rf/reg-event-fx
  :navigate
  (fn [_ [_ & route]]
    {:navigate! route}))

;; -- Subscriptions --

(rf/reg-sub
  :current-route
  (fn [db]
    (:current-route db)))

(rf/reg-sub
  :state
  (fn [db _]
    (select-keys db [:loading :error :account])))

(rf/reg-sub
  :login-error
  (fn [db _]
    (:login-error db)))

(rf/reg-sub
  :register-error
  (fn [db _]
    (:register-error db)))

(rf/reg-sub
  :account
  (fn [db _]
    (:account db)))

(rf/reg-sub
  :movies
  (fn [db _]
    (:movies db)))

(rf/reg-sub
  :movie
  (fn [db _]
    (:movie db)))

(rf/reg-sub
  :movie-count
  (fn [{:keys [movies]} _]
    (when movies
      (count movies))))

(def page-size 12)

(defn filter-movies
  [{:keys [category owned watched movie-filter-text movies]}]
  (let [category-filter (if category
                          #(= (:category %) category)
                          (constantly true))
        title-filter (if (str/blank? movie-filter-text)
                       (constantly true)
                       #(util/includes-ignore-case? (:title %) movie-filter-text))
        watched-filter (if (nil? watched)
                         (constantly true)
                         #(= (:watched %) watched))
        owned-filter (if (nil? owned)
                       (constantly true)
                       #(= (:owned %) owned))
        filtered-movies (->> movies
                             (filter category-filter)
                             (filter title-filter)
                             (filter watched-filter)
                             (filter owned-filter)
                             (into []))]
    {:movies filtered-movies}))


(defn filter-and-paginate-movies
  [{page-number :page-number :as db}]
  (let [{filtered-movies :movies} (filter-movies db)
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
     :movies page-movies}))

(rf/reg-sub
  :filtered-movies
  (fn [db _]
    (filter-movies db)))

(rf/reg-sub
  :paginated-movies
  (fn [db _]
    (filter-and-paginate-movies db)))

(rf/reg-sub
  :movie-filter-text
  (fn [db _]
    (:movie-filter-text db)))

(rf/reg-sub
  :category
  (fn [db _]
    (:category db)))

(rf/reg-sub
  :table
  (fn [db _]
    (:table db)))

(rf/reg-sub
  :watched
  (fn [db _]
    (:watched db)))

(rf/reg-sub
  :owned
  (fn [db _]
    (:owned db)))

;; -- Views --

;; Rating
(defn serialize-rating [rating] (if rating (str rating) ""))

(defn valid-rating? [value]
  (or (= value "") (boolean (re-matches #"^(10|10.0|[0-9](\.[0-9])?)$" value))))

(defn rating-form [{:keys [rating]}]
  (let [rating-atom (r/atom (serialize-rating rating))]
    (fn [{:keys [uuid owned watched rating] :as movie}]
      (let [value @rating-atom
            valid? (valid-rating? value)]
        [:<>
         [:div.col-auto
          [:button.btn.btn-primary
           {:class (util/classes ["btn" (if watched "btn-primary" "btn-secondary")])
            :on-click (if watched
                        #(do (rf/dispatch [:update-movie uuid {:owned owned :watched false :rating nil}])
                             (reset! rating-atom ""))
                        #(rf/dispatch [:update-movie uuid {:owned owned :watched true :rating nil}]))}
           (if watched icons/eye-icon icons/eye-slash-icon)]]
         [:div.col-auto
          [:input {:type "text"
                   :value value
                   :class (util/classes ["form-control" ["is-invalid" (not valid?)]])
                   :step "0.1"
                   :min "0"
                   :max "10"
                   :size "6"
                   :placeholder "Unrated"
                   :disabled (not watched)
                   :on-change #(let [value (-> % .-target .-value)]
                                 (reset! rating-atom value)
                                 (when (valid-rating? value)
                                   (rf/dispatch [:update-movie uuid {:owned owned :watched true :rating (js/parseFloat value)}])))}]]]))))

(defn owned-form []
  (fn [{:keys [uuid owned watched rating] :as movie}]
    (if owned
      [:div.col-auto
       [:button.btn.btn-primary
        {:on-click #(rf/dispatch [:update-movie uuid {:owned false :watched watched :rating rating}])}
        icons/bag-check-icon]]
      [:div.col-auto
       [:button.btn.btn-secondary
        {:on-click #(rf/dispatch [:update-movie uuid {:owned true :watched watched :rating rating}])}
        icons/bag-icon]])))

(defn movie-item [{:keys [movie-path]}]
  [:li {:key movie-path} movie-path])

(defn movie-filter-input []
  [:div.mb-3
   [:input.form-control
    {:id "movie-filter-input"
     :placeholder "Title"
     :type "text"
     :auto-focus true
     :value @(rf/subscribe [:movie-filter-text])
     :on-change #(rf/dispatch [:movie-filter-change (-> % .-target .-value)])}]])

(defn value-select [{:keys [label on-change value target-value]}]
  (let [active? (= target-value value)]
    [:a.page-link
     {:aria-label label
      :style (merge {"cursor" "pointer"
                     "color" "#0275d8"
                     "marginBottom" "1rem"}
                    (when active?
                      {"zIndex" "3"
                       "color" "#fff"
                       "backgroundColor" "#0d6efd"
                       "borderColor" "#0d6efd"}))
      :class (if active? "active" "disabled")
      :on-click #(on-change (if active? nil target-value))}
     [:span {:aria-hidden "true"} label]]))

(defn watched-select []
  (value-select {:label "Watched"
                 :value @(rf/subscribe [:watched])
                 :target-value true
                 :on-change #(rf/dispatch [:set-watched %])}))

(defn unwatched-select []
  (value-select {:label "Unwatched"
                 :value @(rf/subscribe [:watched])
                 :target-value false
                 :on-change #(rf/dispatch [:set-watched %])}))

(defn owned-select []
  (value-select {:label "Owned"
                 :value @(rf/subscribe [:owned])
                 :target-value true
                 :on-change #(rf/dispatch [:set-owned %])}))

(defn unowned-select []
  (value-select {:label "Unowned"
                 :value @(rf/subscribe [:owned])
                 :target-value false
                 :on-change #(rf/dispatch [:set-owned %])}))

(defn movie-category-cols []
  (let [account @(rf/subscribe [:account])
        category @(rf/subscribe [:category])
        kids? (= category "kids")
        letter (when (alphabet/alphabet-map category) category)]
    [:<>
     [:div.col-auto
      (let [defaulted-letter (or letter "a")
            [before-previous previous] (alphabet/take-before 2 defaulted-letter)
            [next after-next] (alphabet/take-after 2 defaulted-letter)]
        [:ul.pagination
         [nav/previous-link {:on-click #(rf/dispatch [:to-category previous])}]
         [nav/link {:label (str/capitalize before-previous)
                    :on-click #(rf/dispatch [:to-category before-previous])}]
         [nav/link {:label (str/capitalize previous)
                    :on-click #(rf/dispatch [:to-category previous])}]
         [nav/link {:active letter
                    :label (str/capitalize defaulted-letter)
                    :on-click #(rf/dispatch [:to-category defaulted-letter])}]
         [nav/link {:label (str/capitalize next)
                    :on-click #(rf/dispatch [:to-category next])}]
         [nav/link {:label (str/capitalize after-next)
                    :on-click #(rf/dispatch [:to-category after-next])}]
         [nav/next-link {:on-click #(rf/dispatch [:to-category next])}]])]
     [:div.col-auto
      [value-select {:label "Kids"
                     :value category
                     :target-value "kids"
                     :on-change #(rf/dispatch [:to-category %])}]]
     (when account
       [:<>
        [:div.col-auto [owned-select]]
        [:div.col-auto [unowned-select]]
        [:div.col-auto [watched-select]]
        [:div.col-auto [unwatched-select]]])]))

(defn movie-table-select []
  [:div.col-auto
   (value-select {:label "Table"
                  :value @(rf/subscribe [:table])
                  :target-value true
                  :on-change #(rf/dispatch [:set-table %])})])

(defn page-range [page-number page-count]
  (let [lo (max 1 (dec page-number))
        hi (min page-count (inc page-number))
        left (if (= 1 lo) [] [nil])
        right (if (= page-count hi) [] [nil])]
    (concat left (range lo (inc hi)) right)))

(defn page-number-elements [page-count page-number]
  (loop [elements [page-number]
         distance 1]
    (if (= 5 (count elements))
      elements
      (let [left (- page-number distance)
            with-left (if (< left 1)
                        elements
                        (vec (cons left elements)))
            right (+ page-number distance)
            with-right (if (> right page-count)
                         with-left
                         (conj with-left right))]
        (if (= (count elements) (count with-right))
          elements
          (recur with-right (inc distance)))))))

(defn page-elements [page-count page-number]
  (let [elements (page-number-elements page-count page-number)
        elements (if (> (first elements) 1)
                   (assoc elements 0 nil)
                   elements)
        last-idx (dec (count elements))
        elements (if (< (nth elements last-idx) page-count)
                   (assoc elements last-idx nil)
                   elements)]
    elements))

(defn movie-pagination []
  (let [{:keys [page-number page-count filtered-movie-count]} @(rf/subscribe [:paginated-movies])]
    (when-not (zero? filtered-movie-count)
      [:ul.pagination
       [nav/link {:label "«"
                  :key "first"
                  :on-click #(rf/dispatch [:to-page 1])
                  :disabled (= 1 page-number)}]
       [nav/previous-link {:on-click #(rf/dispatch [:to-page (dec page-number)])
                           :disabled (= 1 page-number)}]
       (for [[key page] (map-indexed vector (page-elements page-count page-number))]
         [nav/link {:key key
                    :label (if page page "...")
                    :active (= page page-number)
                    :disabled (not page)
                    :on-click #(rf/dispatch [:to-page page])}])
       [nav/next-link {:on-click #(rf/dispatch [:to-page (inc page-number)])
                       :disabled (= page-number page-count)}]
       [nav/last-link {:on-click #(rf/dispatch [:to-page page-count])
                       :disabled (= page-count page-number)}]])))

(defn ellipsis [length string]
  (if (> (count string) length)
    (str (str/trim (str/join (take length string))) "...")
    string))

(def front-movie-cols
  [{:key "number"
    :label "#"
    :render #(inc (:idx %))}
   {:key "title"
    :label "Title"
    :render (fn [{{:keys [title uuid]} :row}]
              [:a {:href (routing/href :movie {:uuid uuid})} title])}
   {:key "release-date"
    :label "Released"
    :path :release-date
    :format #(or % "Unknown")}
   {:key "runtime"
    :label "Runtime"
    :path :runtime
    :format #(or % "Unknown")}])

(def back-movie-cols
  [{:key "average-rating"
    :label "Average Rating"
    :path :average-rating
    :format #(or % "Not rated")}
   {:key "imdb-rating"
    :label "IMDB Rating"
    :path :imdb-rating}
   {:key "imdb-votes"
    :label "IMDB Votes"
    :path :imdb-votes}
   {:key "metascore"
    :label "Metascore"
    :path :imdb-votes}
   {:key "category"
    :label "Category"
    :path :category}])

(def movie-cols
  (concat front-movie-cols back-movie-cols))

(def account-movie-cols
  (concat
   front-movie-cols
   [{:key "owned"
     :label "Owned"
     :path :owned
     :format #(if % "Yes" "No")}
    {:key "watched"
     :label "Watched"
     :path :watched
     :format #(if % "Yes" "No")}
    {:key "rating"
     :label "Rating"
     :path :rating
     :format #(or % "Not rated")}]
   back-movie-cols))

(defn movie-table [{:keys [movies account]}]
  (let [movies (:movies @(rf/subscribe [:filtered-movies]))
        account @(rf/subscribe [:account])
        cols (if account
               account-movie-cols
               movie-cols)]
    [table/responsive-table {:cols cols
                             :style {"marginBottom" "1rem"
                                     "textAlign" "center"}
                             :rows movies
                             :row-key :uuid}]))

(defn movie-cards [{:keys [movies account]}]
  [:div.row.row-cols-1.row-cols-md-3.g-4.mb-3
   (for [{:keys [title
                 uuid
                 overview
                 tmdb-backdrop-path
                 release-date
                 watched] :as movie} movies]
     [:div.col {:key uuid}
      [:div.card
       [:a {:href (routing/href :movie {:uuid uuid})
            :style {"textDecoration" "none"
                    "color" "#000"}}
        (if tmdb-backdrop-path
          [:img.card-img-top
           {:src (str "http://image.tmdb.org/t/p/w300" tmdb-backdrop-path)
            :style {"display" "block"
                    "height" "auto"}
            :alt title}]
          [:div {:style {"backgroundColor" "#eee"
                         "display" "flex"
                         "height" "169px"
                         "width" "100%"}}
           [:h5 {:style {"alignSelf" "center"
                         "textAlign" "center"
                         "width" "100%"}}
            title]])]
       [:div.card-body
        [:h5.card-title
         {:style {"display" "inline"}}
         [:a {:href (routing/href :movie {:uuid uuid})} title]]
        (when release-date
          [:h6.card-subtitle.text-muted
           {:style {"display" "inline" "marginLeft" "0.25em"}}
           release-date])
        [:p.card-text (if overview
                        (ellipsis 150 overview)
                        "No overview available.")]
        (when account
          [:div.row.g-1
           [owned-form movie]
           [rating-form movie]])]]])])

(defn movie-page []
  (let [{:keys [average-rating category title overview tmdb-poster-path tmdb-id imdb-id release-date runtime uuid owned watched rating imdb-rating imdb-votes metascore] :as movie} @(rf/subscribe [:movie])
        account @(rf/subscribe [:account])]
    (when movie
      [:<>
       [:p "This is a movie."]
       [:h2 title]
       [:div.row
        [:div.col-md-4
         (if tmdb-poster-path
           [:img.img-fluid.mb-3
            {:src (str "http://image.tmdb.org/t/p/w780" tmdb-poster-path)}]
           [:div {:style {"backgroundColor" "#eee"
                          "display" "flex"
                          "height" "460px"
                          "width" "100%"}}
            [:h5 {:style {"alignSelf" "center"
                          "textAlign" "center"
                          "width" "100%"}}
             title]])]
        [:div.col-md-8
         [:section
          [:h6 "Overview"]
          [:blockquote.blockquote (or overview "No overview available.")]]
         [:section.mb-3
          [:h6 "Status"]
          (when account
            [:div.row.g-2
             [owned-form movie]
             [rating-form movie]])]
         [:section.mb-3
          [:h6 "Info"]
          [:table.table.table-bordered
           [:tbody
            [:tr
             [:th {:scope "row"} "Released"]
             [:td (or release-date "Unknown")]]
            [:tr
             [:th {:scope "row"} "Runtime"]
             [:td (or runtime "Unknown")]]
            [:tr
             [:th {:scope "row"} "Owned"]
             [:td (if owned "Yes" "No")]]
            [:tr
             [:th {:scope "row"} "Watched"]
             [:td (if watched "Yes" "No")]]
            [:tr
             [:th {:scope "row"} "Rating"]
             [:td (or rating "Not rated")]]
            [:tr
             [:th {:scope "row"} "Average Rating"]
             [:td (or average-rating "Not rated")]]
            [:tr
             [:th {:scope "row"} "UUID"]
             [:td uuid]]
            [:tr
             [:th {:scope "row"} "IMDB Rating"]
             [:td imdb-rating]]
            [:tr
             [:th {:scope "row"} "IMDB Votes"]
             [:td imdb-votes]]
            [:tr
             [:th {:scope "row"} "Metascore"]
             [:td metascore]]
            [:tr
             [:th {:scope "row"} "Category"]
             [:td (str/capitalize category)]]
            [:tr
             [:th {:scope "row"} "Links"]
             [:td
              (when tmdb-id
                [:a {:href (str "https://www.themoviedb.org/movie/" tmdb-id)
                     :style {"marginRight" "0.5em"}}
                 "TMDB"])
              (when imdb-id
                [:a {:href (str "https://www.imdb.org/title/" imdb-id)}
                 "IMDB"])]]]]]]]])))

(defn register-page []
  (let [email-atom (r/atom "")
        password-atom (r/atom "")]
    (fn []
      (let [error @(rf/subscribe [:register-error])
            email @email-atom
            password @password-atom
            params {:email email :password password}
            email-taken? (= error "email-taken")
            disabled? (or (str/blank? email) (str/blank? password))]
        [:<>
         [:p "You're trying to register."]
         [:h2 "Register"]
         [:div.row.mb-3
          [:label.col-sm-2.col-form-label
           {:for "email"}
           "Email"]
          [:div.col-sm-4
           [:input.form-control
            {:id "email"
             :type "email"
             :name "email"
             :value email
             :auto-focus true
             :class (util/classes [["is-invalid" email-taken?]])
             :on-change #(reset! email-atom (-> % .-target .-value))}]
           (when email-taken? [:div.invalid-feedback "An account with this email already exists."])]]
         [:div.row.mb-3
          [:label.col-sm-2.col-form-label
           {:for "password"}
           "Password"]
          [:div.col-sm-4
           [:input.form-control
            {:id "password"
             :type "text"
             :name "password"
             :value password
             :style {"WebkitTextSecurity" "disc"}
             :on-change #(reset! password-atom (-> % .-target .-value))}]]]
         [:button.btn.btn-primary
          {:type "submit"
           :disabled disabled?
           :on-click #(rf/dispatch [:register params])}
          "Submit"]]))))

(defn login-page []
  (let [email-atom (r/atom "")
        password-atom (r/atom "")]
    (fn []
      (let [error @(rf/subscribe [:login-error])
            email @email-atom
            password @password-atom
            params {:email email :password password}
            missing-account? (= error "missing-account")
            invalid-password? (= error "invalid-password")
            disabled? (or (str/blank? email) (str/blank? password))]
        [:<>
         [:p "You're trying to log in."]
         [:h2 "Login"]
         [:div.row.mb-3
          [:label.col-sm-2.col-form-label
           {:for "email"}
           "Email"]
          [:div.col-sm-4
           [:input.form-control
            {:id "email"
             :type "email"
             :name "email"
             :value email
             :auto-focus true
             :class (util/classes [["is-invalid" missing-account?]])
             :on-change #(reset! email-atom (-> % .-target .-value))}]
           (when missing-account? [:div.invalid-feedback "Account not found."])]]
         [:div.row.mb-3
          [:label.col-sm-2.col-form-label
           {:for "password"}
           "Password"]
          [:div.col-sm-4
           [:input.form-control
            {:id "password"
             :type "password"
             :name "password"
             :value password
             :class (util/classes [["is-invalid" invalid-password?]])
             :on-change #(reset! password-atom (-> % .-target .-value))}]
           (when invalid-password? [:div.invalid-feedback "Invalid password."])]]
         [:button.btn.btn-primary
          {:type "submit"
           :disabled disabled?
           :on-click #(rf/dispatch [:login params])}
          "Submit"]]))))

(defn app []
  (let [{:keys [loading error account]} @(rf/subscribe [:state])
        current-route @(rf/subscribe [:current-route])]
    [:div.container
     {:style {"marginTop" "1em"}}
     [:div.row
      [:div.col.auto
       [:a {:href "/"}
        [:h1 "Movies"]]]
      [:div.col-md-4
       (if account
         [:div.float-end
          (:email account)
          [:button.btn.btn-secondary.ms-2
           {:type "button"
            :style {"display" "inline"}
            :on-click #(rf/dispatch [:logout])}
           "Logout"]]
         [:div.float-end
          [:a {:href (routing/href :login)} "Login"]
          [:a.ms-2 {:href (routing/href :register)} "Register"]])]]
     (cond
       loading [:p "Loading..."]
       error [:<>
              [:p "An error occurred."]
              (if (string? error)
                [:p error]
                [:pre (with-out-str (pprint/pprint error))])]
       :else (when current-route
               [(-> current-route :data :view)]))]))

(defn movies []
  (let [movies (:movies @(rf/subscribe [:paginated-movies]))
        account @(rf/subscribe [:account])
        table @(rf/subscribe [:table])]
    (if (empty? movies)
      [:div.text-center.mt-5.mb-5
       [:h3 "No movies found."]]
      (if table
        [movie-table {:movies movies :account account}]
        [movie-cards {:movies movies :account account}]))))

(defn home-page []
  [:<>
   [:p "These are my movies."]
   [:nav
    [:div.row
     [movie-category-cols]
     [movie-table-select]]
    [movie-filter-input]
    [movie-pagination]]
   [movies]
   [:nav
    [movie-pagination]]])

(defn search-page []
  [:<>
   [:p "This is where you search."]])

;; -- Routes --

(def routes
  ["/"
   [""
    {:name :home
     :view home-page
     :controllers
     [{:start (fn []
                (println "Entering home page")
                (rf/dispatch [:fetch-movies]))
       :stop (fn []
               (println "Leaving home page"))}]}]

   ["search"
    {:name :search
     :view search-page
     :controllers
     [{:start (fn [] (println "Entering search page"))
       :stop (fn [] (println "Leaving search page"))}]}]

   ["register"
    {:name :register
     :view register-page
     :controllers
     [{:start (fn [] (println "Entering register page"))
       :stop (fn [] (println "Leaving register page"))}]}]

   ["login"
    {:name :login
     :view login-page
     :controllers
     [{:start (fn [] (println "Entering login page"))
       :stop (fn [] (println "Leaving login page"))}]}]

   ["movies/:uuid"
    {:name :movie
     :view movie-page
     :params {:path [:map [:uuid string?]]}
     :controllers
     [{:parameters {:path [:uuid]}
       :start (fn [{{uuid :uuid} :path}]
                (println "Entering movie page for uuid" uuid)
                (rf/dispatch [:fetch-movie uuid]))
       :stop (fn []
               (println "Leaving movie page"))}]}]])

;; -- Entry Point--

(defn init []
  (println "Initializing")
  (let [account (storage/get-account)]
    (rf/dispatch-sync [:initialize account])
    (routing/init-routes! routes)
    (rd/render [app] (js/document.getElementById "app"))))
