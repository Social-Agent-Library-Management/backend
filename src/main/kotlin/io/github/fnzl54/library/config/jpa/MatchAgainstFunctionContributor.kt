package io.github.fnzl54.library.config.jpa

import org.hibernate.boot.model.FunctionContributions
import org.hibernate.boot.model.FunctionContributor
import org.hibernate.type.StandardBasicTypes

class MatchAgainstFunctionContributor : FunctionContributor {
    override fun contributeFunctions(functionContributions: FunctionContributions) {
        val doubleType =
            functionContributions
                .typeConfiguration
                .basicTypeRegistry
                .resolve(StandardBasicTypes.DOUBLE)

        functionContributions
            .functionRegistry
            .patternDescriptorBuilder(
                "match_against",
                "match(?1, ?2) against (?3 in boolean mode)",
            ).setExactArgumentCount(3)
            .setInvariantType(doubleType)
            .register()
    }
}
