create table authors (
    author_id SERIAL PRIMARY KEY,
    author_name TEXT NOT NULL
);

CREATE TABLE books (
    book_id SERIAL PRIMARY KEY,
    book_isbn TEXT UNIQUE NOT NULL,
    book_title TEXT NOT NULL,
    author_id INTEGER REFERENCES authors(author_id) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE
    books
ADD
    COLUMN tsv tsvector NOT NULL GENERATED ALWAYS AS (to_tsvector('english', book_title)) STORED;

CREATE INDEX books_tsv_idx ON books USING GIN (tsv);

create table books_store (
    books_store_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) ON DELETE CASCADE NOT NULL,
    quantity INTEGER NOT NULL
);

create table book_expiry (
    book_expiry_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) ON DELETE CASCADE NOT NULL,
    discontinued BOOLEAN NOT NULL
);

create type UserRole as ENUM ('0', '1', '2');

create table users (
    user_id SERIAL PRIMARY KEY NOT NULL,
    user_name TEXT NOT NULL UNIQUE,
    user_password TEXT NOT NULL,
    user_email TEXT NOT NULL,
    user_role UserRole NOT NULL default '2',
    -- TODO: make this an enum
    active_status BOOLEAN NOT NULL DEFAULT false
);

create table account_activation_tokens (
    active_token TEXT NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE
);

create table checkouts (
    checkout_id SERIAL PRIMARY KEY,
    book_id INTEGER REFERENCES books(book_id) NOT NULL,
    taken_by_user_id INTEGER REFERENCES users(user_id) NOT NULL,
    return_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

create type Color as ENUM ('RED', 'GREEN', 'BLUE');

create table extras (
    extras_id SERIAL PRIMARY KEY,
    color Color NOT NULL default 'RED',
    metadata_json jsonb NOT NULL default '{}' :: jsonb,
    content TEXT NOT NULL -- content_tokens TSVECTOR NOT NULL
);

ALTER TABLE
    extras
ADD
    COLUMN tsv tsvector NOT NULL GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX extras_tsv_idx ON extras USING GIN (tsv);