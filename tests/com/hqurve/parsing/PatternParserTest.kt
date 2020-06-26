package com.hqurve.parsing

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class PatternParserTest {

    @Test
    fun parse() {
        val patternParser = PatternParser()

        val expressions = listOf(
            "<label>[c]\\BracketedStructure{\\PredicateIntegerPair{<label>[p]}{<label>[n]}}\\BracketedStructure{\$1}",
            "\\multexpession ((<symbol>[+] | <symbol> [-]) \\multexpession)*",
            "<label>[car]#{1,}\\mult{<label>[car]}#{,}?"
        )
        val parsedPatterns = expressions.map(patternParser::parse)
        print(parsedPatterns)
    }
}