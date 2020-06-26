package com.hqurve.parsing.examples

import com.hqurve.parsing.*
import org.apache.commons.math3.special.Gamma
import kotlin.math.*

typealias VariableMap = Map<String, Double>
class SimpleCalculator{
    private val parser = Parser<Double, VariableMap>(Tokenizer(
        includeWhitespaces = false
    )).apply{

        registerMacro("function1", 1, 0, listOf(
            "<label>[%1] <symbol>[(] \\expression <symbol>[)]" to {results, _ -> (results as CompoundResult)[2]}
        ))
        registerMacro("function2", 1, 0, listOf(
            "<label>[%1] <symbol>[(] \\expression <symbol>[,] \\expression <symbol>[)]" to {results, _ ->
                results as CompoundResult
                CompoundResult(results[2], results[4])
            }
        ))

        registerMacro("number", 0, 0, listOf(
            "<number>" to {result, _ -> ValueResult( ((result as TokenResult).token as NumberToken).value.toDouble() )}
        ))

        registerMacro("variable", 0, 0, listOf(
            "<label>" to {result, variables ->
                val variableName = ((result as TokenResult).token as LabelToken).str
                ValueResult(variables[variableName] ?: error("Unknown variable/function/constant : $variableName"))
            }
        ))

        registerMacro("constant", 0, 0, listOf(
            "<label>[pi]" to {_, _ -> ValueResult(Math.PI)},
            "<label>[e]" to {_, _ -> ValueResult(Math.E)}
        ))

        registerMacro("func", 0, 0, listOf(
            "\\function1[sin]" to {result, _ -> ValueResult( sin((result as ValueResult).value))},
            "\\function1[cos]" to {result, _ -> ValueResult( cos((result as ValueResult).value))},
            "\\function1[tan]" to {result, _ -> ValueResult( tan((result as ValueResult).value))},
            "\\function1[exp]" to {result, _ -> ValueResult( exp((result as ValueResult).value))},
            "\\function1[log]" to {result, _ -> ValueResult( log((result as ValueResult).value, Math.E))},
            "\\function2[logN]" to {results, _ ->
                results as CompoundResult
                ValueResult( log((results[0] as ValueResult).value, (results[1] as ValueResult).value))
            },
            "\\function1[gamma]" to {result, _ -> ValueResult( Gamma.gamma((result as ValueResult).value))},
            "\\function1[floor]" to {result, _ -> ValueResult( floor((result as ValueResult).value) )},
            "\\function1[ceil]" to {result, _ -> ValueResult( ceil((result as ValueResult).value) )},
            "\\function1[round]" to {result, _ -> ValueResult( round((result as ValueResult).value) )},
            "<symbol>[|] \\expression <symbol>[|]" to {results, _ ->
                results as CompoundResult
                ValueResult( abs((results[1] as ValueResult).value))
            }
        ))

        registerMacro("value", 0, 0, listOf(
            "\\number | \\constant | \\func | \\variable" to {result, _ -> result},
            "<symbol>[(] \\expression <symbol>[)]" to {result, _ -> (result as CompoundResult)[1]}
        ))

        registerMacro("unaryOperator", 0, 0, listOf(
            "<symbol>[-] \\value" to {result, _ -> ValueResult(-((result as CompoundResult)[1] as ValueResult).value)},
            "\\value <symbol>[!]" to {result, _ -> ValueResult( Gamma.gamma(((result as CompoundResult)[0] as ValueResult).value + 1) )},
            "\\value" to {result, _ -> result}
        ))

        registerMacro("powExpression", 0, 0, listOf(
            "\\unaryOperator (<symbol>[^] \\unaryOperator)*" to {result, _ ->
                result as CompoundResult
                var calc = (result[0] as ValueResult).value
                for (subResult in result[1] as CompoundResult){
                    subResult as CompoundResult
                    calc = calc.pow((subResult[1] as ValueResult).value)
                }
                ValueResult(calc)
            }
        ))

        registerMacro("multiExpression", 0, 0, listOf(
            "\\powExpression ( (<symbol>[*] | <symbol>[/] | <symbol>[\\%]) \\powExpression)*" to {result, _ ->
                result as CompoundResult
                var calc = (result[0] as ValueResult).value
                for (subResult in result[1] as CompoundResult){
                    subResult as CompoundResult
                    val value = (subResult[1] as ValueResult).value
                    when( ((subResult[0] as TokenResult).token as SymbolToken).sym){
                        '*' -> calc *= value
                        '/' -> calc /= value
                        '%' -> calc %= value
                        else -> error("how did we get here? (Unknown multi operator)")
                    }
                }
                ValueResult(calc)
            }
        ))

        registerMacro("expression", 0, 0, listOf(
            "\\multiExpression ( (<symbol>[+] | <symbol>[-]) \\multiExpression)*" to {result, _ ->
                result as CompoundResult
                var calc = (result[0] as ValueResult).value
                for (subResult in result[1] as CompoundResult){
                    subResult as CompoundResult
                    val value = (subResult[1] as ValueResult).value
                    when( ((subResult[0] as TokenResult).token as SymbolToken).sym){
                        '+' -> calc += value
                        '-' -> calc -= value
                        else -> error("how did we get here? (Unknown plus/minus operator)")
                    }
                }
                ValueResult(calc)
            }
        ))


        checkCompletion()
    }


    fun calculate(expression: String, variables: VariableMap) = (parser.parse(expression, "expression", variables) as ValueResult?)?.value
}


fun main(){
    val variables = mapOf<String, Number>(
        "x" to 10.0,
        "delta" to 0.00001
    ).mapValues{(_, value) -> value.toDouble()}

    val expressions = listOf(
        "x + 15",
        "x!",
        "4 + 4 - 2^-3",
        "logN(5 ^ -10, 5)",
        "(1 + delta)^(1/delta)",
        "( (1 + e^(x+delta)) - (1 + e^x) ) / delta",
        "( sin(x + delta) - sin(x) )/delta"
    )
    val calculator = SimpleCalculator()

    for (expression in expressions){
        println("$expression -> ${calculator.calculate(expression, variables)}")
    }
}