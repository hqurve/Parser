package com.hqurve.parsing

infix fun Token?.matches(tokenPredicate: TokenPredicate) = tokenPredicate.matches(this)


class TokenPredicate private constructor(
    vararg comparableProperties: Any?,
    val matcherFun: (token: Token?)->Boolean
){
    val properties = comparableProperties
    fun matches(token: Token?) = matcherFun(token)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenPredicate

        if (!properties.contentEquals(other.properties)) return false

        return true
    }

    override fun hashCode(): Int {
        return properties.contentHashCode()
    }


    companion object{
        fun any() = TokenPredicate(Token::class){it is Token}
        fun empty() = TokenPredicate(null){it == null}
        fun exact(requiredToken: Token) = TokenPredicate(requiredToken){it == requiredToken}

        fun whitespace() = TokenPredicate(WhitespaceToken::class){it is WhitespaceToken}

        fun label() = TokenPredicate(LabelToken::class){it is LabelToken}


        fun string() = TokenPredicate(StringToken::class){it is StringToken}
        fun string(mode: StringToken.Modes) = TokenPredicate(StringToken::class, mode){it is StringToken && it.mode == mode}

        fun number() = TokenPredicate(NumberToken::class){it is NumberToken}
        fun number(mode: NumberToken.Modes) = TokenPredicate(NumberToken::class, mode){it is NumberToken && it.mode == mode}

        fun number(lowerBound: Long, upperBound: Long) = TokenPredicate(NumberToken::class, lowerBound, upperBound){
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Int, upperBound: Int) = TokenPredicate(NumberToken::class, lowerBound, upperBound){
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Double, upperBound: Double) = TokenPredicate(NumberToken::class, lowerBound, upperBound){
            it is NumberToken
                    && it.value.toDouble() in lowerBound..upperBound
        }

        fun symbol() = TokenPredicate(SymbolToken){it is SymbolToken}
        fun symbol(sym: Char) = exact(SymbolToken(sym))
        fun symbol(syms: Collection<Char>): TokenPredicate{
            val symbolSet = syms.toSet()
            return TokenPredicate(SymbolToken, symbolSet){
                it is SymbolToken
                        && it.sym in symbolSet
            }
        }

        private val TOKEN_PREDICATE_GENERATORS: Map<String, List<Pair< List<TokenPredicate>, (List<Token?>) -> TokenPredicate >>>
        fun genTokenPredicate(keyWord: String, arguments: List<Token?>): TokenPredicate{
            val generators = TOKEN_PREDICATE_GENERATORS[keyWord.toLowerCase()] ?: error("Invalid keyword: $keyWord")

            val (_, generator) =  generators.firstOrNull{ (patterns, _) ->
                    arguments.size == patterns.size
                        &&
                        arguments.zip(patterns).all{ (arg, pattern) -> arg matches pattern}
            }?: error("Invalid arguments for $keyWord")

            return generator(arguments)
        }
        init{
            TOKEN_PREDICATE_GENERATORS = mapOf(
                "any" to listOf(
                    emptyList<TokenPredicate>() to { _ -> whitespace()}
                ),
                "whitespace" to listOf(
                    emptyList<TokenPredicate>() to { _ -> whitespace() }
                ),
                "label" to listOf(
                    emptyList<TokenPredicate>() to { _ -> label() },

                    listOf(label()) to {args -> exact(args[0] as LabelToken)}
                ),

                "string" to listOf(
                    emptyList<TokenPredicate>() to { _ -> string() },

                    listOf(label()) to {args ->
                        string(
                            StringToken.Modes.valueOf((args[0] as LabelToken).str.toUpperCase())
                        )
                    }
                ),

                "number" to listOf(
                    emptyList<TokenPredicate>() to { _ -> number()},

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
                    emptyList<TokenPredicate>() to { _ -> symbol()},

                    listOf(symbol()) to {args -> exact(args[0] as SymbolToken)}
                )
            )
        }
    }
}