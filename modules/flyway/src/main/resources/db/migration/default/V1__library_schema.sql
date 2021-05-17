create table authors (
    author_id SERIAL PRIMARY KEY,
    author_name VARCHAR(30) NOT NULL
);

CREATE TABLE books (
    book_id SERIAL PRIMARY KEY,
    book_isbn VARCHAR(50) UNIQUE NOT NULL,
    book_title VARCHAR(30) NOT NULL,
    author_id INTEGER REFERENCES authors(author_id) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

create table books_store (
    books_store_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) NOT NULL,
    quantity INTEGER NOT NULL
);

create table book_expiry (
    book_expiry_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) NOT NULL,
    discontinued BOOLEAN NOT NULL
);

create table users (
    user_id SERIAL PRIMARY KEY NOT NULL,
    user_name VARCHAR(30) NOT NULL
);

create table checkouts (
    checkout_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) NOT NULL,
    taken_by_user_id INTEGER REFERENCES users(user_id) NOT NULL,
    return_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);