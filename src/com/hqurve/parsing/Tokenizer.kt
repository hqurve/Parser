package com.hqurve.parsing

interface Token{

    companion object{
        val tokenClasses = listOf(
            WhitespaceToken::class,
            LabelToken::class,
            StringToken::class,
            NumberToken::class,
            SymbolToken::class
        )
//        internal val tokenClassParsers = tokenClasses.map{kClass->
//            val companionObject = kClass.companionObjectInstance
//
//            @Suppress("UNCHECKED_CAST")
//            val function = kClass.companionObject!!.memberFunctions.single { it.name == "fromString" } as KFunction<Pair<Token, Int>?>
//
//
//            {s: String, i: Int -> function.call(companionObject, s, i)}
//        }
    }
}

class TokenizerException(pos: Int, message: String): RuntimeException("Token Exception at $pos: $message")

class Tokenizer{
    companion object{
        fun exception(pos: Int, message: String): Nothing = throw TokenizerException(pos, message)
    }
    fun tokenize(string: String): List<Token>{
        val list = mutableListOf<Token>()

        var end = 0
        while (end < string.length) {
            val ch = string[end]

            val pair = when{
                ch.isWhitespace() -> WhitespaceToken.fromString(string, end)
                ch.isLetter() ->LabelToken.fromString(string, end)
                ch.let{it == '\'' || it == '\"'} -> StringToken.fromString(string, end)
                ch.isDigit() -> NumberToken.fromString(string, end)
                else -> SymbolToken.fromString(string, end)
            }

            list.add(pair.first)
            end = pair.second
        }

        if (end != string.length) exception(end, "Unable to parse past here")
        return list.toList()
    }
}

//class TokenizedTerm(val tokens: List<Token>){
//    companion object{
//        internal fun fromString(string: String, pos: Int): Pair<TokenizedTerm, Int>{
//            val list = mutableListOf<Token>()
//
//            var end = pos
//            while (end < string.length){
//                var pair: Pair<Token, Int>? = null
//                for (parser in Token.tokenClassParsers){
//                    pair = parser(string, end)?: continue
//                    break
//                }
//                if (pair == null) break
//
//                if (pair.first !is WhitespaceToken) {
//                    list.add(pair.first)
//                }
//                end = pair.second
//            }
//
//            return TokenizedTerm(list.toList()) to end
//        }
//    }
//}

data class StringToken(val str: String, val mode: Modes): Token{
    enum class Modes{STRONG, WEAK}

    companion object{
        internal fun fromString(string: String, pos: Int): Pair<StringToken, Int>{
            val (modeChar, mode) = when(string[pos]){
                '\'' -> '\'' to Modes.WEAK
                '\"' -> '\"' to Modes.STRONG
                else -> Tokenizer.exception(pos, "Invalid StringToken")
            }

            var endPos = pos + 1
            while(true){
                if (endPos == string.length) Tokenizer.exception(endPos, "Unexpected StringToken end")
                if (string[endPos] == modeChar) break
                if (string[endPos] == '\\'){
                    endPos++
                    if (endPos == string.length) Tokenizer.exception(endPos, "Badly escaped character in StringToken")
//                    if (string[endPos] != modeChar && string[endPos] != '\\') return null
                    endPos++
                }else{
                    endPos++
                }
            }

            return StringToken(string.substring(pos+1, endPos).replace(Regex("\\\\(.)"), "$1"), mode) to endPos + 1
        }
    }
}
data class NumberToken(val value: Number, val mode: Modes): Token{
    enum class Modes{INTEGER, DECIMAL}

    companion object{
        internal fun fromString(string: String, pos: Int): Pair<NumberToken, Int>{
            var end = pos

            while (end < string.length && string[end].isDigit()){
                end++
            }

            if (end == pos) Tokenizer.exception(pos, "Empty number")

            val mode: Modes
            val value: Number
            if (end + 1 < string.length && string[end] == '.' && string[end+1].isDigit()){
                mode = Modes.DECIMAL
                end++
                while (end < string.length && string[end].isDigit()){
                    end++
                }
                value = string.substring(pos, end).toDouble()
            }else{
                mode = Modes.INTEGER
                value = string.substring(pos, end).toLong()
            }

            return NumberToken(value, mode) to end
        }
    }
}
data class SymbolToken(val sym: Char): Token{

    companion object{
        private val SYMBOL_SET = """
            !   ~   &   ^
            ${'$'}   %   #   @
            =   +   -   *
            /   \   |   _
            ;   :   ?   ,
            .
            [   {   (   <
            ]   }   )   >
        """.replace(Regex("\\s+"), "").toSet()
//        init{
//            print("Symbol set: $SYMBOL_SET")
//        }
        internal fun fromString(string: String, pos: Int): Pair<SymbolToken, Int>{
            if (string[pos] !in SYMBOL_SET)Tokenizer.exception(pos, "Invalid symbol")
            return SymbolToken(string[pos]) to pos + 1
        }
    }
}
data class LabelToken(val str: String): Token{

    companion object{
        internal fun fromString(string: String, pos: Int): Pair<LabelToken, Int>{
            if (!string[pos].isLetter()) Tokenizer.exception(pos, "Invalid LabelToken")
            var end = pos + 1

            while (end < string.length && string[end].isLetterOrDigit()){
                end++
            }

            return LabelToken(string.substring(pos, end)) to end
        }
    }
}
data class WhitespaceToken(val str: String): Token{

    companion object{
        internal fun fromString(string: String, pos: Int): Pair<WhitespaceToken, Int>{
            var end = pos
            while (end < string.length && string[end].isWhitespace()){
                end++
            }

            if (end == pos) Tokenizer.exception(pos, "Empty whitespace")
            return WhitespaceToken(string.substring(pos, end)) to end
        }
    }
}
//class BracketedToken(val term: TokenizedTerm, val mode: Modes): Token{
//    enum class Modes(chars: String){
//        CURVED("()"),
//        SQUARE("[]"),
//        CURLY("{}");
//
//        val opening = chars[0]
//        val closing = chars[1]
//    }
//    override fun matches(any: Any) = mode.toString().toLowerCase() == any
//    companion object{
//        internal fun fromString(string: String, pos: Int): Pair<BracketedToken, Int>?{
//            val mode = when(string[pos]){
//                '(' -> Modes.CURVED
//                '[' -> Modes.SQUARE
//                '{' -> Modes.CURLY
//                else-> return null
//            }
//            if (pos + 1 == string.length) return null
//
//            val (term, end) = TokenizedTerm.fromString(string, pos+ 1)
//            if (end == string.length || string[end] != mode.closing) return null
//
//            return BracketedToken(term, mode) to end + 1
//        }
//    }
//}