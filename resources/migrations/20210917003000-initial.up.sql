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
CREATE TABLE movie (
  movie_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL UNIQUE,
  letter VARCHAR(1) NOT NULL,
  path TEXT,
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
  movie_id INT REFERENCES movie (movie_id),
  rating DECIMAL NOT NULL,
  active BOOLEAN DEFAULT TRUE,
  created TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified TIMESTAMPTZ
);
--;;
CREATE TRIGGER movie_rating_modified
BEFORE UPDATE ON movie_rating
FOR EACH ROW EXECUTE PROCEDURE update_modified();
--;;
CREATE VIEW movie_view AS
SELECT m.movie_id AS id,
       m.uuid,
       m.title,
       m.letter,
       m.path,
       m.created,
       m.modified,
       mr.rating
FROM movie m
  LEFT JOIN movie_rating mr ON m.movie_id = mr.movie_id AND mr.active
WHERE m.active;
