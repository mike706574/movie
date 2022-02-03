CREATE OR REPLACE VIEW movie_view AS
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
       mr.average_rating,
       m.imdb_rating,
       m.imdb_votes,
       m.metascore
FROM movie m
  LEFT JOIN movie_average_rating_view mr ON m.movie_id = mr.movie_id
WHERE m.active;
