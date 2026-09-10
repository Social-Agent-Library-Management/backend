!스크린샷 2026-09-10 오후 5.02.54.png

## **1. 들어가며**

지난 글에서 도서 검색을 LIKE → MySQL FULLTEXT(ngram) → OpenSearch(nori) 순으로 비교했습니다. 마지막에 남긴 숙제가 하나 있었습니다. `multi_match` + `fuzziness: AUTO`로는 한글 오타를 교정할 수 없다는 것이었습니다.

원인은 두 가지가 겹쳐 있었습니다.

1. nori는 사전 기반 형태소 분석기라, “스프릥” 같은 미등록 단어를 “스프”+“릥”으로 쪼갭니다.
2. `fuzziness: AUTO`는 **토큰 길이**로 허용 편집거리를 정하는데(1~2글자는 0, 3~5글자는 1, 6글자 이상은 2), 쪼개진 토큰이 1~2글자라 애초에 fuzzy 확장이 일어나지 않습니다.

억지로 `fuzziness: 2`를 걸어봤더니 이번엔 10만 건 중 8만 건이 걸리는 더 나쁜 실패로 바뀌었습니다. 결론은 “fuzziness는 이미 비교 대상으로 잡힌 토큰끼리 미세 조정하는 파라미터일 뿐, 토큰화 단계에서 이미 어긋나버린 비교 대상 자체를 바로잡아주진 못한다” 였습니다.

이번 글은 그 “별도 suggester”를 실제로 붙여보는 과정을 통해 OpenSearch의 **Suggester API**로 “스프릥”을 “스프링”으로 되돌릴 수 있는지, 그리고 그 과정에서 지난 글과 똑같은 함정(사전 기반 토큰화가 후보 자체를 망가뜨리는 문제)이 한 겹 더 나타나는지를 확인합니다.

## **2. Suggester API란**

OpenSearch/Elasticsearch에는 검색 쿼리(`query`)와 별개로, 같은 `_search` 요청에 `suggest` 블록을 얹어 “이 검색어 대신 이런 단어/문구는 어떠세요?”를 물어보는 기능이 있습니다. 크게 세 종류가 있습니다.

- **Term Suggester**: 입력을 단어 단위로 쪼갠 뒤, 각 단어를 인덱스 사전(vocabulary)에 있는 단어들과 편집거리로 비교해 후보를 냅니다. 단어 하나하나를 독립적으로 고치기 때문에, 여러 단어가 섞인 문장에서 “고친 단어들을 이어붙였더니 말이 안 되는” 조합이 나올 수 있습니다.
- **Phrase Suggester**: Term Suggester 위에, 후보 단어들을 실제로 이어붙였을 때 그 순서가 말이 되는지까지 채점합니다(뒤에서 볼 `title.trigram` 같은 shingle 필드가 이 채점에 쓰입니다). 이번 글에서 쓸 방식입니다.
- **Completion Suggester**: 자동완성용입니다. FST(유한상태전이기) 기반으로 접두어 매칭에 특화되어 있고, 오타 교정보다는 “다 치기 전에 미리 보여주기”에 가깝습니다. 이번 글의 범위 밖입니다.

오타 교정에는 Phrase Suggester가 적합해 보였습니다. 지난 글의 “미니멀리즘 실천법” 케이스처럼 여러 단어로 이뤄진 제목에서, 단어 하나하나를 따로 고치는 것보다 전체 문구가 자연스러운지까지 보는 편이 나을 거라 판단했습니다.

쿼리 모양은 이렇습니다.

```json
{
  "suggest": {
    "title_suggest": {
      "text": "스프릥",
      "phrase": {
        "field": "title.trigram",
        "direct_generator": [
          { "field": "title.suggest", "suggest_mode": "always" }
        ]
      }
    }
  }
}
```

`field`(채점용)와 `direct_generator.field`(후보 생성용)가 분리되어 있다는 게 포인트입니다. 이 분리가 왜 필요한지는 다음 장에서 실제로 실패시켜보면서 확인하겠습니다.

## **3. nori 필드에 그대로 걸어봤더니**

가장 먼저 한 일은, 새 필드를 만들기 전에 **기존 `title`(nori 분석) 필드에 Phrase Suggester를 그냥 걸어보는 것**이었습니다. 지난 글에서 fuzziness가 nori 때문에 망가졌으니, suggester도 같은 필드를 쓰면 같은 문제를 물려받을 거라 예상했습니다.

```bash
curl -s -X POST "localhost:9200/books/_search" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "suggest": {
    "nori_suggest": {
      "text": "스프릥",
      "phrase": {
        "field": "title",
        "direct_generator": [
          { "field": "title", "suggest_mode": "always", "min_word_length": 1 }
        ]
      }
    }
  }
}'
```

```json
{"suggest": {"nori_suggest": [{"text": "스프릥", "options": [
  {"text": "스프링 릥", "score": ...}
]}]}}
```

예상대로 깨졌습니다. **“스프릥”이 “스프링 릥”으로 고쳐졌습니다.** nori가 “스프릥”을 “스프”+“릥” 두 토큰으로 쪼갠 뒤, “스프”만 인덱스에 있는 “스프링”으로 교정하고 “릥”은 손대지 못한 채 그대로 남긴 겁니다. 지난 글 3.2절에서 본 것과 같은 메커니즘이 한 겹 위(검색 결과 매칭이 아니라 오타 교정 후보 생성)에서 재현된 셈입니다.

다른 케이스도 확인했습니다.

```bash
curl -s -X POST "localhost:9200/books/_search" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "suggest": { "nori_suggest": { "text": "미니멀리즘 실천법",
    "phrase": { "field": "title",
      "direct_generator": [{ "field": "title", "suggest_mode": "always", "min_word_length": 1 }] } } }
}'
```

```
"미니멀리즘 실천법" -> "미니멀리즘 실전 법"
```

이번엔 오타가 아예 없는 정상 문구인데도 **엉뚱하게 고쳐졌습니다.** nori가 “실천법”을 “실천”+“법”으로 쪼개 놓았고, “실천”이 우연히 “실전”과 편집거리 1이라 그쪽으로 넘어가 버린 겁니다.

두 사례의 공통점은 이렇습니다. **suggester 자체는 잘못이 없습니다. 후보를 생성하는 필드가 이미 형태소 단위로 조각나 있으면, 편집거리 비교도 그 조각 단위로 일어난다는 게 문제입니다.** “스프릥”과 “스프링”을 통째로 비교했다면 편집거리 1로 바로 찾아졌을 텐데, “스프”와 “릥”이라는 조각을 각각 비교하다 보니 “릥”이라는 고아가 남았습니다.

결론: **오타 교정용 후보를 생성하는 필드는, nori를 거치지 않아야 합니다.**

## **4. 전용 suggest 필드 설계**

### **4.1 `standard` 토크나이저가 답이었습니다**

nori를 안 쓰면서도 한글을 다룰 수 있는 가장 간단한 방법을 먼저 확인했습니다. `standard` 토크나이저입니다.

```bash
curl -s -X POST "localhost:9200/_analyze" -H 'Content-Type: application/json' \
  -d '{"tokenizer":"standard","filter":["lowercase"],"text":"스프릥"}'
# {"tokens":[{"token":"스프릥", ...}]}   <- 1토큰, 쪼개지지 않음

curl -s -X POST "localhost:9200/_analyze" -H 'Content-Type: application/json' \
  -d '{"tokenizer":"standard","filter":["lowercase"],"text":"클린 코드 입문"}'
# ['클린', '코드', '입문']   <- 공백 기준으로만 자름
```

`standard` 토크나이저는 유니코드 표준(UAX#29)에 따라 단어 경계를 판단합니다. 한글 음절(가~힣)은 이 표준에서 알파벳과 같은 부류(`Word_Break=ALetter`)로 취급되기 때문에, 공백이나 문장부호가 나오기 전까지는 통째로 하나의 토큰으로 유지됩니다. nori처럼 “이 글자 뭉치가 사전에 있는 단어인지” 따지지 않고, 그냥 공백으로만 자릅니다.

> 이 트릭은 한글이라 가능합니다. 중국어·일본어 텍스트는 UAX#29에서 음절 단위로 쪼개지는 규칙이 달라서 같은 방식이 통하지 않습니다.
>

즉 “스프릥”을 `standard`로 분석하면 nori처럼 쪼개지지 않고 그대로 남고, 인덱스에 있는 “스프링”과 whole-word 편집거리를 직접 비교할 수 있게 됩니다. 편집거리 1, 바로 찾아집니다.

### **4.2 매핑에 서브필드 추가**

기존 `title`(nori 분석)은 그대로 두고, 서브필드 2개를 추가했습니다. 기존 검색 경로에는 전혀 영향이 없는 additive 변경입니다.

```json
// src/main/resources/opensearch/books_index.json

{
  "settings": {
    "analysis": {
      "tokenizer": {
        "nori_mixed": { "type": "nori_tokenizer", "decompound_mode": "mixed" }
      },
      "filter": {
        "title_shingle": { "type": "shingle", "min_shingle_size": 2, "max_shingle_size": 3 }
      },
      "analyzer": {
        "korean_nori": { "type": "custom", "tokenizer": "nori_mixed", "filter": ["nori_readingform", "lowercase"] },
        "plain_word": { "type": "custom", "tokenizer": "standard", "filter": ["lowercase"] },
        "plain_trigram": { "type": "custom", "tokenizer": "standard", "filter": ["lowercase", "title_shingle"] }
      }
    }
  },
  "mappings": {
    "properties": {
      "bookId": { "type": "long" },
      "title": {
        "type": "text",
        "analyzer": "korean_nori",
        "fields": {
          "suggest": { "type": "text", "analyzer": "plain_word" },
          "trigram": { "type": "text", "analyzer": "plain_trigram" }
        }
      },
      "author": { "type": "text", "analyzer": "korean_nori" },
      "publisher": { "type": "keyword" },
      "isbn": { "type": "keyword" },
      "createdAt": { "type": "date" }
    }
  }
}
```

- `title.suggest`(`plain_word`): 편집거리 비교용 후보 사전. `standard` + `lowercase`만 태워 단어를 통째로 보존합니다.
- `title.trigram`(`plain_trigram`, shingle 2~3): 후보 단어들을 이어붙인 문구가 자연스러운지 채점하는 필드입니다.

```bash
curl -s -X POST "localhost:9200/books/_analyze" -H 'Content-Type: application/json' \
  -d '{"field":"title.trigram","text":"클린 코드 입문"}'
# ['클린', '클린 코드', '클린 코드 입문', '코드', '코드 입문', '입문']
```

`author`는 의도적으로 제외했습니다. 벤치마크 케이스가 전부 title 키워드이고, 합성 한글 성명 데이터를 후보 사전에 섞으면 노이즈만 늘어나기 때문입니다.

`author`는 매핑 변경 없이 그대로 두었지만, **매핑에 서브필드를 추가한 이상 전체 재색인은 피할 수 없습니다.** OpenSearch는 매핑이 바뀌어도 기존 문서를 소급 재분석하지 않기 때문입니다. `OpenSearchIndexGenerator.main()`으로 인덱스를 통째로 지우고 MySQL에서 10만 건을 다시 밀어 넣었습니다.

```
'books' 인덱스를 nori 매핑으로 재생성했습니다.
MySQL에서 book 100048건 조회 완료
... (bulk 청크 101개)
OpenSearch 'books' 인덱스에 100048건 색인 완료
```

## **5. 벤치마크 도구**

지난 글의 `ExplainAnalyzer.kt`와 같은 케이스로 비교해야 의미가 있으므로, `KeywordCase`와 다섯 케이스를 공유 객체로 뽑아냈습니다.

```kotlin
// src/devtools/kotlin/org/library/devtools/benchmark/KeywordCases.kt

data class KeywordCase(val label: String, val keyword: String, val note: String)

object KeywordCases {
    val all = listOf(
        KeywordCase("흔한 키워드", "스프링", "다수 매칭 예상 (tier S)"),
        KeywordCase("희귀 키워드", "미니멀리즘 실천법", "소수 건만 매칭 예상 (tier Rare)"),
        KeywordCase("접두 검색", "클린", "'클린 코드'/'클린 아키텍처' 등 접두 매칭"),
        KeywordCase("형태소 경계를 넘는 부분 문자열", "린코", "'클린 코드'의 어중간한 substring, 단어 경계 무시"),
        KeywordCase("오타(fuzzy)", "스프릥", "'스프링'의 1글자 오타"),
    )
}
```

`ExplainAnalyzer.kt`는 이 공유 리스트를 참조하도록 한 줄만 바꿨습니다(`private val cases = KeywordCases.all`). 새 도구는 각 케이스에 대해 (1) suggest 블록을 포함해 검색하고, (2) 제안어가 있으면 그 제안어로 다시 검색해 보정 전/후 매칭 건수를 함께 뽑습니다.

```kotlin
// src/devtools/kotlin/org/library/devtools/benchmark/SuggestAnalyzer.kt

fun main() {
    val reports = OpenSearchConfig.newClient().use { client ->
        KeywordCases.all.map { case ->
            val (before, options) = searchWithSuggest(client, case.keyword)
            val after = options.firstOrNull()?.let { searchOnly(client, it.text) }
            CaseReport(case, before, options, after)
        }
    }
    printSuggestDetails(reports)
    printSummaryTable(reports)
}

private fun searchWithSuggest(client: RestClient, keyword: String): Pair<SearchResult, List<SuggestOption>> {
    // multi_match 검색과 suggest 블록을 한 요청에 같이 태운다(실제 서비스에서도 왕복 1회면 충분).
    val quoted = objectMapper.writeValueAsString(keyword)
    val body = """
        {
          "query": { "multi_match": { "query":$quoted, "fields": ["title", "author"], "fuzziness": "AUTO" } },
          "suggest": {
            "${BookIndex.TITLE_SUGGEST_NAME}": {
              "text":$quoted,
              "phrase": {
                "field": "${BookIndex.TITLE_TRIGRAM_FIELD}",
                "gram_size": 3,
                "confidence": 1.0,
                "max_errors": 2,
                "direct_generator": [
                  { "field": "${BookIndex.TITLE_SUGGEST_FIELD}", "suggest_mode": "always",
                    "min_word_length": 1, "prefix_length": 1, "max_edits": 2 }
                ]
              }
            }
          }
        }
    """.trimIndent()
    // ...
}
```

파라미터를 해당 값으로 이유를 정리하면 이렇습니다.

- **`min_word_length: 1`** — 가장 중요한 한 줄입니다. 기본값은 **4**입니다. 한글 콘텐츠 단어는 2~3음절이 흔한데(“클린”, “스프릥”), 기본값을 그대로 두면 애초에 후보 생성 대상에서 조용히 제외됩니다. 에러도 없이 그냥 제안이 0건 나옵니다.
- **`prefix_length: 1`**(기본값과 동일, 명시) — 첫 글자는 정확히 일치해야 합니다. 첫 음절에서 난 오타는 이 설정으로는 교정되지 않습니다. `0`으로 낮추면 후보 폭이 넓어지는 대신 계산 비용이 늘어납니다.
- **`max_edits: 2`**(기본값과 동일, 명시) — 지난 글 4.3절과 대조되는 지점입니다. 그때는 1~2글자로 쪼개진 nori 조각 기준 편집거리라 `fuzziness: 2`가 10만 건 중 8만 건을 잡아먹었는데, 여기서는 **통짜 단어**(“스프릥” vs “스프링”) 기준 편집거리라 폭발하지 않습니다.
- **`max_errors: 2`** — 기본값 1.0(문구당 오탈자 허용 단어 1개)으로는 두 단어짜리 “미니멀리즘 실천법” 같은 케이스가 막힐 수 있어 올렸습니다.
- **`confidence: 1.0`**(기본값과 동일, 명시) — 후보 문구가 원본보다 점수가 높아야만 채택됩니다. 이게 두 가지를 동시에 해결합니다. 정상 철자 키워드에서 빈 `options`가 나오는 이유이고, `fuzziness: 2`처럼 폭발하지 않는 구조적인 이유이기도 합니다. **fuzzy 쿼리는 “후보를 넓혀서 전부 매칭”이지만, suggester는 “후보를 넓혀서 채점한 뒤 상위 몇 개만 반환”입니다.** 결과 집합에 직접 영향을 주지 않고, 제안이라는 별도 채널로만 나옵니다.

## **6. 결과**

```bash
$ ./gradlew ... SuggestAnalyzerKt
```

```
## Phrase Suggester 원본 응답

흔한 키워드 ("스프링")        : (제안 없음)
희귀 키워드 ("미니멀리즘 실천법") : (제안 없음)
접두 검색 ("클린")            : (제안 없음)
부분 문자열 ("린코")          : (제안 없음)
오타(fuzzy) ("스프릥")        : 스프링  score=0.12276865
  보정 후 상위 3건: 스프링 / 스프링 / 스프링
```

| 케이스 | 키워드 | 제안어 | score | 보정 전 hits | 보정 후 hits |
| --- | --- | --- | --- | --- | --- |
| 흔한 키워드 | 스프링 | - | - | 8,073 | - |
| 희귀 키워드 | 미니멀리즘 실천법 | - | - | 16 | - |
| 접두 검색 | 클린 | - | - | 4,258 | - |
| 부분 문자열 | 린코 | - | - | 0 | - |
| **오타** | **스프릥** | **스프링** | 0.1228 | **0** | **8,073** |

“스프릥” 행이 이번 글의 답입니다. 3장에서 nori 필드에 그대로 걸었을 때 나왔던 “스프링 릥”과 달리, 전용 필드에서는 고아 조각 없이 깔끔하게 “스프링”으로 교정됐고, 그 제안어로 다시 검색하니 지난 글의 정상 매칭 건수(8,073)와 정확히 일치했습니다.

나머지 네 케이스는 전부 제안이 없습니다. 이것도 원하던 동작입니다. 정상 철자인 “스프링”·“클린”은 고칠 이유가 없으니 빈 결과가 맞고, 특히 “미니멀리즘 실천법”이 3장의 “미니멀리즘 실전 법” 같은 오교정 없이 그대로 통과된 게 `confidence: 1.0`이 제 역할을 했다는 뜻입니다.

**“린코”는 여전히 0건입니다.** 이건 버그가 아니라 예상된 한계입니다. “린코”는 오타가 아니라 “클린 코드”의 형태소 경계를 넘는 부분 문자열이고, 후보 사전 어디에도 “린”으로 시작하는 단어가 없으니 애초에 편집거리로 비교할 대상이 생기지 않습니다. Suggester가 모든 검색 실패를 해결해주지는 않습니다. 오타 교정과 부분 문자열 검색은 다른 문제이고, 후자는 n-gram이나 wildcard 같은 다른 도구의 영역입니다.

## **7. 후보는 어떻게 만들어지나 — 용어 사전 탐색의 비밀**

지금까지는 Phrase Suggester를 블랙박스로 놓고 결과만 봤습니다. 이번 장은 그 안을 열어봅니다. “스프릥”이 어떻게 “스프링”이라는 후보로 좁혀지는지, 그리고 그 탐색이 왜 용어 사전이 커도 느려지지 않는지를 확인합니다.

### **7.1 Term 단계만 떼어보기**

Phrase Suggester는 사실 두 단계로 이뤄져 있습니다. (1) `direct_generator`가 철자 후보를 뽑고, (2) `field`(shingle)가 그 후보를 문장에 끼워 넣었을 때 자연스러운지 채점합니다. 1단계만 따로 떼어, `title.suggest` 필드에 Term Suggester만 걸어봤습니다.

```bash
curl -s -X POST "localhost:9200/books/_search" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "suggest": {
    "term_only": {
      "text": "스프릥",
      "term": {
        "field": "title.suggest",
        "suggest_mode": "always",
        "min_word_length": 1,
        "prefix_length": 1,
        "max_edits": 2
      }
    }
  }
}'
```

```json
{ "options": [ { "text": "스프링", "score": 0.6666666, "freq": 8073 } ] }
```

`score: 0.6667`은 순수 **문자열 유사도**입니다(대략 `(글자수 - 편집거리) / 글자수` = `(3-1)/3`). 6장에서 본 최종 점수 `0.1228`과는 다른 숫자인데, 그건 2단계(`title.trigram`)가 이 후보를 문장에 대입해 다시 채점한 값이기 때문입니다.

### **7.2 용어 사전이 커도 왜 느리지 않을까**

용어 사전을 하나씩 순회하며 편집거리를 계산하면 사전 크기에 비례해 느려집니다. Lucene(OpenSearch의 검색 엔진 코어)은 용어 사전을 **FST(Finite State Transducer)**, 즉 정렬된 트라이 형태의 압축 구조로 저장합니다. “스프”, “스프링”, “스프트”처럼 접두어가 같은 단어들은 트리에서 같은 경로를 공유합니다.

`prefix_length`는 여기에 한 겹 더 얹는 최적화입니다. 첫 글자가 다르면 자동자 탐색을 시작하기도 전에 그 서브트리 전체를 건너뜁니다. FST가 정렬된 구조라 “스”로 시작하는 가지 하나만 골라 그 안에서만 탐색하면 되고, 나머지 사전은 아예 안 봅니다.

### **7.3 그런데 첫 글자에서 오타가 나면?**

이 최적화는 공짜가 아닙니다. `prefix_length: 1`은 “첫 글자는 무조건 맞다”고 가정하고 탐색 범위를 좁히는 것이므로, **정말로 첫 글자에서 오타가 나면 후보 자체가 안 뽑힙니다.** “스프링”의 첫 글자를 오타낸 “츠프링”으로 실제 확인해봤습니다.

```bash
# prefix_length: 1 (기본값)
curl ... -d '{"suggest":{"t":{"text":"츠프링","term":{"field":"title.suggest",
  "suggest_mode":"always","min_word_length":1,"prefix_length":1,"max_edits":2}}}}'
# -> (후보 없음)

# prefix_length: 0
curl ... -d '{"suggest":{"t":{"text":"츠프링","term":{"field":"title.suggest",
  "suggest_mode":"always","min_word_length":1,"prefix_length":0,"max_edits":2}}}}'
# -> [{"text": "스프링", "score": 0.6666666, "freq": 8073}]
```

`prefix_length: 1`에서는 “츠”로 시작하는 서브트리에서만 찾다 보니 “스”로 시작하는 “스프링”이 탐색 대상에서 아예 제외됩니다. `0`으로 낮추면 정상적으로 잡힙니다.

### **7.4 그래서 얼마나 차이가 나나 — 실측**

같은 “츠프링” 쿼리를 20회씩 반복해 `took`(서버 내부 실측 ms)을 평균 냈습니다.

| prefix_length | 평균 took | 20회 합계 |
| --- | --- | --- |
| `0` | 4.25ms | 85ms |
| `1` | 2.30ms | 46ms |

약 1.8배 차이가 났습니다. 다만 정직하게 말하면, 이 프로젝트의 `title.suggest` 용어 사전은 문서가 10만 건이어도 실제 서로 다른 단어 수는 워드뱅크 규모(수십~수백 개) 수준이라 절대값 차이가 크지 않습니다. (이건 실제 데이터 사이즈 확인 및 쿼리 테스트를 확인해봐야 합니다.) **용어 사전이 훨씬 큰 서비스(어휘가 수십만 개 이상)일수록 이 배율은 더 벌어질 걸로 예상합니다.** 첫 글자 자동 가지치기가 스킵하는 서브트리의 크기 자체가 사전이 커질수록 함께 커지기 때문입니다.

**이 프로젝트에서 `prefix_length: 1`(기본값)을 그대로 쓰기로 한 이유**는 두 가지입니다.

1. 첫 글자 오타는 상대적으로 드뭅니다 — 사람이 단어를 입력할 때 첫 글자는 더 신경 써서 치는 경향이 있어, 커버리지 손실이 크지 않습니다.
2. 반대급부로 얻는 속도 이득이 공짜가 아니라 실측으로 확인되는 이득입니다. 도서관 검색처럼 사전이 작은 서비스에서도 방향성은 같았고, 사전이 커질수록 더 유리해질 걸로 예상되니 기본값을 바꿀 이유가 없었습니다.

첫 글자 오타까지 잡고 싶다면 `prefix_length: 0`으로 낮추는 선택지도 있습니다. 이번 devtools 벤치마크에서는 다루지 않았지만, “커버리지를 올릴지 vs 속도를 지킬지”를 결정하는 다이얼이 있다는 것 자체가 중요한 소득이었습니다.

## **8. 실제 검색 API에 붙이기**

devtools 도구로 방식을 검증했으니, 이제 `/books/search`가 실제로 오타 교정을 돌려주게 만들 차례입니다.

### **8.1 언제 보여줄 것인가**

가장 먼저 정해야 했던 건 “제안을 언제 호출하고, 언제 노출할 것인가”였습니다. 실무에서는 대체로 세 갈래로 나뉩니다.

- **제로 히트 폴백**: 검색 결과가 0건일 때만 제안을 채워서 보여줍니다.
- **배너형**: 결과가 있어도 제안 점수가 충분히 높으면 “OO(으)로 검색하셨나요?” 배너를 같이 띄웁니다.
- **자동 교정**: confidence가 매우 높으면 사용자 모르게 교정된 쿼리로 바로 검색합니다.

호출 자체는 어느 쪽이든 매 검색 요청마다 `query`와 `suggest`를 한 번에 같이 보내는 게 낫습니다. 별도 왕복을 만들면 그만큼 지연만 늘어나기 때문입니다. 그래서 **“호출은 항상 같이 하되, 응답에 실어서 보여줄지는 결과 건수를 보고 결정”**하는 방식으로 갔고, 이 프로젝트 규모(도서 10만 건)에서는 배너형까지 갈 필요 없이 **제로 히트 폴백**이 제일 단순하고 자연스럽다고 판단했습니다. 미리 적어두면, 이 판단은 9장에서 실제 데이터를 넣자마자 뒤집혔습니다.

다만 정직하게 말하면, “suggest 계산 비용이 크지 않다”는 근거로 삼을 수 있는 실측은 7.4절의 `prefix_length` 비교(4.25ms vs 2.30ms)뿐이고, 그마저도 `title.suggest`에 Term Suggester만 단독으로 건 것이지 실제 서비스처럼 `multi_match` + `phrase` suggester를 한 요청에 같이 태운 `took` 값을 잰 건 아닙니다. 결과가 있는 검색에서도 매번 suggest가 함께 계산된다는 걸 감안하면, 이 프로젝트보다 트래픽이 큰 서비스라면 실제 결합 쿼리의 `took`을 별도로 재보고 넘어가는 게 안전합니다.

### **8.2 `BookSearchPort`가 제안도 함께 반환하도록**

`BookSearchPort.search`의 반환 타입을 `Page<BookDocument>`에서, 페이지와 제안을 함께 담는 타입으로 바꿨습니다.

```kotlin
// src/main/kotlin/org/library/book/application/port/BookSearchPort.kt

interface BookSearchPort {
    fun search(query: String?, page: PageRequestParams): BookSearchResult
}

data class BookSearchResult(
    val page: Page<BookDocument>,
    val suggestion: String?,
)
```

LIKE·FULLTEXT(ngram) 어댑터는 오타 교정 후보를 만들 수단이 없으니 `suggestion = null`을 그대로 반환합니다. `BookSearchPort`가 엔진에 무관한 추상화인 이상, “이 엔진은 제안을 못 한다”는 사실도 타입으로 표현하는 게 자연스러웠습니다.

### **8.3 OpenSearch 어댑터 — 검색과 제안을 한 요청으로**

`OpenSearchBookSearchAdapter`는 devtools `SuggestAnalyzer.kt`에서 검증한 것과 똑같은 파라미터로, `multi_match` 쿼리와 `suggest` 블록을 **하나의 `_search` 요청**에 같이 실어 보냅니다.

```kotlin
// src/main/kotlin/org/library/book/adapter/OpenSearchBookSearchAdapter.kt

override fun search(query: String?, page: PageRequestParams): BookSearchResult {
    // ... query 구성 ...

    // 검색어가 없는 전체 목록 조회에는 교정할 대상이 없으므로 suggest 블록을 아예 안 붙인다.
    // 있을 때는 히트 유무와 무관하게 매 검색 요청마다 같은 왕복으로 함께 계산해두고,
    // 실제로 화면에 보여줄지는 SearchBooksService가 매칭 건수를 보고 결정한다.
    val suggestField = keyword?.let {
        """
          "suggest": {
            "${BookIndex.TITLE_SUGGEST_NAME}": {
              "text":${objectMapper.writeValueAsString(it)},
              "phrase": {
                "field": "${BookIndex.TITLE_TRIGRAM_FIELD}",
                "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
                "confidence": 1.0,
                "max_errors": 2,
                "direct_generator": [
                  {
                    "field": "${BookIndex.TITLE_SUGGEST_FIELD}",
                    "suggest_mode": "always",
                    "min_word_length": 1,
                    "prefix_length": 1,
                    "max_edits": 2
                  }
                ]
              }
            }
          },
        """.trimIndent()
    }.orEmpty()

    // ... requestBody에 $suggestField 삽입, 검색 실행 ...

    return BookSearchResult(
        page = PageImpl(documents, pageRequest, totalHits),
        suggestion = extractSuggestion(root),
    )
}

// 응답 경로: suggest.<name>[0].options[0].text. confidence 기준을 못 넘으면 options가 빈 배열이다.
private fun extractSuggestion(root: JsonNode): String? =
    root.path("suggest").path(BookIndex.TITLE_SUGGEST_NAME).path(0).path("options").path(0).path("text")
        .asString("")
        .takeIf { it.isNotBlank() }
```

`gram_size`는 상수로 빼서 devtools 벤치마크(5장)와 실제 어댑터가 **같은 값을 참조**하도록 했습니다. 이 숫자는 `title.trigram`의 shingle 설정(`max_shingle_size: 3`)과 반드시 일치해야 하는데, 어긋나면 채점 단계에서 없는 차수의 n-gram을 조회하다 백오프만 반복하게 됩니다. 에러가 나지 않고 점수만 조용히 이상해지는 종류의 불일치라, 매핑과 쿼리 양쪽에 흩어놓지 않는 편이 안전합니다.

### **8.4 노출은 `SearchBooksService`가 결정**

어댑터는 항상 제안을 계산해 돌려주지만, 그걸 실제로 응답에 실을지는 서비스 레이어의 몫입니다. “결과가 이미 있으면 제안을 보여줄 이유가 없다”는 전제로 게이트를 짰습니다 — 합성 데이터에서는 오타를 치면 대체로 0건이 나왔으니 맞는 것처럼 보였습니다.

```kotlin
// src/main/kotlin/org/library/book/application/SearchBooksService.kt

fun execute(query: String?, params: PageRequestParams): Response {
    val result = bookSearchPort.search(query, params)
    return Response(
        books = result.page.content,
        pagination = Pagination.from(result.page),
        // 결과가 있으면 굳이 교정어를 보여줄 필요가 없어, 매칭 0건일 때만 채운다(제로 히트 폴백).
        suggestion = result.suggestion.takeIf { result.page.totalElements == 0L },
    )
}

data class Response(
    val books: List<BookDocument>,
    val pagination: Pagination,
    val suggestion: String?,
)
```

## **9. 합성 데이터를 실제 데이터로 바꾸고 나서**

여기까지가 합성 데이터(도서 10만 건, 워드뱅크 70개 단어로 조합) 기준입니다. 그 뒤에 실제 도서관 목록 **4,147건**을 넣었더니, 위에서 “된다”고 확인했던 것들이 줄줄이 무너졌습니다.

### **9.1 “나무와”를 검색했더니**

```
도서 검색 결과
신갈나무투쟁기 / 숲의향기와빛깔그리고멈추지않는사랑 / 나무야미안해
도시와농촌을이어주는아이들 / 흙돌풀꽃나무와함께크는아이들 ...
```

`나무와숲`이라는 책이 인덱스에 분명히 있는데 상위 20위 안에 없었습니다. 대신 `도시와농촌을…`, `지구온난화와산림` 처럼 “나무”와 무관한 책이 잔뜩 올라왔습니다. 원인은 셋이 겹쳐 있었습니다.

### **9.2 조사, 그리고 버려지던 점수**

첫째, `korean_nori`에 `nori_part_of_speech` 필터가 빠져 있었습니다. 조사가 토큰으로 그대로 살아남습니다.

```bash
curl -s "localhost:9200/books/_analyze" -d '{"analyzer":"korean_nori","text":"나무와"}'
# ['나무', '와']   <- 조사 '와' 가 색인/검색됨
```

`multi_match`의 기본 연산자는 OR이니, **조사 하나만 맞아도 히트합니다.** `숲의향기**와**빛깔`, `지구온난화**와**산림` 이 걸린 이유입니다. “우주의”로 재보면 규모가 더 선명합니다.

| 토큰 | 매칭 문서 |
| --- | --- |
| `우주` | 7건 |
| `의` | **1,130건** |

둘째, `sort`가 `_score`를 통째로 버리고 있었습니다.

```kotlin
"sort": [ { "createdAt": "desc" } ]   // ← _score 를 계산조차 하지 않는다
```

OpenSearch는 `sort`를 명시하면 `_score`를 계산하지 않습니다(응답의 `_score`가 전부 `null`로 옵니다). 즉 **“매치되기만 하면 등록 최신순”** 이었습니다. `sort`의 첫 키에 `"_score"`를 넣어 고쳤습니다.

셋째, 지난 글의 숙제라 여겼던 `fuzziness: AUTO`가 실은 **한국어에서 거의 동작하지 않고 있었습니다.** AUTO는 토큰 길이 2자 이하에 편집거리 0을 주는데, 한국어 형태소는 대부분 2음절입니다.

| 검색어 | 토큰 길이 | 허용 편집거리 | 오타 실측 |
| --- | --- | --- | --- |
| 숲 | 1 | 0 | `슾` → 0건 |
| 나무 | 2 | 0 | `나모` → 0건 |
| 그리고 | 3 | 1 | `그리구` → 15건 ✅ |

합성 데이터의 워드뱅크가 `스프링`(3자)·`알고리즘`(4자)·`데이터베이스`(6자)로 전부 3음절 이상이라, 5~7장에서 오타 교정이 잘 되는 것처럼 보였던 겁니다. **오타 교정은 suggester가 담당하므로 쿼리에서 `fuzziness`를 아예 빼버렸습니다.**

덧붙여 `author`도 `korean_nori`로 분석하고 있었는데, 인명은 형태소 분해 대상이 아닙니다.

```
권진동   → ['권', '진동']       안진권 → ['안진', '권']
이위영   → ['이위영']           문학의집서울 → ['문학', '의', '집', '서울']
```

같은 형태의 이름이 제각각 쪼개지고, 단체명은 조사로 오분해됩니다. 합성 데이터의 저자명은 `김민준` 같은 성1+이름2 정형 200종이라 항상 한 토큰으로 남아 이 문제가 드러나지 않았습니다. `author`를 `plain_word`로 바꿨습니다.

### **9.3 조사를 지워도 끝이 아니었습니다**

품사 필터를 넣고 한참 뒤에 “강원대”를 검색했다가 같은 증상을 다시 만났습니다.

```
"강원대" → 24건
   대나무 / 지식의 대융합 / 자살을꿈꾸는십대 / 중국대나무도감 ...
```

```
title 분석: "강원대" → [강원, 대]

match(title:강원) =  0건   ← 정작 찾으려는 것
match(title:대)   = 24건   ← "대" 한 글자가 결과를 지배
```

이번엔 조사가 아니라 **접미사**입니다. `nori_part_of_speech`의 기본 stoptags는 품사 태그로 거르는데, “강원대”의 “대”는 일반명사로 태깅되니 손댈 수 없습니다. 앞서 “주치의”의 “의”가 조사가 아닌 명사로 남았던 것과 같은 종류입니다. 그렇다고 “대”를 stop 필터에 넣으면 “대나무”·“대융합”이 같이 죽습니다.

패턴을 보니 **토큰이 2개일 때만** 터졌습니다.

| 검색어 | 토큰 | 0건인 쪽 | 결과를 지배하는 쪽 |
| --- | --- | --- | --- |
| 국기의 | `[국기, 의]` | 국기 | 의 |
| 강원대 | `[강원, 대]` | 강원 | 대 |

토큰이 2개면 하나가 죽는 순간 남는 게 하나뿐이라 그 한 글자가 결과 전체를 결정합니다. 반면 토큰이 6~7개면 하나가 어긋나도 나머지가 서로를 검증해줍니다. **짧으면 엄격, 길면 관대**가 필요한 상황이라, 단어를 하나씩 막는 대신 `minimum_should_match`로 갔습니다.

```kotlin
"multi_match": {
  "fields": ["title^3", "author^2"],
  "minimum_should_match": "2<75%"   // 토큰 2개 이하면 전부, 3개 이상이면 75%(내림)
}
```

`"2<75%"`는 “절이 2개를 넘으면 75%, 2개 이하면 전부”로 읽습니다. 흔히 쓰는 `"75%"`나 `"-1"`은 이 문제를 못 막습니다 — 토큰 2개에 적용하면 계산 결과가 1이 되어 지금과 똑같아집니다.

| 검색어 | 이전 | 이후 |
| --- | --- | --- |
| 강원대 | 24 | **0** |
| 국기의 | 3 | **0** |
| 나무와숲 | 142 | **6** |
| 나무와 | 57 | 57 |
| 권 | 113 | 113 |

정상 검색어 400건 회귀에서 **결과 유실 0건, 1위 변경 3건**(그중 “유머의”는 `유소유` → `유머의 힘`으로 개선)이었고 히트는 208건 줄었습니다. 대가도 있습니다. 토큰 2개짜리 오타는 이제 0건이 됩니다 — “권력의종만”이 예전엔 5건 중 1위로 정답을 보여줬는데 지금은 0건 + 제안으로 바뀝니다. 91건이 전부 무관하던 “나모와숲” 같은 경우엔 오히려 나아지지만, 제안 배너가 화면에 붙어야 손해가 상쇄됩니다.

덧붙여, 이 변경으로 4.2절에서 고민하던 “조사 stop 필터”가 불필요해졌습니다. 단어를 손으로 채워 넣는 목록은 늘어날 수밖에 없는데, 토큰 수 기준 규칙 하나가 같은 문제를 더 넓게 덮습니다.

### **9.4 “린코”의 뒷이야기 — 부분 문자열**

6장에서 “린코”가 0건인 걸 “오타 교정과 부분 문자열 검색은 다른 문제”라며 넘겼습니다. 실제 데이터에서 이게 바로 문제가 됐습니다. “권”으로 검색하면 44건이 나오는데, DB에는 제목에 “권”이 든 책이 52건, 저자에 든 책이 62건 있었습니다.

```
권력의종말      → ['권력', '의', '종말']       "권" 으로 매치 안 됨
상처받지않을권리  → ['상처', '받', '지', '않', '을', '권리']
세상을바꾼12권의책 → [..., '12', '권', '의', '책']   ← 여기만 매치
```

형태소 단위 완전 일치로는 경계 안쪽을 볼 수 없습니다. 그래서 한 글자 단위 ngram 서브필드를 추가했습니다.

```json
"char_unigram": { "type": "ngram", "min_gram": 1, "max_gram": 1,
                  "token_chars": ["letter", "digit"] }
```

`min_gram = max_gram = 1`로 고정한 건 1글자 검색어(“권”)를 포기할 수 없었기 때문입니다. 그리고 이 필드는 **반드시 `match_phrase`로 조회해야 합니다.**

| 검색어 | `match` (OR) | `match_phrase` (위치 연속) |
| --- | --- | --- |
| 나무와숲 | 585건 | **1건** |
| 우주의기원 | 1,763건 | **1건** |

`text` 필드는 `index_options`가 기본 `positions`라 역색인에 글자 위치가 함께 저장됩니다. `match_phrase`가 그 위치를 대조해 `n, n+1, n+2` 연속을 요구하므로, 결과적으로 `LIKE '%입력%'`와 같은 의미가 됩니다. `match`로 걸면 아무 글자나 OR로 걸려 노이즈가 폭증합니다.

최종 쿼리는 `bool.should` 세 절입니다. 형태소 절이 그물을 넓게 던지고(재현율), 부분 문자열 절이 정확한 것을 위로 끌어올립니다(정밀도). 둘 다 맞은 문서는 점수가 합산돼 자연히 1위가 됩니다.

```kotlin
"bool": {
  "minimum_should_match": 1,
  "should": [
    { "multi_match":  { "query": q, "type": "best_fields",
                        "fields": ["title^3", "author^2"] } },
    { "match_phrase": { "title.ngram":  { "query": q, "boost": 2 } } },
    { "match_phrase": { "author.ngram": { "query": q, "boost": 1 } } }
  ]
}
```

`must`(AND)를 쓰지 않은 이유가 있습니다. “우주의”는 토큰이 `[우주, 의]`인데 정답 `우주의기원`의 토큰은 `[우주, 기원]`입니다. AND로 묶으면 정답이 0건이 됩니다.

### **9.5 제로 히트 폴백을 뒤집다**

8.4절의 전제 — “결과가 있으면 제안을 보여줄 이유가 없다” — 가 실제 데이터에서 정반대로 작동했습니다.

```
"나모와숲" (오타, 정답 "나무와숲")
  히트 91건, 1위 "다숲"          <- 전부 "숲" 한 글자로만 걸린 무관한 책
  제안 "나무와숲"                <- 계산은 됐지만 0건이 아니라서 버려짐
```

한국어 오타는 조사나 한 글자가 걸려 **0건이 안 나오는 경우가 대부분**입니다. 정작 필요할 때 숨기는 게이트였습니다. 그럼 게이트를 없애면 되나 싶었지만, `confidence: 1.0`만으로는 정상 검색어의 13%에 엉뚱한 제안이 붙었습니다(`조경` 32건인데 “조건?”). `confidence`를 1.5로 올려보니 이번엔 진짜 오타 교정이 전부 죽었습니다.

대신 데이터에서 규칙을 찾았습니다. **오타는 검색어 원문이 어떤 제목·저자에도 부분 문자열로 존재하지 않습니다.** 9.3절에서 만든 `title.ngram`으로 이미 계산할 수 있어서, 같은 요청에 filter 집계 하나만 얹었습니다.

```kotlin
"aggs": { "exact_substring": { "filter": { "bool": { "minimum_should_match": 1,
  "should": [ { "match_phrase": { "title.ngram":  q } },
              { "match_phrase": { "author.ngram": q } } ] } } } }
```

```kotlin
// 게이트가 서비스 레이어에서 어댑터로 내려왔다.
suggestion = extractSuggestion(root).takeIf { exactSubstringHits == 0L }
```

노출 조건이 결국 두 개의 AND가 됐습니다. `confidence 1.0`(원본보다 나은 후보가 있나)과 `exact_substring == 0`(고칠 필요가 있나). 정상 검색어 250건으로 재보니 오탐이 13% → **0%**, 실제 오타 6건은 전부 노출됐습니다.

### **9.6 띄어 쓰면 제안이 어긋납니다**

4.1절에서 `standard` 토크나이저가 “한글을 통째로 보존한다”는 걸 장점으로 꼽았습니다. 실제 데이터에서는 그게 양날이었습니다. 제목이 붙여쓰기라 `title.suggest`의 term이 **“제목 전체”** 가 되기 때문입니다.

```
문서 '강원국의글쓰기' → title.suggest → ['강원국의글쓰기']   term 1개
```

그래서 사용자가 띄어 쓰면 검색어만 조각나고, 통짜 제목과 편집거리가 멀어집니다.

```
"강원기의글쓰"(6자) vs "강원국의글쓰기"(7자)  → 편집거리 2  ✅ 후보 생성
"강원기의"(4자)     vs "강원국의글쓰기"(7자)  → 편집거리 4  ❌ 초과
```

증상이 두 갈래로 나왔습니다. 후보가 아예 안 만들어지거나, 토큰 하나만 엉뚱하게 고친 제안이 나옵니다.

```
"강원긱의 글쓰기" → 0건, 제안 없음
"강원기의 글쓰"   → 0건, 제안 "강원기의 글로"   ← 오타는 그대로 두고 뒤만 고침
```

`direct_generator`에 후보 필드를 더 붙여봤지만(nori `decompound_mode: none`, 문자 3~5gram) 소용없었습니다. 후보는 생성되는데 `confidence: 1.0`에서 원본에 집니다 — 실제 제목이 `강원국의글쓰기`(공백 없음)라 교정안 `강원국의 글쓰기`도 인덱스에 없어서, 둘 다 백오프 바닥값을 받고 편집 벌점이 없는 원본이 이기기 때문입니다. **phrase suggester는 토큰을 바꿀 수는 있어도 합칠 수는 없습니다.**

색인을 더 늘리는 대신 애플리케이션에서 해결했습니다. 결과가 0건이면 **공백을 지워 제안만 한 번 더 요청**하고, 언어모델 점수가 높은 쪽을 채택합니다.

```kotlin
if (keyword == null || totalHits > 0L || exactSubstringHits > 0L) return primary
val stripped = keyword.replace(WHITESPACE, "")
...
return if (primary == null || retried.score > primary.score) retried else primary
```

점수 차이가 커서 비교가 애매하지 않았습니다.

```
"강원기의 글쓰" → "강원기의 글로"    0.0000805
"강원기의글쓰"  → "강원국의글쓰기"   0.0149606      ← 186배
```

처음엔 “1차 제안이 없을 때만” 재시도하려 했는데, 점수 비교로 바꾸니 **틀린 제안까지 교정**됐습니다.

| 검색어 | 이전 제안 | 이후 제안 |
| --- | --- | --- |
| 강원기의 글쓰 | 강원기의 글로 | **강원국의글쓰기** |
| 우주의 기언 | 우리의 기술 | **우주의기원** |
| 권력의 종만 | 권력과 종말 | **권력의종말** |
| 강원긱의 글쓰기 | (없음) | **강원국의글쓰기** |

`totalHits > 0`이면 재시도하지 않는 조건이 중요합니다. 어순만 뒤바꿔 입력한 경우(결과는 있는데 원문이 부분 문자열로는 없는 경우)에 엉뚱한 제안이 붙는데, 이 조건을 넣으니 해당 표본에서 발동률이 7.3% → 0.7%로 떨어졌습니다. 검색어 유형별 오탐을 재보면 제목 전체·앞 두 단어·앞 세 단어 각 120건에서 **재시도가 발동조차 하지 않습니다**(0건). 현실적인 검색어는 `exact_substring > 0`이거나 결과가 있어서 조건에 걸리지 않기 때문입니다. 추가 왕복은 0건일 때만 일어납니다.

### **9.7 무작위 오타를 퍼부어 봤더니**

여기까지는 제가 손으로 고른 오타로만 확인했습니다. 편향이 걱정돼서 자모 단위로 오타를 자동 생성해 대량으로 돌렸습니다. 실제 제목에서 음절 하나를 골라 초성·중성·종성 중 하나를 무작위로 바꾸는 방식입니다.

```
'검은 여우'  → '껌은 여우'      (초성 ㄱ → ㄲ)
'신의 대리인' → '신에 대리인'     (중성 ㅢ → ㅔ)
```

두 가지가 새로 나왔습니다.

**① 존재하지 않는 제목을 제안합니다.** 제안이 뜬 292건 중 13건(4.5%)이 그랬습니다.

```
'신에 대리인'  → 제안 '신화 대리인'      ← 그런 책은 없다
'껌은 여우'    → 제안 '껌은 여행'        ← 오타는 그대로 두고 뒤만 고침
```

Phrase Suggester는 **실재하는 제목을 찾는 게 아니라 토큰을 조합합니다.** `신화`도 `대리인`도 각각 인덱스에 있으니 `신화 대리인`은 유효한 경로입니다. 그 조합이 실제 책인지는 확인하지 않습니다.

원래 채점 단계가 걸러야 하는데(`title.trigram`에 `신의 대리인`은 있고 `신화 대리인`은 없습니다), 정작 `신의`가 채점까지 도달하지 못했습니다. `신에`의 후보를 빈도순으로 줄 세우면 `신의`가 6번째인데 `direct_generator.size` 기본값이 5라 잘립니다.

**② 제안어가 소문자로, 문장부호가 빠진 채 내려옵니다.**

```
원본 'WTO체제와한국농업의진로'          → 제안 'wto체제와한국농업의진로'
원본 '불패의 리더 이순신, 그는 어떻게…'   → 제안 '불패의 리더 이순신 그는 어떻게…'
```

`title.suggest`가 `lowercase`를 태우고, 토크나이저가 버린 쉼표·콜론은 복원되지 않습니다. suggester는 색인된 term을 공백으로 이어붙일 뿐입니다.

둘 다 같은 방법으로 잡혔습니다. **제안어로 실제 문서를 한 번 찾아보고, 있으면 그 문서의 원문 제목을 대신 돌려주는 것**입니다.

```kotlin
val suggestion = bestSuggestion(primary, keyword, totalHits, exactSubstringHits)
    ?.let { resolveToRealTitle(it.text) }
```

`resolveToRealTitle`은 9.4절에서 만든 `title.ngram`에 `match_phrase`를 겁니다. 이 필드는 공백·문장부호·대소문자를 모두 무시하므로 **표기가 달라도 같은 책을 찾아내고, 아무것도 못 찾으면 존재하지 않는 제안이니 버립니다.** 검증과 원문 복원이 한 번의 조회로 동시에 됩니다.

```
제안 'wto체제와한국농업의진로'  → 1건 → 실제 제목 'WTO체제와한국농업의진로'  ✅
제안 '신화 대리인'            → 0건 → 폐기                              ✅
```

후보 컷오프도 같이 손봤습니다. 다만 파라미터를 **둘 다** 올려야 합니다.

```kotlin
private const val GENERATOR_CANDIDATES = 20   // direct_generator.size + max_inspections
private const val PHRASE_PATHS = 12           // phrase.size
```

`direct_generator.size`는 “후보를 몇 개 뽑을지”, `phrase.size`는 “경로를 몇 개 탐색할지”입니다. 하나만 올리면 다른 쪽이 병목이라 결과가 **완전히 동일**합니다 — 처음에 `phrase.size`를 빼먹고 “효과 0”이라는 잘못된 결론을 낼 뻔했습니다.

| 설정 | 정답 | 표기만 다름 | 오답 | 제안 없음 |
| --- | --- | --- | --- | --- |
| 기존 | 145 (61.7%) | 20 | 17 | 53 |
| 검증만 추가 | 165 (70.2%) | **0** | **4** | 66 |
| 검증 + 후보 확대 | **168 (71.5%)** | **0** | **4** | 63 |

효과의 대부분은 검증에서 나옵니다. 후보 확대는 3건을 더 얹는데, 바뀐 3건이 전부 개선이고 악화가 없어서 같이 넣었습니다.

비용은 거의 없습니다. 검증 조회는 평균 `took` **0.11ms**이고, 정상 검색어에서는 아예 발동하지 않습니다 — `exact_substring` 게이트가 앞단에서 막아 제안 자체가 안 만들어지기 때문입니다. 후보 확대도 지연 차이가 측정 노이즈에 묻혔습니다(2.35ms → 2.16ms).

엣지 케이스도 21종 확인했습니다. 빈 문자열·공백·이모지·200자·개행·따옴표·역슬래시·`{json:1}`·`나무 AND 숲`·`나무*`·`나무~2` 전부 예외 없이 처리되고, 쿼리 문법이 주입되지 않습니다. `objectMapper.writeValueAsString` 이스케이프가 제 역할을 합니다.

### **9.8 결과와 회귀 검증**

제목 전체·단어·저자명에서 뽑은 검색어 400건으로 변경 전후를 비교했습니다. 결과 유실 0건, 히트 증가 0건, 1위 변경 0건이었습니다. 이 회귀 스크립트가 시행착오도 잡아냈는데, 조사 15개를 한꺼번에 stop 필터로 지웠다가 `나무야미안해`가 0건이 된 케이스가 그랬습니다. `decompound_mode: mixed`가 만드는 토큰 그래프에 구멍이 뚫려 쿼리 빌더가 빈 쿼리를 뱉은 것이었고, `_validate/query`로 확인해 되돌렸습니다.

| 검색어 | BEFORE | AFTER | AFTER 1위 |
| --- | --- | --- | --- |
| 우주의 | 1,145 | **4** | 우주의기원 |
| 나무와 | 179 | **57** | 나무와숲 |
| 나무와숲 | 263 | **6** | 나무와숲 |
| 신영복 | 12 | **6** | 나무야 나무야 |
| 권 | 44 | **113** | 퇴마록 (14권) |
| 강원대 | 24 | **0** | — |
| 나모와숲 | 213 | **0** | — (제안 “나무와숲”) |

`권`만 히트가 늘었는데 이게 정상입니다. 부분 문자열 절이 `권력`·`권리`·`집권`에 묻힌 책까지 잡아냈고, DB의 `title LIKE '%권%'` 52건 + `author LIKE '%권%'` 62건과 정확히 맞습니다.

사용자 관점 지표도 재봤습니다. 오타를 쳐도 검색 결과에 원본이 올라오는 경우가 있으니, 제안 정답률만으로는 체감을 알 수 없습니다. 둘을 합친 **도달률**은 이렇습니다.

```
오타 372건 — 상위 3건 기준
   오타에도 검색 결과에 원본이 있음   179건 (48.1%)
   제안이 정답                   273건 (73.4%)
   ── 둘 중 하나로 도달           306건 (82.3%)
```

못 도달하는 17.7%는 원인이 명확합니다. 오타 위치를 나눠 보면 드러납니다.

| 오타 위치 | 도달률 |
| --- | --- |
| 첫 음절 | 48.2% |
| 나머지 위치 | **83.0%** |

7.3절에서 트레이드오프로 받아들였던 `prefix_length: 1`이 여기서 값을 치릅니다. 다만 제 표본은 오타 위치를 균등하게 뿌린 것이고, 실제 사용자는 첫 글자를 더 신경 써서 칩니다. 현실 분포에서는 82%보다 높게 나올 걸로 봅니다.

가장 큰 교훈은 데이터 쪽이었습니다. `MysqlDataGenerator`는 애초에 **부하·카디널리티 실험용**으로 만든 도구인데(희귀 키워드 빈도를 조절하는 `RareKeywordPlan`이 그 흔적입니다), 그걸 정확도 검증에도 그대로 썼습니다. 워드뱅크가 70개 단어에 전부 띄어쓰기가 있고 조사가 거의 없으니, 형태소 분석기가 할 일이 사실상 없어서 어떤 버그도 표면화되지 않았습니다. **정확도 회귀는 합성 데이터가 아니라 실제 데이터에서 샘플링한 검색어로 돌려야 합니다.**

## **정리**

- nori 필드에 Phrase Suggester를 그대로 걸면, 지난 글에서 fuzziness를 망가뜨렸던 것과 같은 원인(사전 기반 토큰화로 인한 조각화)이 오타 교정 후보 생성 단계에서도 재현됩니다. “스프릥”이 “스프링 릥”으로, 심지어 정상 문구 “미니멀리즘 실천법”이 “미니멀리즘 실전 법”으로 잘못 고쳐졌습니다.
- **해법은 nori를 거치지 않는 전용 필드였습니다.** `standard` 토크나이저는 한글 음절을 알파벳과 같은 부류로 취급해 공백 기준으로만 잘라, “스프릥”을 통째로 보존합니다. 이 필드에서 whole-word 편집거리를 비교하니 “스프링”이 바로 찾아졌습니다.
- **`min_word_length`의 기본값(4)은 한글 오타 교정에서 조용한 함정입니다.** 2~3음절짜리 한글 단어가 통째로 후보 생성 대상에서 빠집니다.
- `confidence`로 원본보다 점수가 낮은 후보를 걸러내기 때문에, 지난 글에서 `fuzziness: 2`가 8만 건을 잡아먹었던 것과 달리 결과 집합을 오염시키지 않습니다. 정상 철자에는 제안이 아예 나오지 않습니다.
- **후보 탐색은 사전을 다 훑지 않습니다.** Lucene은 용어 사전을 FST로 저장하고, fuzzy 후보는 Levenshtein Automaton과의 교집합 연산으로 찾습니다. `prefix_length: 1`은 여기에 “첫 글자가 다르면 그 서브트리는 아예 안 본다”는 가지치기를 한 겹 더 얹는 것이고, 실측으로도 `prefix_length: 0` 대비 약 1.8배(4.25ms → 2.30ms) 빨랐습니다. 대신 첫 글자 오타는 못 잡는다는 트레이드오프가 있습니다.
- **devtools에서 검증한 방식을 그대로 실제 API에 붙였습니다.** `BookSearchPort.search`가 `BookSearchResult(page, suggestion)`을 반환하도록 바꾸고, OpenSearch 어댑터는 매 검색 요청에 `query`와 `suggest`를 한 번에 같이 보냅니다. 실제로 `/books/search?q=스프릥`을 호출하면 `books: []`에 `suggestion: "스프링"`이 실려 옵니다.
- **그리고 실제 데이터를 넣자 합성 데이터가 가려두었던 것들이 한꺼번에 드러났습니다.** 조사가 토큰으로 살아남아 `의` 하나로 1,130건이 걸리고, `sort`가 `_score`를 버려 관련도 순서가 아예 없었고, `fuzziness: AUTO`는 한국어 2음절 형태소에 편집거리 0을 줘서 처음부터 동작하지 않고 있었습니다. 워드뱅크 단어가 전부 3음절 이상이었던 탓에 5~7장에서는 잘 되는 것처럼 보였던 겁니다.
- **6장에서 “다른 도구의 영역”이라며 남겼던 부분 문자열 검색이 결국 필수였습니다.** 한 글자 ngram 서브필드를 `match_phrase`로 조회하면 `LIKE '%입력%'`가 됩니다(`text` 필드가 기본으로 위치를 색인하기 때문입니다). 형태소 절과 `bool.should`로 묶어 점수를 합산하니, 재현율과 정밀도를 둘 다 챙길 수 있었습니다.
- **제로 히트 폴백은 틀린 게이트였습니다.** 한국어 오타는 조사가 걸려 0건이 안 나오는 경우가 대부분이라, 정작 필요할 때 제안을 숨겼습니다. “검색어 원문이 데이터에 부분 문자열로 실재하지 않을 때”로 바꾸니 오탐이 13% → 0%가 됐습니다.
- **단어를 하나씩 막는 방식은 막다른 길이었습니다.** 조사 “의”를 stop 필터로 지워도 “강원대”의 “대”처럼 품사로 구를 수 없는 접미사에서 같은 문제가 재발합니다. 원인은 특정 단어가 아니라 **토큰이 2개일 때 하나가 죽으면 남은 하나가 결과를 지배한다**는 구조였고, `minimum_should_match: "2<75%"`(짧으면 엄격, 길면 관대) 한 줄이 목록 관리 없이 같은 문제를 덮었습니다.
- **Phrase Suggester는 실재하는 제목이 아니라 토큰 조합을 만듭니다.** `신에 대리인`에 `신화 대리인`을 제안하는 식으로, 각 토큰은 인덱스에 있지만 그 조합은 아무 책도 아닌 경우가 제안 292건 중 13건이었습니다. 제안어로 실제 문서를 한 번 찾아보고 **그 문서의 원문 제목을 대신 돌려주니**, 존재하지 않는 제안이 걸러지는 동시에 소문자·문장부호 소실까지 함께 해결됐습니다(정답 61.7% → 71.5%, 검증 조회 0.11ms).
- **`standard` 토크나이저의 “통째 보존”은 양날이었습니다.** 붙여쓰기 제목에서는 term이 “제목 전체”가 되어, 사용자가 띄어 쓰면 후보를 못 만들거나 토큰 하나만 엉뚱하게 고칩니다. 후보 필드를 추가해도 `confidence`에서 원본에 지므로, 0건일 때 공백을 지워 제안만 한 번 더 요청하고 점수가 높은 쪽을 택하는 방식으로 풀었습니다.
- **정확도 검증에 합성 데이터를 쓰면 안 됩니다.** 부하 테스트용으로 만든 생성기를 그대로 재활용한 게 이번 문제들의 공통 원인이었습니다. 실제 데이터에서 뽑은 검색어 400건 회귀가 시행착오까지 잡아줬습니다.