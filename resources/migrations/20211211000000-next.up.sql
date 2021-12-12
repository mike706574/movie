--;;
CREATE OR REPLACE VIEW account_movie_view AS
SELECT a.account_id,
       coalesce(am.owned, false) AS owned,
       coalesce(am.watched, false) AS watched,
       am.rating,
       m.*
FROM account a
     CROSS JOIN movie_view m
     LEFT JOIN account_movie am ON m.movie_id = am.movie_id AND a.account_id = am.account_id AND am.active;
