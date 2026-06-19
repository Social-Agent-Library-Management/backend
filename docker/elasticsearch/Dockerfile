# Elasticsearch + Nori(한글 형태소 분석) 플러그인 포함 커스텀 이미지
# 버전은 build.gradle이 관리하는 elasticsearch-java(8.18.8)와 맞춘다.
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.8

RUN bin/elasticsearch-plugin install --batch analysis-nori
