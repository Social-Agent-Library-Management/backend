package org.library.external.opensearch.index

import java.text.Normalizer

object HangulJamo {

    fun decompose(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

    fun compose(jamo: String): String = Normalizer.normalize(jamo, Normalizer.Form.NFC)
}
