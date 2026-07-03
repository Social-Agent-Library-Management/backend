CREATE FULLTEXT INDEX idx_book_title_author_ft
    ON book (title, author) WITH PARSER ngram;
