# 사용처 : CreateBookService.execute
# 활성 도서끼리만 ISBN 중복을 금지한다.
CREATE UNIQUE INDEX ux_books_active_isbn
    ON books ((IF(deleted_at IS NULL, isbn, NULL)));
