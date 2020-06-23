package com.hqurve.parsing

import org.junit.jupiter.api.Test

internal class TokenizerTest {

    @Test
    fun parse() {
        val tokenizer = Tokenizer()
        val v = tokenizer.tokenize("a + b > (12 + 1.3* car -2)'Joshua\\\\\\\"'")
        print(v)
        val k = 2
    }
}