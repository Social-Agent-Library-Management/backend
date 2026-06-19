-- 검색 엔진을 Elasticsearch로 교체하면서 MySQL FULLTEXT 인덱스는 더 이상 사용하지 않는다.
-- 쓰기 부하 절감을 위해 미사용 인덱스를 제거한다.
DROP INDEX idx_book_title_author_ft ON book;
