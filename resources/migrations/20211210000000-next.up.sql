ALTER TABLE account_movie
ADD COLUMN owned BOOLEAN;
--;;
UPDATE account_movie SET owned = true;
--;;
ALTER TABLE account_movie
ALTER COLUMN owned SET NOT NULL;
--;;
DROP VIEW account_movie_view;
--;;
CREATE VIEW account_movie_view AS
SELECT a.account_id,
       coalesce(am.owned, false) AS owned,
       coalesce(am.watched, false) AS watched,
       am.rating,
       m.*
FROM account a
     CROSS JOIN movie_view m
     LEFT JOIN account_movie am ON m.movie_id = am.movie_id AND am.active;
--;;
CREATE OR REPLACE VIEW movie_average_rating_view AS
SELECT movie_id,
       AVG(rating) AS average_rating
FROM account_movie
WHERE rating IS NOT NULL AND active
GROUP BY movie_id;
