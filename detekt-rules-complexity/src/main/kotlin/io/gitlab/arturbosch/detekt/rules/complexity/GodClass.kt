package io.gitlab.arturbosch.detekt.rules.complexity

import io.github.detekt.metrics.CyclomaticComplexity
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Metric
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.Configuration
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.descriptors.impl.referencedProperty
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getOwnerForEffectiveDispatchReceiverParameter

/**
 * This rule reports God Classes
 *
 * <noncompliant>
 * </noncompliant>
 *
 * <compliant>
 * </compliant>
 */
@RequiresTypeResolution
class GodClass(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        "GodClass",
        Severity.Maintainability,
        "God Class Description",
        Debt.TWENTY_MINS
    )

    @Configuration("WMC")
    private val weightedMethodCountThreshold: Int by config(47)

    @Configuration("ATFD")
    private val accessToForeignDataThreshold: Int by config(5)

    //@Configuration("TCC")
    private val tightClassCohesionThreshold: Float = 1 / 3f

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        val className = klass.fqName ?: return
        val methodsInClass = klass.declarations.filterIsInstance<KtNamedFunction>().also {
            if(it.isEmpty()) return
        }
        val variableFqNamesInClass = getFqNamesOfAllPropertiesInKlass(klass)

        val methodsToAccessedProperties = mapMethodFqNameToAccessedLocalPropertiesFqNames(
            methodsInClass,
            variableFqNamesInClass
        )
        val tcc = calculateTightClassCohesion(methodsToAccessedProperties)

        val wmc = methodsInClass.sumOf { CyclomaticComplexity.calculate(it) }

        val atfd = methodsInClass.sumOf { getForeignDataProvidersUsedInBlock(it.bodyBlockExpression!!, className).size }

        println("BREAKPOINT PRINTLN")

        if (
            wmc >= weightedMethodCountThreshold &&
            tcc < tightClassCohesionThreshold &&
            atfd > accessToForeignDataThreshold
        ) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(klass.identifyingElement ?: klass),
                    message = issue.description,
                    metrics = listOf(
                        Metric("WMC", wmc, threshold = weightedMethodCountThreshold),
                        Metric("TCC", tcc, tightClassCohesionThreshold.toDouble()),
                        Metric("ATFD", atfd, accessToForeignDataThreshold)
                    )
                )
            )
        }
    }

    private fun mapMethodFqNameToAccessedLocalPropertiesFqNames(
        methodsInClass: List<KtNamedFunction>,
        variableFqNamesInClass: List<FqName>
    ): Map<FqName, List<FqName>> =
        methodsInClass.mapNotNull { method ->
            val methodFqName = method.fqName ?: return@mapNotNull null
            methodFqName to method.collectDescendantsOfType<KtNameReferenceExpression>()
                .mapNotNull { it.getResolvedCall(bindingContext)?.resultingDescriptor?.referencedProperty?.fqNameSafe }
                .filter { variableFqNamesInClass.contains(it) }
                .distinct()
        }.associate { it }

    private fun calculateTightClassCohesion(methodsToAccessedProperties: Map<FqName, List<FqName>>): Double {
        val methods = methodsToAccessedProperties.keys.toList()
        var pairs = 0
        val maxNumOfMethodPairs = calculateMaxNumOfMethodPairs(methods.size)

        for (i in methods.indices) {
            for (j in (i + 1) until methods.size) {
                val accessesOfFirst = methodsToAccessedProperties[methods[i]]
                val accessesOfSecond = methodsToAccessedProperties[methods[j]]
                val combined = mutableSetOf<FqName>()
                combined.addAll(accessesOfFirst!!.toTypedArray())
                combined.addAll(accessesOfSecond!!.toTypedArray())
                if (combined.size < (accessesOfFirst.size + accessesOfSecond.size)) {
                    pairs++
                }
            }
        }
        return pairs / maxNumOfMethodPairs.toDouble()
    }

    private fun calculateMaxNumOfMethodPairs(numOfMethodsInClass: Int) =
        numOfMethodsInClass * (numOfMethodsInClass - 1) / 2

    private fun getFqNamesOfAllPropertiesInKlass(klass: KtClass): List<FqName> {
        return klass.primaryConstructorParameters.mapNotNull { it.fqName }.toMutableList().apply {
            addAll(klass.getProperties().mapNotNull { it.fqName })
        }
    }

    private fun getForeignDataProvidersUsedInBlock(block: KtBlockExpression, containerName: FqName): List<FqName> =
        block.collectDescendantsOfType<KtNameReferenceExpression>().mapNotNull { reference ->
            // This removes all ReferenceExpressions to functions etc.
            reference.getResolvedCall(bindingContext)?.resultingDescriptor?.referencedProperty
        }.filter {
            // Constants not considered ATFD
            !it.isConst
        }.map {
            it.getOwnerForEffectiveDispatchReceiverParameter()?.fqNameSafe
        }.filter {
            it != containerName
        }.filterNotNull().distinct()
}
