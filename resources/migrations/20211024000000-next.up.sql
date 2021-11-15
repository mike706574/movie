ALTER TABLE movie ALTER COLUMN path DROP NOT NULL;
--;;
DROP VIEW account_movie_view, movie_view, movie_average_rating_view, movie_rating_view;
--;;
ALTER TABLE movie_rating RENAME TO account_movie;
--;;
ALTER TABLE account_movie RENAME COLUMN movie_rating_id TO account_movie_id;
--;;
ALTER TABLE account_movie
ADD COLUMN watched BOOLEAN NOT NULL;
--;;
CREATE VIEW movie_average_rating_view AS
SELECT movie_id,
       AVG(rating) AS average_rating
FROM account_movie
WHERE rating IS NOT NULL
GROUP BY movie_id;
--;;
CREATE VIEW movie_view AS
SELECT m.movie_id,
       m.uuid,
       m.title,
       m.category,
       m.path,
       m.release_date,
       m.overview,
       m.original_language,
       m.runtime,
       m.tmdb_id,
       m.imdb_id,
       m.tmdb_title,
       m.tmdb_popularity,
       m.tmdb_backdrop_path,
       m.tmdb_poster_path,
       m.created,
       m.modified,
       mr.average_rating
FROM movie m
  LEFT JOIN movie_average_rating_view mr ON m.movie_id = mr.movie_id
WHERE m.active;
--;;
CREATE VIEW account_movie_view AS
SELECT a.account_id,
       coalesce(am.watched, false) AS watched,
       am.rating,
       m.*
FROM account a
     CROSS JOIN movie_view m
     LEFT JOIN account_movie am ON m.movie_id = am.movie_id AND am.active;
