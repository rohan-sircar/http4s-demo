create table authors (
  id SERIAL PRIMARY KEY,
  name VARCHAR(15) NOT NULL
);

create table books (
  id SERIAL PRIMARY KEY,
  title VARCHAR(50) NOT NULL,
  author_id INTEGER NOT NULL,
  FOREIGN KEY(author_id) REFERENCES authors(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);