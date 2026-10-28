package com.smoh.showmeoutfit.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two domain invariants that are cheapest to break and most expensive to fix later:
 * framework purity (JVM-testable) and localization purity (no display formatting in domain).
 */
class DomainPurityTest {

    private val domainDir = File("src/main/java/com/smoh/showmeoutfit/domain")

    private fun domainSources(): List<File> {
        assertTrue("Domain source directory not found: ${domainDir.absolutePath}", domainDir.isDirectory)
        return domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `domain does not import Android or AndroidX`() {
        val violations = domainSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) ->
                    val trimmed = line.trim()
                    trimmed.startsWith("import android.") ||
                        trimmed.startsWith("import androidx.")
                }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }

        assertTrue("Android imports found in domain: $violations", violations.isEmpty())
    }

    @Test
    fun `domain does not format user-facing text`() {
        // `"...".format("arg")` and NumberFormat are the easy ways to smuggle formatting past a
        // check that only looks for `String.format(` and `Locale.`.
        val forbidden = listOf(
            "String.format(",
            ".format(",
            "NumberFormat",
            "SimpleDateFormat",
            "java.util.Locale",
            "Locale.",
            "DateTimeFormatter",
            "getString(",
            "R.string"
        )
        val violations = domainSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) ->
                    val trimmed = line.trim()
                    // Comments are allowed to mention the banned APIs.
                    !trimmed.startsWith("//") &&
                        !trimmed.startsWith("*") &&
                        forbidden.any { trimmed.contains(it) }
                }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }

        assertTrue("Display formatting found in domain: $violations", violations.isEmpty())
    }
}
