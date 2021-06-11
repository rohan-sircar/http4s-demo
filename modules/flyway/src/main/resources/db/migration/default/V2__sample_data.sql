insert into
    authors (author_name)
values
    ('Author1');

insert into
    authors (author_name)
values
    ('Author2');

insert into
    authors (author_name)
values
    ('Author3');

insert into
    books (book_isbn, book_title, author_id)
values
    ('aebwegbwe', 'book1', 3);

insert into
    books (book_isbn, book_title, author_id)
values
    ('abeqegbqeg', 'book2', 2);

insert into
    books (book_isbn, book_title, author_id)
values
    ('aebhqeqegq', 'book3', 1);

insert into
    books_store (book_id, quantity)
values
    (1, 5);

insert into
    books_store (book_id, quantity)
values
    (2, 3);

insert into
    books_store (book_id, quantity)
values
    (3, 8);

insert into
    book_expiry (book_id, discontinued)
values
    (1, false);

insert into
    book_expiry (book_id, discontinued)
values
    (2, false);

insert into
    book_expiry (book_id, discontinued)
values
    (3, false);

insert into
    users (user_name, user_password, user_role)
values
    (
        'user1',
        '$2a$10$V2qon2elG0P6/u5J.5xyDOx/.S94Cnt0iod64qAUhbGo6C8UCQOcm',
        '0'
    );

insert into
    users (user_name, user_password, user_role)
values
    (
        'user2',
        '$2a$10$sjofv/dUmC9Fqe7oS4vxpOlwjU634X/1UaNyBltzFffUhkj3xde8a',
        '1'
    );

insert into
    extras (
        color,
        metadata_json,
        content
    )
values
    (
        'BLUE',
        '{"foo":"bar"}',
        'quick fox jumped over fence'
    );