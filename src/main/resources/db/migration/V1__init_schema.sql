CREATE TABLE book (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    deleted    BIT          NOT NULL,
    isbn       VARCHAR(255),
    title      VARCHAR(255) NOT NULL,
    author     VARCHAR(255) NOT NULL,
    publisher  VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_book_isbn UNIQUE (isbn)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE book_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    deleted     BIT          NOT NULL,
    book_id     BIGINT       NOT NULL,
    call_number VARCHAR(255) NOT NULL,
    status      VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_book_item_call_number UNIQUE (call_number),
    CONSTRAINT fk_book_item_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE loan (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    deleted      BIT          NOT NULL,
    book_item_id BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    department   VARCHAR(255) NOT NULL,
    email        VARCHAR(255),
    loan_date    DATE         NOT NULL,
    due_date     DATE         NOT NULL,
    return_date  DATE,
    PRIMARY KEY (id),
    CONSTRAINT fk_loan_book_item FOREIGN KEY (book_item_id) REFERENCES book_item (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
