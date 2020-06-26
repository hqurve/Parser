package com.hqurve.parsing


interface Result<T>
data class CompoundResult<T>(val subResults: List<Result<T>>): Result<T>, List<Result<T>>{
    constructor(vararg subs: Result<T>): this(subs.toList())
    override val size: Int
        get() = subResults.size

    override fun contains(element: Result<T>) = subResults.contains(element)
    override fun containsAll(elements: Collection<Result<T>>) = subResults.containsAll(elements)
    override fun get(index: Int) = subResults[index]
    override fun indexOf(element: Result<T>) = subResults.indexOf(element)
    override fun isEmpty() = subResults.isEmpty()
    override fun iterator() = subResults.iterator()
    override fun lastIndexOf(element: Result<T>) = subResults.lastIndexOf(element)
    override fun listIterator() = subResults.listIterator()
    override fun listIterator(index: Int) = subResults.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int) = subResults.subList(fromIndex, toIndex)
}
data class ValueResult<T>(val value: T): Result<T>
data class TokenResult<T>(val token: Token): Result<T>


class Parser<T, F>(private val tokenizer: Tokenizer = Tokenizer()){

    private inner class ResultHandler(val handler: (Result<T>, F)->Result<T> = {result, _ -> result}){
        fun handle(result: Result<T>, flags: F) = handler(result, flags)
    }

    private abstract inner class Matcher{
        abstract fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance
    }
    private abstract inner class MatcherInstance(protected val tokens: List<Token>, val pos: Int){
        //Initializer code would make first match
        abstract val end: Int?

        fun isMatching() = end != null
        abstract fun tryAgain() //updates the end value

        abstract fun getResult(flags: F): Result<T>
    }

    private inner class EmptyMatcher: Matcher() {
        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            override var end: Int? = pos
                private set
            override fun tryAgain(){
                end = null
            }
            override fun getResult(flags: F) = CompoundResult<T>(emptyList())
        }
        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }

    }
    private inner class TokenMatcher(val type: String, val args: List<Token?>): Matcher(){
        val tokenPredicate = TokenPredicate.genTokenPredicate(type, args)

        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            override var end: Int? = if (pos < tokens.size && tokens[pos] matches tokenPredicate) pos + 1 else null
                private set

            override fun tryAgain(){
                end = null
            }
            override fun getResult(flags: F) = TokenResult<T>(tokens[pos])
        }

        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.TokenMatcher

            if (type != other.type) return false
            if (args != other.args) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + args.hashCode()
            return result
        }


    }
    private inner class LazyCallerMatcher(val macroName: String, val tokenArguments: List<Token?>, val matcherArguments: List<Matcher>): Matcher(){
        val internalCallerMatcher: Matcher by lazy{
            createMatcherFromTemplate(macroName, tokenArguments, matcherArguments)
        }

        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            private val internalInstance = internalCallerMatcher.createInstance(tokens, pos)

            override val end: Int?
                get() = internalInstance.end

            override fun tryAgain() = internalInstance.tryAgain()

            override fun getResult(flags: F) = internalInstance.getResult(flags)
        }

        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.LazyCallerMatcher

            if (macroName != other.macroName) return false
            if (tokenArguments != other.tokenArguments) return false
            if (matcherArguments != other.matcherArguments) return false

            return true
        }

        override fun hashCode(): Int {
            var result = macroName.hashCode()
            result = 31 * result + tokenArguments.hashCode()
            result = 31 * result + matcherArguments.hashCode()
            return result
        }


    }
    private inner class SequentialMatcher(val subMatchers: List<Matcher>): Matcher(){
        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            private val subInstances = mutableListOf<MatcherInstance>()
            private var state = 0

            override var end: Int? = null
                private set
            init{
                subInstances.add( subMatchers[0].createInstance(tokens, pos))
                performTest()
            }
            fun performTest(){
                while(subInstances.isNotEmpty() && state < subMatchers.size){
                    if (subInstances.last().isMatching()){
                        state++
                        if (state < subMatchers.size){
                            subInstances += subMatchers[state].createInstance(tokens, subInstances.last().end!!)
                        }
                    }else{
                        state--
                        subInstances.removeAt(subInstances.lastIndex)
                        if (state >= 0){
                            subInstances.last().tryAgain()
                        }
                    }
                }

                end =
                    if (state == -1){
                        null
                    }else{
                        subInstances.last().end
                    }
            }

            override fun tryAgain() {
                if (!isMatching()) return

                state--
                subInstances.last().tryAgain()

                performTest()
            }

            override fun getResult(flags: F) = CompoundResult(subInstances.map{it.getResult(flags)})


        }

        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.SequentialMatcher

            if (subMatchers != other.subMatchers) return false

            return true
        }

        override fun hashCode(): Int {
            return subMatchers.hashCode()
        }


    }

    private inner class BranchedMatcher(val subMatchers: List<Matcher>): Matcher(){
        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            var currentSubInstance: MatcherInstance?
            var nextSubInstance = 1

            override var end: Int? = null
                private set

            init{
                currentSubInstance = subMatchers[0].createInstance(tokens, pos)

                performTest()
            }

            private fun performTest(){
                while (!currentSubInstance!!.isMatching() && nextSubInstance < subMatchers.size){
                    currentSubInstance = subMatchers[nextSubInstance].createInstance(tokens, pos)
                    nextSubInstance++
                }
                if (currentSubInstance!!.isMatching()){
                    end = currentSubInstance!!.end
                }else{
                    currentSubInstance = null
                    end = null
                }
            }

            override fun tryAgain() {
                if (!isMatching()) return

                currentSubInstance!!.tryAgain()
                performTest()
            }

            override fun getResult(flags: F) = currentSubInstance!!.getResult(flags)
        }

        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.BranchedMatcher

            if (subMatchers != other.subMatchers) return false

            return true
        }

        override fun hashCode(): Int {
            return subMatchers.hashCode()
        }


    }
    
    
    private inner class QuantifiedMatcher(val subMatcher: Matcher, val quantifier: PatternQuantifier): Matcher(){
        
        private inner class GreedyInstance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos) {
            val subInstances = mutableListOf<MatcherInstance>()
            override var end: Int? = null
                private set

            init{
                subInstances.add(subMatcher.createInstance(tokens, pos))

                performTest()
            }

            fun findNextBranch(){
                while(subInstances.isNotEmpty() && !subInstances.last().isMatching()){
                    subInstances.removeAt(subInstances.lastIndex)
                    subInstances.lastOrNull()?.tryAgain()
                }
            }
            fun performTest(){
                while(true){
                    findNextBranch()

                    if (subInstances.isNotEmpty()) {
                        while (subInstances.size < quantifier.max && subInstances.last().isMatching()) {
                            subInstances.add(subMatcher.createInstance(tokens, subInstances.last().end!!))
                        }
                        if (!subInstances.last().isMatching()) {
                            subInstances.removeAt(subInstances.lastIndex)
                        }
                    }

                    if (subInstances.size in quantifier.min .. quantifier.max){
                        end = subInstances.lastOrNull()?.end ?: pos
                        break
                    }else if (subInstances.isEmpty()){
                        end = null
                        break
                    }else{
                        subInstances.last().tryAgain()
                    }
                }
            }


            override fun tryAgain() {
                if (!isMatching()) return

                if (subInstances.isEmpty() || quantifier.mode == PatternQuantifier.Mode.POSSESSIVE){
                    end = null
                }else{
                    subInstances.last().tryAgain()
                    performTest()
                }
            }

            override fun getResult(flags: F) = CompoundResult( subInstances.map{it.getResult(flags)} )
        }
        private inner class ReluctantInstance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos) {
            val subInstances = mutableListOf<MatcherInstance>()
            override var end: Int? = null
                private set

            init{
                if (quantifier.min == 0){
                    end = pos
                }else{
                    performTest()
                }
            }

            fun findNextBranch(){
                while(subInstances.isNotEmpty() && !subInstances.last().isMatching()){
                    subInstances.removeAt(subInstances.lastIndex)
                    subInstances.lastOrNull()?.tryAgain()
                }
            }

            fun performTest(){
                while(true) {
                    if (subInstances.size == quantifier.max) {
                        subInstances.last().tryAgain()
                    }else{
                        subInstances.add(subMatcher.createInstance(tokens, subInstances.lastOrNull()?.end ?: pos))
                    }

                    findNextBranch()

                    if (subInstances.isEmpty()){
                        end = null
                        break
                    }else if (subInstances.size in quantifier.min .. quantifier.max){
                        end = subInstances.last().end
                        break
                    }
                }
            }

            override fun tryAgain() {
                if (!isMatching()) return

                performTest()
            }

            override fun getResult(flags: F) = CompoundResult( subInstances.map{it.getResult(flags)} )
        }

        override fun createInstance(tokens: List<Token>, pos: Int)
            = when(quantifier.mode){
                PatternQuantifier.Mode.RELUCTANT -> ReluctantInstance(tokens, pos)
                else -> GreedyInstance(tokens, pos)
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.QuantifiedMatcher

            if (subMatcher != other.subMatcher) return false
            if (quantifier != other.quantifier) return false

            return true
        }

        override fun hashCode(): Int {
            var result = subMatcher.hashCode()
            result = 31 * result + quantifier.hashCode()
            return result
        }
    }

    private inner class CustomHandlerMatcher(val matcher: Matcher, val handler: ResultHandler): Matcher(){
        private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance(tokens, pos){
            val instance = matcher.createInstance(tokens, pos)

            override val end: Int?
                get() = instance.end

            override fun tryAgain() = instance.tryAgain()

            override fun getResult(flags: F) = handler.handle(instance.getResult(flags), flags)
        }
        override fun createInstance(tokens: List<Token>, pos: Int) = Instance(tokens, pos)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Parser<*, *>.CustomHandlerMatcher

            if (matcher != other.matcher) return false
            if (handler != other.handler) return false

            return true
        }

        override fun hashCode(): Int {
            var result = matcher.hashCode()
            result = 31 * result + handler.hashCode()
            return result
        }


    }
    private fun createMatcher(pattern: Pattern, tokenArguments: List<Token?>, matcherArguments: List<Matcher>): Matcher{
        return when(pattern){
            is EmptyPattern -> EmptyMatcher()
            is ArgumentPattern -> matcherArguments[pattern.index - 1]
            is TokenPattern -> TokenMatcher(pattern.type, pattern.arguments.map{
                    when(it){
                        is ArgumentTemplateToken -> tokenArguments[it.index - 1]
                        is LiteralTemplateToken -> it.token
                        else -> error("Unknown Template token: $it")
                    }
                })
            is CallerPattern ->
                LazyCallerMatcher(pattern.macroName,
                    pattern.tokenArguments.map{
                        when(it){
                            is ArgumentTemplateToken -> tokenArguments[it.index - 1]
                            is LiteralTemplateToken -> it.token
                            else -> error("Unknown Template token: $it")
                        }
                    },
                    pattern.patternArguments.map{
                        createMatcher(it, tokenArguments, matcherArguments)
                    }
                )
            is SequentialPattern -> SequentialMatcher(pattern.subPatterns.map{createMatcher(it, tokenArguments, matcherArguments)})
            is BranchedPattern -> BranchedMatcher(pattern.subPatterns.map{createMatcher(it, tokenArguments, matcherArguments)})
            is QuantifiedPattern -> QuantifiedMatcher(createMatcher(pattern.subPattern, tokenArguments, matcherArguments), pattern.quantifier)
            else -> error("Unknown pattern: $pattern")

        }
    }
    private fun createMatcherFromTemplate(macroName: String, tokenArguments: List<Token?>, matcherArguments: List<Matcher>): Matcher{

        val cachedMatcher = cachedMatcherMap[Triple(macroName, tokenArguments, matcherArguments)]
        if (cachedMatcher != null){
            return cachedMatcher
        }


        val matcherTemplate = registeredTemplateMap[MatcherTemplateSignature(macroName, tokenArguments.size, matcherArguments.size)]!!

        val matcher = BranchedMatcher(matcherTemplate.patternHandlerPairs.map{ (pattern, handler)->
            CustomHandlerMatcher(createMatcher(pattern, tokenArguments, matcherArguments), handler)
        })

        cachedMatcherMap[Triple(macroName, tokenArguments, matcherArguments)] = matcher

        return matcher
    }

    private data class MatcherTemplateSignature(val name: String, val tokenArgumentCount: Int, val matcherArgumentCount: Int)
    private inner class MatcherTemplate(val signature: MatcherTemplateSignature, val patternHandlerPairs: List<Pair<Pattern, ResultHandler>>){
        val referencedMatcherSignatures: Set<MatcherTemplateSignature>

        init{//verifying inputs
            val requiredTokenArguments = mutableSetOf<Int>()
            val requiredMatcherArguments = mutableSetOf<Int>()
            val requiredMatcherSignatures = mutableSetOf<MatcherTemplateSignature>() //macroName -> tokenArgCount, matcherArgCount

            fun recursiveTraceThrough(pattern: Pattern){
                when (pattern){
                    is ArgumentPattern -> requiredMatcherArguments.add(pattern.index)
                    is TokenPattern -> requiredTokenArguments.addAll(pattern.arguments.mapNotNull{(it as? ArgumentTemplateToken)?.index})
                    is CallerPattern ->{
                        requiredTokenArguments.addAll(pattern.tokenArguments.mapNotNull{(it as? ArgumentTemplateToken)?.index})
                        pattern.patternArguments.forEach{recursiveTraceThrough(it)}

                        requiredMatcherSignatures.add(MatcherTemplateSignature(
                            pattern.macroName, pattern.tokenArguments.size, pattern.patternArguments.size
                        ))
                    }
                    is SequentialPattern -> pattern.subPatterns.forEach{recursiveTraceThrough(it)}
                    is BranchedPattern -> pattern.subPatterns.forEach{recursiveTraceThrough(it)}
                    is QuantifiedPattern -> recursiveTraceThrough(pattern.subPattern)
                }
            }
            for ((pattern, _) in patternHandlerPairs){
                recursiveTraceThrough(pattern)
            }

            if (requiredTokenArguments.any { it > signature.tokenArgumentCount }){
                error("MatcherTemplate '${signature.name}' states that it requires ${signature.tokenArgumentCount} tokenArguments but its patterns reference indices beyond that ${requiredTokenArguments.filter{ it > signature.tokenArgumentCount}}")
            }
            if (requiredMatcherArguments.any { it > signature.matcherArgumentCount }){
                error("MatcherTemplate '${signature.name}' states that it requires ${signature.matcherArgumentCount} tokenArguments but its patterns reference indices beyond that ${requiredMatcherArguments.filter{ it > signature.matcherArgumentCount}}")
            }

            referencedMatcherSignatures = requiredMatcherSignatures.toSet()
        }
    }



    private val patternParser = PatternParser()
    private val registeredTemplateMap = mutableMapOf<MatcherTemplateSignature, MatcherTemplate>()
    private val cachedMatcherMap = mutableMapOf<Triple<String, List<Token?>, List<Matcher>>, Matcher>()


    fun checkCompletion(){
        val requirementList = mutableListOf<Pair<MatcherTemplateSignature, MatcherTemplate>>()
        for (matcherTemplate in registeredTemplateMap.values){
            requirementList.addAll( matcherTemplate.referencedMatcherSignatures.map{it to matcherTemplate} )
        }

        val requirementMap = requirementList.groupBy({it.first}, {it.second})

          val missingMap = requirementMap.filterKeys { it !in registeredTemplateMap }

    if (missingMap.isNotEmpty()){
        error("Missing the following signatures used by: $missingMap")
    }
}

    fun registerMacro(macroName: String, tokenArgumentCount: Int, matcherArgumentCount: Int, patternHandlerPairs: List<Pair<String, (Result<T>, F) -> Result<T>>>){
        if (patternHandlerPairs.isEmpty()) error("Empty patternHandlerPair list")
        val signature = MatcherTemplateSignature(macroName, tokenArgumentCount, matcherArgumentCount)
        if (signature in registeredTemplateMap){
            error("Macro already registered with signature $macroName($tokenArgumentCount, $matcherArgumentCount)")
        }

        registeredTemplateMap[signature] = MatcherTemplate(signature, patternHandlerPairs.map{(patternString, handler) ->
            patternParser.parse(patternString) to ResultHandler(handler)
        })
    }

    fun parse(string: String, entryPoint: String, flags: F): Result<T>?{
        val tokens = tokenizer.tokenize(string)

        val matcherInstance = createMatcherFromTemplate(entryPoint, emptyList(), emptyList()).createInstance(tokens, 0)

        while (matcherInstance.isMatching() && matcherInstance.end!! < tokens.size){
            matcherInstance.tryAgain()
        }
        return when(matcherInstance.isMatching()){
            true -> matcherInstance.getResult(flags)
            false -> null
        }
    }
}