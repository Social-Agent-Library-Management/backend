# 사용처 : CreateBookService.execute, UpdateBookService.execute
# 활성 도서끼리만 ISBN 중복을 금지한다.
CREATE UNIQUE INDEX ux_book_active_isbn
    ON book ((IF(deleted_at IS NULL, isbn, NULL)));

# 사용처 : CreateBookItemService.execute
# 관리번호는 전체 고유해야 한다. (check-then-act의 동시 요청 레이스 컨디션을 DB 유니크 제약으로 방어)
CREATE UNIQUE INDEX ux_book_item_management_number
    ON book_item (management_number);

# 사용처 : BookRepository.searchFullText (search.engine=mysql_fulltext)
# 한글은 공백 기준 형태소 분리가 무의미해 ngram 파서(기본 ngram_token_size=2) 기반 역색인을 쓴다.
ALTER TABLE book ADD FULLTEXT INDEX idx_book_title_author_ft (title, author) WITH PARSER ngram;
