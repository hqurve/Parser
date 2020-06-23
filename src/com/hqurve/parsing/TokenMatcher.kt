package com.hqurve.parsing

internal infix fun Token?.matches(tokenMatcher: TokenMatcher) = tokenMatcher.matches(this)


internal class TokenMatcher private constructor(
    vararg comparableProperties: Any?,
    val matcherFun: (token: Token?)->Boolean
){
    val properties = comparableProperties
    fun matches(token: Token?) = matcherFun(token)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenMatcher

        if (!properties.contentEquals(other.properties)) return false

        return true
    }

    override fun hashCode(): Int {
        return properties.contentHashCode()
    }


    companion object{
        fun any() = TokenMatcher(Token){it is Token}
        fun empty() = TokenMatcher(null){it == null}
        fun exact(requiredToken: Token) = TokenMatcher(requiredToken){it == requiredToken}

        fun whitespace() = TokenMatcher(WhitespaceToken){it is WhitespaceToken}

        fun label() = TokenMatcher(LabelToken){it is LabelToken}


        fun string() = TokenMatcher(StringToken){it is StringToken}
        fun string(mode: StringToken.Modes) = TokenMatcher(StringToken, mode){it is StringToken && it.mode == mode}

        fun number() = TokenMatcher(NumberToken){it is NumberToken}
        fun number(mode: NumberToken.Modes) = TokenMatcher(NumberToken, mode){it is NumberToken && it.mode == mode}

        fun number(lowerBound: Long, upperBound: Long) = TokenMatcher(NumberToken, lowerBound, upperBound){
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Int, upperBound: Int) = TokenMatcher(NumberToken, lowerBound, upperBound){
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Double, upperBound: Double) = TokenMatcher(NumberToken, lowerBound, upperBound){
            it is NumberToken
                    && it.value.toDouble() in lowerBound..upperBound
        }

        fun symbol() = TokenMatcher(SymbolToken){it is SymbolToken}
        fun symbol(sym: Char) = exact(SymbolToken(sym))
        fun symbol(syms: Collection<Char>): TokenMatcher{
            val symbolSet = syms.toSet()
            return TokenMatcher(SymbolToken, symbolSet){
                it is SymbolToken
                        && it.sym in symbolSet
            }
        }

        private val tokenMatcherGenerators: Map<String, List<Pair< List<TokenMatcher>, (List<Token?>) -> TokenMatcher >>>
        fun genTokenMatcher(keyWord: String, arguments: List<Token?>): TokenMatcher{
            val generators = tokenMatcherGenerators[keyWord.toLowerCase()] ?: error("Invalid keyword: $keyWord")

            val (_, generator) =  generators.firstOrNull{ (patterns, _) ->
                    arguments.size == patterns.size
                        &&
                        arguments.zip(patterns).all{ (arg, pattern) -> arg matches pattern}
            }?: error("Invalid arguments for $keyWord")

            return generator(arguments)
        }
        init{
            tokenMatcherGenerators = mapOf(
                "any" to listOf(
                    emptyList<TokenMatcher>() to {_ -> whitespace()}
                ),
                "whitespace" to listOf(
                    emptyList<TokenMatcher>() to {_ -> whitespace() }
                ),
                "label" to listOf(
                    emptyList<TokenMatcher>() to {_ -> label() },

                    listOf(label()) to {args -> exact(args[0] as LabelToken)}
                ),

                "string" to listOf(
                    emptyList<TokenMatcher>() to {_ -> string() },

                    listOf(label()) to {args ->
                        string(
                            StringToken.Modes.valueOf((args[0] as LabelToken).str.toUpperCase())
                        )
                    }
                ),

                "number" to listOf(
                    emptyList<TokenMatcher>() to {_ -> number()},

                    listOf(number()) to {args -> exact(args[0] as NumberToken)},

                    listOf(label()) to {args ->
                        number(
                            NumberToken.Modes.valueOf((args[0] as LabelToken).str.toUpperCase())
                        )
                    },

                    listOf(number(NumberToken.Modes.INTEGER), number(NumberToken.Modes.INTEGER)) to {args ->
                        number(
                            (args[0] as NumberToken).value as Long,
                            (args[1] as NumberToken).value as Long
                        )
                    },
                    listOf(number(NumberToken.Modes.INTEGER), empty()) to {args->
                        number(
                            (args[0] as NumberToken).value as Long,
                            Long.MAX_VALUE
                        )
                    },
                    listOf(empty(), number(NumberToken.Modes.INTEGER)) to {args->
                        number(
                            0,
                            (args[1] as NumberToken).value as Long
                        )
                    },

                    listOf(number(NumberToken.Modes.DECIMAL), number(NumberToken.Modes.DECIMAL)) to {args ->
                        number(
                            (args[0] as NumberToken).value as Double,
                            (args[1] as NumberToken).value as Double
                        )
                    },
                    listOf(number(NumberToken.Modes.DECIMAL), empty()) to {args->
                        number(
                            (args[0] as NumberToken).value as Double,
                            Double.MAX_VALUE
                        )
                    },
                    listOf(empty(), number(NumberToken.Modes.DECIMAL)) to {args->
                        number(
                            0.0,
                            (args[1] as NumberToken).value as Double
                        )
                    }
                ),

                "symbol" to listOf(
                    emptyList<TokenMatcher>() to {_ -> symbol()},

                    listOf(symbol()) to {args -> exact(args[0] as SymbolToken)}
                )
            )
        }
    }
}