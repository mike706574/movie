# movie

A personal movie app in Clojure/Script.

Hosted at https://movie-mike.herokuapp.com - I'm using the free tier, so it might take a bit to load the first time.

## Development

Start the frontend:

```
npx shadow-cljs watch frontend
```

Start the backend without a REPL:

```
lein run
```

Start the backend with a REPL:

```
lein repl
(load-file "dev/user.clj")
(reset)
```

## TODO

Connect to local database:

```
PGPASSWORD=postgres psql -h localhost -p 7601 -d postgres -U postgres
```

Connect to production database:

```
PGPASSWORD=$MOVIE_DB_PASSWORD psql -h $MOVIE_DB_HOST -d $MOVIE_DB_NAME -U $MOVIE_DB_USER
```

Dump production database:

```
pg_dump -h $MOVIE_DB_HOST -U $MOVIE_DB_USER -W -F t --no-owner --no-acl $MOVIE_DB_NAME > movie.tar
```

Restore production database over local database:

```
pg_restore -h localhost -d postgres -U postgres -p 7601 -c -W movie.tar
```

Sync movies:

```
java -jar target/movie.jar -d /mnt/d/kids/movies/ -k category-dir -c kids -p $ADMIN_PASSWORD sync-movies
java -jar target/movie.jar -d /mnt/d/adults/movies/ -k root-dir -p $ADMIN_PASSWORD sync-movies
```
