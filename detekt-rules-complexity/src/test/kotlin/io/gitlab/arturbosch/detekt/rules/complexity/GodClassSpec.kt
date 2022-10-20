package io.gitlab.arturbosch.detekt.rules.complexity

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class GodClassSpec(private val env: KotlinCoreEnvironment) {


    val config = TestConfig(
        mapOf(
            "" to "**Dummy*.kt",
            Config.EXCLUDES_KEY to "**/library/**"
        )
    )

    val subject = GodClass(Config.empty)

    @Test
    fun test() {
        val code = """
            data class Test(val a: Int = 5)            

            class Stack(val size: Int, test: Int) {
                val array = 0
                val top = 0

                val bla = Test()

                
                fun isEmpty() {
                    if(top < 5) {
                        if(bla.a < 5){
                            println(top)
                        }
                    }
                }
                fun size() {
                    println(size)
                }
                fun vTop() {
                    println(array + top)
                }
                fun push() {
                    println(top + size + array)
                }
                fun pop() {
                    println(top + bla.a)            
                }
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }
}
