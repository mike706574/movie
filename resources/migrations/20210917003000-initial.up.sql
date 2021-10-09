CREATE OR REPLACE FUNCTION update_modified()
RETURNS TRIGGER AS $$
BEGIN
   IF row(NEW.*) IS DISTINCT FROM row(OLD.*) THEN
      NEW.modified = now();
      RETURN NEW;
   ELSE
      RETURN OLD;
   END IF;
END;
$$ language 'plpgsql';
--;;
CREATE TABLE account (
  account_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified TIMESTAMPTZ
);
--;;
CREATE TABLE movie (
  movie_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL UNIQUE,
  category TEXT NOT NULL,
  path TEXT NOT NULL,
  release_date TEXT,
  overview TEXT,
  original_language TEXT,
  runtime INT,
  tmdb_id INT,
  imdb_id TEXT,
  tmdb_title TEXT,
  tmdb_popularity DECIMAL,
  tmdb_backdrop_path TEXT,
  tmdb_poster_path TEXT,
  active BOOLEAN DEFAULT TRUE,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified TIMESTAMPTZ
);
--;;
CREATE TRIGGER movie_modified
BEFORE UPDATE ON movie
FOR EACH ROW EXECUTE PROCEDURE update_modified();
--;;
CREATE TABLE movie_rating (
  movie_rating_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  movie_id INT NOT NULL REFERENCES movie (movie_id),
  account_id INT NOT NULL REFERENCES account (account_id),
  rating DECIMAL,
  active BOOLEAN DEFAULT TRUE,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified TIMESTAMPTZ
);
--;;
CREATE UNIQUE INDEX movie_rating_uniqueness
ON movie_rating (movie_id, account_id)
WHERE (active);
--;;
CREATE TRIGGER movie_rating_modified
BEFORE UPDATE ON movie_rating
FOR EACH ROW EXECUTE PROCEDURE update_modified();
--;;
CREATE VIEW movie_rating_view AS
SELECT movie_rating_id,
       movie_id,
       account_id,
       rating,
       created,
       modified
FROM movie_rating
WHERE active;
--;;
CREATE VIEW movie_average_rating_view AS
SELECT movie_id,
       AVG(rating) AS average_rating
FROM movie_rating_view
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
       mr.rating,
       m.*
FROM account a
     CROSS JOIN movie_view m
     LEFT JOIN movie_rating_view mr ON m.movie_id = mr.movie_id AND a.account_id = mr.account_id;
