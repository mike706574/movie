create table movie (
  id serial primary key,
  title varchar(120) not null,
  letter varchar(1) not null,
  directory varchar(120) not null unique,
  release_date varchar(10),
  overview text,
  tmdb_id varchar(60),
  imdb_id varchar(60),
  tmdb_title varchar(120),
  backdrop_path varchar(120)
);

create table watched (
  movie_id serial references movie (id),
  username varchar(60) not null,
  primary key(movie_id, username)
);

create table file (
  movie_id serial references movie (id),
  file_name varchar(60) not null,
  file_type varchar(20) not null,
  primary key(movie_id, file_name)
);
