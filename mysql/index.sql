# 사용처 : CreateBookService.execute, UpdateBookService.execute
# 활성 도서끼리만 ISBN 중복을 금지한다.
CREATE UNIQUE INDEX ux_books_active_isbn
    ON books ((IF(deleted_at IS NULL, isbn, NULL)));

# 사용처 : CreateBookItemService.execute
# 관리번호는 전체 고유해야 한다. (check-then-act의 동시 요청 레이스 컨디션을 DB 유니크 제약으로 방어)
CREATE UNIQUE INDEX ux_book_items_management_number
    ON book_items (management_number);
