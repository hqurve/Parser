package com.hqurve.parsing

import org.apache.commons.math3.complex.Complex
import org.junit.jupiter.api.Test

operator fun Int.times(z: Complex): Complex = Complex(this.toDouble()) * z
operator fun Int.div(z: Complex): Complex = Complex(this.toDouble()) / z
operator fun Complex.plus(z: Complex):Complex = this.add(z)
operator fun Complex.minus(z: Complex): Complex = this.subtract(z)
operator fun Complex.times(z: Complex): Complex = this.multiply(z)
operator fun Complex.div(z: Complex): Complex = this.divide(z)
operator fun Complex.unaryMinus(): Complex = this * Complex(-1.0)

fun gamma(z: Complex): Complex{
    return z.pow(z - Complex(0.5)) * (-z).exp() * Complex(Math.PI * 2).sqrt() * (
            Complex.ONE + 1/(12 * z) + 1/(288 * z.pow(2.0)) - 139/(51840 * z.pow(3.0)) - 571/(2488320 * z.pow(4.0))
        )
}


internal class ParserTest{
    @Test
    fun parseTokenMatcher(){
        val parser = Parser<Complex, Any?>(Tokenizer(includeWhitespaces = false)).apply{
            registerMacro("function1", 1, 0, listOf(
                "<label>[%1] <symbol>[(] \\expression <symbol>[)]" to {results, _ -> (results as CompoundResult)[2]}
            ))

            registerMacro("number", 0, 0, listOf(
                "<number>" to {results, _ ->
                    val value = results as TokenResult<*>
                    ValueResult(
                        Complex((value.token as NumberToken).value.toDouble())
                    )
                },
                "<number><label>[i]" to {results, _ ->
                    val value = (results as CompoundResult<*>)[0] as TokenResult<*>
                    ValueResult(
                        Complex(0.0, (value.token as NumberToken).value.toDouble())
                    )
                },
                "<label>[i]" to {_, _ -> ValueResult(Complex(0.0, 1.0))}
            ))


            registerMacro("constant", 0, 0, listOf(
                "<label>[pi]" to {_, _ -> ValueResult(Complex(Math.PI))},
                "<label>[e]" to {_, _ -> ValueResult(Complex(Math.E))}
            ))
            registerMacro("func", 0, 0, listOf(
                "\\function1[sin]" to {results, _-> ValueResult((results as ValueResult).value.sin()) },
                "\\function1[cos]" to {results, _-> ValueResult((results as ValueResult).value.cos()) },
                "<symbol>[|] \\expression <symbol>[|]" to {results, _ ->
                    results as CompoundResult<Complex>
                    ValueResult(
                        Complex((results[1] as ValueResult<Complex>).value.abs())
                    )
                },
                "\\function1[gamma]" to {results, _ -> ValueResult(gamma(
                    (results as ValueResult<Complex>).value
                ))}
            ))

            registerMacro("value", 0, 0, listOf(
                "<symbol>[(] \\expression <symbol>[)]" to {results, _ -> (results as CompoundResult)[1]},
                "\\constant" to {results, _ -> results},
                "\\number" to {results, _ -> results},
                "\\func" to {results, _ -> results}
            ))

            registerMacro("negValue", 0, 0, listOf(
                "<symbol>[-]? \\value" to {results, _ ->
                    results as CompoundResult
                    if ((results[0] as CompoundResult).isEmpty()){
                        results[1]
                    }else{
                        ValueResult( (results[1] as ValueResult).value.multiply(-1))
                    }
                }
            ))


            registerMacro("powExpression", 0, 0, listOf(
                "\\negValue (<symbol>[^] \\negValue)*" to {results, _ ->
                    results as CompoundResult<Complex>

                    var calc = (results[0] as ValueResult<Complex>).value
                    for (subResult in (results[1] as CompoundResult<Complex>)) {
                        subResult as CompoundResult<Complex>
                        val value = (subResult[1] as ValueResult).value
                        calc = calc.pow(value)
                    }
                    ValueResult(calc)
                }
            ))
            registerMacro("multExpression", 0, 0, listOf(
                "\\powExpression ((<symbol>[*] | <symbol>[/]) \\powExpression)*" to {results, _->
                    results as CompoundResult<Complex>

                    var calc = (results[0] as ValueResult<Complex>).value
                    for (subResult in (results[1] as CompoundResult<Complex>)) {
                        subResult as CompoundResult<Complex>
                        val value = (subResult[1] as ValueResult).value
                        when (((subResult[0] as TokenResult<*>).token as SymbolToken).sym) {
                            '*' -> calc = calc.multiply(value)
                            '/' -> calc = calc.divide(value)
                        }
                    }
                    ValueResult(calc)
                }
            ))
            registerMacro("expression", 0, 0, listOf(
                "\\multExpression ((<symbol>[+] | <symbol>[-]) \\multExpression)*" to { results, _ ->
                    results as CompoundResult<Complex>

                    var calc = (results[0] as ValueResult<Complex>).value
                    for (subResult in (results[1] as CompoundResult<Complex>)) {
                        subResult as CompoundResult<Complex>
                        val value = (subResult[1] as ValueResult).value
                        when (((subResult[0] as TokenResult<*>).token as SymbolToken).sym) {
                            '+' -> calc = calc.add(value)
                            '-' -> calc = calc.subtract(value)
                        }
                    }
                    ValueResult(calc)
                }
            ))

            checkCompletion()
        }

        val result = parser.parse("gamma(1+2i)", "expression", null)
        print(result)

    }
}