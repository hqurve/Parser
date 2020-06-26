package com.hqurve.parsing


import java.lang.RuntimeException

internal interface TemplateToken
internal data class ArgumentTemplateToken(val index: Int): TemplateToken
internal data class LiteralTemplateToken(val token: Token?): TemplateToken

internal data class PatternQuantifier(val min: Int, val max: Int, val mode: Mode){
    enum class Mode{GREEDY, RELUCTANT, POSSESSIVE}
    companion object{
        val SINGLE = PatternQuantifier(1, 1, Mode.POSSESSIVE)
    }

    fun isSingle() = min == 1 && max == 1
    fun isEmpty() = max == 0
}

internal interface Pattern
internal class EmptyPattern: Pattern{
    override fun equals(other: Any?): Boolean {
        return other is EmptyPattern
    }
    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
internal data class ArgumentPattern(val index: Int): Pattern
internal data class TokenPattern(val type: String, val arguments: List<TemplateToken>): Pattern
internal data class CallerPattern(val macroName: String, val tokenArguments: List<TemplateToken>, val patternArguments: List<Pattern>): Pattern

internal data class SequentialPattern(val subPatterns: List<Pattern>): Pattern
internal data class BranchedPattern(val subPatterns: List<Pattern>): Pattern
internal data class QuantifiedPattern(val subPattern: Pattern, val quantifier: PatternQuantifier): Pattern

internal class PatternParserException(pos: Int, message: String): RuntimeException("Pattern parser exception at token $pos: $message")

internal class PatternParser{
    companion object{
        fun exception(pos: Int, message: String): Nothing = throw PatternParserException(pos, message)
    }
    private val tokenizer = Tokenizer(includeWhitespaces = false)
    fun parse(string: String) = parse(tokenizer.tokenize(string))
    fun parse(tokens: List<Token>): Pattern{
        val cleanTokens = tokens.filterNot{it is WhitespaceToken}
        val (pattern, pos) = parseGenericPattern(cleanTokens, 0)
        if (pos != cleanTokens.size) exception(pos, "Unable to parse past this point")
        return pattern
    }
    private fun parseTemplateToken(tokens: List<Token>, pos: Int): Pair<TemplateToken, Int>{
        if (pos+1 < tokens.size && tokens[pos] matches TokenPredicate.symbol('[')){
            val (templateToken, tokensUsed)=
            if (tokens[pos + 1] matches TokenPredicate.symbol(']')){
                LiteralTemplateToken(null) to 0
            }else if (tokens[pos + 1] matches TokenPredicate.symbol('%')){
                if (pos + 2 < tokens.size
                    && tokens[pos + 2] matches TokenPredicate.number(1, Int.MAX_VALUE)
                ){
                    ArgumentTemplateToken((tokens[pos + 2] as NumberToken).value.toInt()) to 2
                }else{
                    exception(pos + 2, "Invalid tokenTemplate lookup, expected integer")
                }
            }else if (tokens[pos + 1] matches TokenPredicate.symbol('\\')){
                if (pos + 2 < tokens.size
                    && tokens[pos + 2] matches TokenPredicate.symbol()) {
                    LiteralTemplateToken(tokens[pos + 2]) to 2
                }else{
                    exception(pos + 2, "Invalid escaped tokenTemplate, missing escaped symbol")
                }
            }else{
                LiteralTemplateToken(tokens[pos + 1]) to 1
            }

            if (pos + tokensUsed + 1 < tokens.size
                && tokens[pos + tokensUsed +1] matches TokenPredicate.symbol(']')
            ){
                return templateToken to pos + tokensUsed + 2
            }else{
                exception(pos + tokensUsed + 1, "Missing end bracket in tokenTemplate")
            }
        }else{
            exception(pos, "Invalid template token")
        }
    }
    private fun parsePatternQuantifier(tokens: List<Token>, pos: Int): Pair<PatternQuantifier, Int>{
        if (pos == tokens.size) return Pair(PatternQuantifier.SINGLE, pos)

        val (min, max, tokensUsed) = when{
            tokens[pos] matches TokenPredicate.symbol('?') -> Triple(0, 1, 1)
            tokens[pos] matches TokenPredicate.symbol('*') -> Triple(0, Int.MAX_VALUE, 1)
            tokens[pos] matches TokenPredicate.symbol('+') -> Triple(1, Int.MAX_VALUE, 1)
            tokens[pos] matches TokenPredicate.symbol('#') ->{
                if (pos + 1 == tokens.size || !(tokens[pos+1] matches TokenPredicate.symbol('{')))
                    exception(pos + 1, "Quantifier missing bounds")

                var index = pos + 2
                val firstValue =
                    if (index < tokens.size && tokens[index] matches TokenPredicate.number(0, Int.MAX_VALUE)){
                        (tokens[index++] as NumberToken).value.toInt()
                    }else{
                        null
                    }

                val hasComma =
                    if (index < tokens.size && tokens[index] matches TokenPredicate.symbol(',')){
                        index++
                        true
                    }else{
                        false
                    }

                val secondValue =
                    if (index < tokens.size && tokens[index] matches TokenPredicate.number(0, Int.MAX_VALUE)){
                        (tokens[index++] as NumberToken).value.toInt()
                    }else{
                        null
                    }

                if (index == tokens.size || !(tokens[index] matches TokenPredicate.symbol('}'))) exception(index, "Quantifier requires end '}'")

                val (min, max, tokensUsed) =
                    if (firstValue != null){
                        if (hasComma){
                            if (secondValue != null) Triple(firstValue, secondValue, 3)
                            else Triple(firstValue, Int.MAX_VALUE, 2)
                        }else {
                            if (secondValue != null) exception(pos + 3, "Missing comma (101)")
                            else Triple(firstValue, firstValue, 1)
                        }
                    }else{
                        if (hasComma){
                            if (secondValue != null) Triple(0, secondValue, 2)
                            else Triple(0, Int.MAX_VALUE, 1)
                        }else{
                            if (secondValue != null) exception(pos + 2, "How did we get here (001)")
                            else Triple(0, 0, 0)
                        }
                    }

                Triple(min, max, tokensUsed + 3)
            }
            else -> return Pair(PatternQuantifier.SINGLE, pos)
        }

        //Getting mode
        val mode = when{
            tokensUsed + pos == tokens.size -> PatternQuantifier.Mode.GREEDY
            tokens[pos + tokensUsed] matches TokenPredicate.symbol('?') -> PatternQuantifier.Mode.RELUCTANT
            tokens[pos + tokensUsed] matches TokenPredicate.symbol('*') -> PatternQuantifier.Mode.GREEDY
            else -> PatternQuantifier.Mode.GREEDY
        }

        return Pair(PatternQuantifier(min, max, mode), pos + tokensUsed + (if (mode == PatternQuantifier.Mode.GREEDY) 0 else 1))
    }
    private fun parseTokenPattern(tokens: List<Token>, pos: Int): Pair<TokenPattern, Int>{
        if (pos + 2 >= tokens.size || !(
            tokens[pos] matches TokenPredicate.symbol('<')
            && tokens[pos+1] matches TokenPredicate.label()
            && tokens[pos+2] matches TokenPredicate.symbol('>')
        )) exception(pos, "Invalid token pattern")

        val type = (tokens[pos+1] as LabelToken).str
        val arguments = mutableListOf<TemplateToken>()

        var end = pos + 3
        while(end < tokens.size && tokens[end] matches TokenPredicate.symbol('[')){
            val (templateToken, newEnd) = parseTemplateToken(tokens, end)

            arguments.add(templateToken)

            end = newEnd
        }

        return Pair(TokenPattern(type, arguments.toList()), end)
    }

    private fun parseLookupPattern(tokens: List<Token>, pos: Int): Pair<ArgumentPattern, Int>{
        if (pos + 1  < tokens.size
            && tokens[pos] matches TokenPredicate.symbol('$')
            && tokens[pos + 1] matches TokenPredicate.number(1, Int.MAX_VALUE)
        ){
            return Pair(ArgumentPattern((tokens[pos + 1] as NumberToken).value.toInt()), pos + 2)
        }else{
            exception(pos, "Invalid LookupPattern")
        }
    }

    private fun parseCallerPattern(tokens: List<Token>, pos: Int): Pair<CallerPattern, Int>{
        if (pos + 1 < tokens.size
            && tokens[pos] matches TokenPredicate.symbol('\\')
            && tokens[pos + 1] matches TokenPredicate.label()
        ){
            val macroName = (tokens[pos + 1] as LabelToken).str

            val tokenArguments = mutableListOf<TemplateToken>()
            val patternArguments = mutableListOf<Pattern>()
            var end = pos + 2

            while (end < tokens.size && tokens[end] matches TokenPredicate.symbol('[')){
                val (templateToken, newEnd) = parseTemplateToken(tokens, end)

                tokenArguments.add(templateToken)
                end = newEnd
            }
            while (end < tokens.size && tokens[end] matches TokenPredicate.symbol('{')){
                val (pattern, newEnd) = parseGenericPattern(tokens, end + 1)

                if (newEnd < tokens.size && tokens[newEnd] matches TokenPredicate.symbol('}')){
                    patternArguments.add(pattern)
                    end = newEnd + 1
                }else{
                    exception(newEnd, "Invalid CallerPattern pattern-argument, expected '}'")
                }
            }

            return Pair(CallerPattern(macroName, tokenArguments.toList(), patternArguments.toList()), end)
        }else{
            exception(pos,"Invalid CallerPattern")
        }
    }

    private fun parseBracketedPattern(tokens: List<Token>, pos: Int): Pair<Pattern, Int>{
        if (pos < tokens.size && tokens[pos] matches TokenPredicate.symbol('(')){
            val (subPattern, end) = parseGenericPattern(tokens, pos + 1)

            if (end < tokens.size && tokens[end] matches TokenPredicate.symbol(')')){
                return Pair(subPattern, end + 1)
            }else{
                exception(end, "Missing end bracket in BracketedPattern")
            }
        }else{
            exception(pos, "Invalid BracketedPattern")
        }
    }

    private fun parseGenericPattern(tokens: List<Token>, pos: Int): Pair<Pattern, Int>{
        val parsedPatterns = mutableListOf(mutableListOf<Pattern>())

        var end = pos
        while (end < tokens.size && !(tokens[end] matches TokenPredicate.symbol(setOf(')', '}'))) ){
            if (tokens[end] matches TokenPredicate.symbol('|')){
                parsedPatterns.add(mutableListOf())
                end++
            }else {
                val (pattern, newEnd) =
                    when {
                        tokens[end] matches TokenPredicate.symbol('$') -> parseLookupPattern(tokens, end)
                        tokens[end] matches TokenPredicate.symbol('<') -> parseTokenPattern(tokens, end)
                        tokens[end] matches TokenPredicate.symbol('\\') -> parseCallerPattern(tokens, end)
                        tokens[end] matches TokenPredicate.symbol('(') -> parseBracketedPattern(tokens, end)
                        else -> exception(end, "Unexpected token")
                    }

                val (quantifier, newNewEnd) = parsePatternQuantifier(tokens, newEnd)
                if (!quantifier.isEmpty()) {
                    if (quantifier.isSingle()) {
                        parsedPatterns.last().add(pattern)
                    } else {
                        parsedPatterns.last().add(QuantifiedPattern(pattern, quantifier))
                    }
                }
                end = newNewEnd
            }
        }

        val branchList = parsedPatterns.map{ patternList->
            when(patternList.size){
                0 -> EmptyPattern()
                1 -> patternList[0]
                else -> SequentialPattern(patternList)
            }
        }

        return Pair(
            when(branchList.size){
                1 -> branchList[0]
                else -> BranchedPattern(branchList)
            }, end)
    }
}