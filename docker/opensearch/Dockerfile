FROM opensearchproject/opensearch:2.19.1

# 한글 형태소 분석(nori)을 위한 공식 플러그인. 기본 이미지에는 번들되어 있지 않아 빌드 시 설치한다.
# analysis-icu 는 자모 분해(icu_normalizer, mode: decompose)에 쓴다 — 직접 만든 11,172 음절 표를 대체한다.
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-nori \
 && /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-icu
