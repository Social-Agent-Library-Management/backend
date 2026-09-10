package org.library.external.opensearch.index

import java.text.Normalizer

/**
 * 한글 음절과 자모를 오가는 유니코드 정규화.
 *
 * 오타 교정 후보는 `title.jamo` 사전에서 나오는데, 이 필드는 색인 시점에 `icu_normalizer`
 * (`name: nfc`, `mode: decompose`)로 자모까지 풀려 있다. 검색어도 **같은 방식으로** 풀어
 * 보내야 비교가 성립하므로, 여기서는 그 분해를 JDK 의 같은 규격(NFD)으로 맞춘다.
 *
 * 처음에는 11,172 음절을 직접 나열한 `mapping` char filter 와 손으로 짠 분해·합성 함수를 썼다.
 * 그 방식은 합성 쪽에 함정이 있었는데 — 호환용 자모(`ㄱ` U+3131)에서는 초성과 종성이 같은
 * 문자라 `ㄴㅏㅁㅜ` 의 세 번째 `ㅁ` 이 종성인지 다음 음절 초성인지 한 칸 앞을 봐야 알 수 있다.
 * NFD 가 쓰는 조합용 자모는 초성 `ᄆ`(U+1106)과 종성 `ᆷ`(U+11B7)이 다른 코드포인트라
 * 그 모호함 자체가 없고, 표준 정규화라 왕복이 규격으로 보장된다.
 *
 * 대신 자모 사이의 편집거리가 달라진다. 같은 자리끼리 바뀌는 **치환** 오타는 두 방식이 동일하지만,
 * 글자를 **빠뜨린** 오타는 조합용 쪽이 정렬이 어긋나 거리가 늘어난다(자모 2개 생략 기준 도달률
 * 94% → 63%). `max_edits` 상한이 2라 그만큼을 잃는 셈인데, 직접 만든 표와 분해·합성 로직을
 * 들고 가는 비용보다 표준을 쓰는 쪽이 낫다고 판단했다.
 */
object HangulJamo {

    /** 음절을 자모로 푼다. `"나무와숲"` → `"나무와숲"`(조합용 자모 9개). */
    fun decompose(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

    /** 자모를 음절로 되돌린다. suggester 가 자모 문자열로 후보를 내려주므로 필요하다. */
    fun compose(jamo: String): String = Normalizer.normalize(jamo, Normalizer.Form.NFC)
}
