-- create table authors (
--     id INTEGER PRIMARY KEY NOT NULL,
--     name VARCHAR(15)
-- );

-- create table books (
--     id INTEGER PRIMARY KEY NOT NULL,
--     title VARCHAR(15) NOT NULL,
--     author_id INTEGER NOT NULL,
--   	FOREIGN KEY(author_id) REFERENCES authors(id)
-- );

INSERT INTO authors (name) VALUES ('Jane Austen');
INSERT INTO books (title, author_id) VALUES ('Pride and Prejudice', 1);