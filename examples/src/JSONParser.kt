package com.hqurve.parsing.examples

import com.hqurve.parsing.*
import kotlin.math.pow


/*
    Example of json parser using specification from https://tools.ietf.org/html/rfc4627 (and http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf)
    Note that for brevity sake, this example is very liberal in terms of the specification. Additionally, the result would not be strongly typed.

    This parser accepts both arrays and objects as the top object. The entryPoint is jsonString.
    Note that both the object and array quantifiers are possessive allowing for greater performance.
 */




class JSONParser{
    private val parser = Parser<Any?, Any?>(Tokenizer(
        includeWhitespaces = false,
        captureDecimalNumbers = true,
        resolveEscapedStringCharacters = false,
        labelsHaveDigits = false
    )).apply{
        registerMacro("number", 0, 0, listOf(
            "<symbol>[-]? <number> (<label>[e] (<symbol>[+] | <symbol>[-])? <number>)?" to {results, _ ->
                results as CompoundResult

                val isMinus = (results[0] as CompoundResult).isNotEmpty()
                val number = ((results[1] as TokenResult).token as NumberToken).value

                val exponent =
                    if ((results[2] as CompoundResult).isEmpty()){
                        null
                    }else{
                        val innerResult = (results[2] as CompoundResult)[0] as CompoundResult
                        val exp = ((innerResult[2] as TokenResult).token as NumberToken).value as Long
                        val isNegated = (innerResult[1] as CompoundResult).run{
                            isNotEmpty() && (first() as TokenResult).token matches TokenPredicate.symbol('-')
                        }

                        if (isNegated) -exp else exp
                    }

                ValueResult(
                    if (exponent == null){
                        if (number is Long){
                           number * (if (isMinus) -1 else 1)
                        }else{
                            (number as Double) * (if (isMinus) -1 else 1)
                        }
                    }else{
                        number.toDouble() * (if (isMinus) -1 else 1) * 10.0.pow(exponent.toDouble())
                    }
                )
            }
        ))

        registerMacro("string", 0, 0, listOf(
            "<string>[strong]" to {result, _->
                val escapedString = ((result as TokenResult).token as StringToken).str
                ValueResult(decodeEscapedString(escapedString))
            }
        ))

        registerMacro("kvPair", 0, 0, listOf(
            "\\string <symbol>[:] \\value" to {results, _ ->
                results as CompoundResult
                ValueResult((results[0] as ValueResult).value as String to (results[2] as ValueResult).value)
            }
        ))
        registerMacro("object", 0, 0, listOf(
            "<symbol>[{] <symbol>[}]" to {_, _ -> ValueResult(emptyMap<String, Any?>())},
            "<symbol>[{] \\kvPair (<symbol>[,] \\kvPair)* <symbol>[}]" to {results, _ ->
                results as CompoundResult
                val firstResult = (results[1] as ValueResult).value

                val trailingResults = (results[2] as CompoundResult).map{subResult->
                    subResult as CompoundResult
                    (subResult[1] as ValueResult).value
                }
                @Suppress("UNCHECKED_CAST")
                ValueResult((listOf(firstResult) + trailingResults).map{ it as Pair<String, Any?> }.toMap())
            }
        ))

        registerMacro("array", 0, 0, listOf(
            "<symbol>[[] <symbol>[\\]]" to {_, _ -> ValueResult(emptyArray<Any?>())},
            "<symbol>[[] \\value (<symbol>[,] \\value)* <symbol>[\\]]" to {results, _->
                results as CompoundResult
                val firstResult = (results[1] as ValueResult).value

                val trailingResults = (results[2] as CompoundResult).map{subResult->
                    subResult as CompoundResult
                    (subResult[1] as ValueResult).value
                }
                ValueResult(listOf(firstResult) + trailingResults)
            }
        ))

        registerMacro("value", 0, 0, listOf(
            "<label>[null]" to {_, _ -> ValueResult(null)},
            "<label>[true]" to {_, _ -> ValueResult(true)},
            "<label>[false]" to {_, _ -> ValueResult(false)},
            "\\object | \\array | \\number | \\string" to {result, _ -> result}
        ))

        registerMacro("head", 0, 0, listOf(
            "\\object | \\array" to {result, _ -> result}
        ))

        checkCompletion()
    }

    private fun decodeEscapedString(escapedString: String): String{
        val sb = StringBuilder()

        var index = 0
        while(index < escapedString.length){
            if (escapedString[index] in '\u0000'..'\u001F' || escapedString[index] == '\"') throw CharacterCodingException()
            if (escapedString[index] == '\\'){
                if (index + 1 == escapedString.length) throw CharacterCodingException()
                sb.append(when(escapedString[index+1]){
                    '\"' -> '\"'
                    '\\' -> '\\'
                    '/'  -> '/'
                    'b' -> '\u0008'
                    'f' -> '\u000C'
                    'n' -> '\u000A'
                    'r' -> '\u000D'
                    't' -> '\u0009'
                    'u' ->{
                        if (index + 6 > escapedString.length) throw CharacterCodingException()
                        val codePoint = escapedString.substring(index + 2, index + 6).toInt()
                        Character.toChars(codePoint)[0]
                        index += 4
                    }
                    else -> throw CharacterCodingException()
                })
                index += 2
            }else{
                sb.append(escapedString[index])
                index++
            }
        }
        return sb.toString()
    }


    fun parse(jsonString: String) = (parser.parse(jsonString,"head" ,null) as ValueResult?)?.value

    @Suppress("UNCHECKED_CAST")
    fun parseObject(jsonString: String) = (parser.parse(jsonString,"object" ,null) as ValueResult?)?.value as Map<String, Any?>?

    @Suppress("UNCHECKED_CAST")
    fun parseArray(jsonString: String) = (parser.parse(jsonString,"array" ,null) as ValueResult?)?.value as List<Any?>?
}


fun main(){
    val jsonParser = JSONParser()

    jsonParser.run{
        println(parse("""
            {
                "name": "hquvre",
                "friends": ["john", "carl", "carlos"],
                "age": 19,
                "occupation":{
                    "title": "student",
                    "type": "undergrad",
                    "degree": "mathematics",
                    "year": 1
                },
                "is happy": true,
                "gender": "male",
                "message": "\n
                I created this general parsing engine (named \"Parser\").\n
                By using this engine, it was very easy for me to create a jsonparser using a simple set of macros.\n
                Moreover, a large portion of the above code is decyphering the encoded numbers and strings which cannot be avoided.\n
                By using this engine, all pattern matching tasks are easily taken care of and allowed the jsonparser to be created quickly.\n
                Of course, a static parser would most likely be quicker than this parser but it is still quite fast. (I still have to do testing though)\n
                But the parser pre-compiles the patterns for each of the macros and reuses matchers allowing it to be quite quick after the first few runs.
                "
            }
        """.replace(Regex("\\s*\\n\\s*"), "")))
        println(parse("""
            [
            {"score": 12.5e2, "name":"player1", "max-level": 502}
            ]
        """.replace(Regex("\\s*\\n\\s*"), "")))
    }
}