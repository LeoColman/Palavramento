#!/usr/bin/env kotlin
// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

// Test strength of the last mutation run in each module (ADR 0023): mutants killed, out of the mutants
// some test actually reached. It is the number PIT prints as "Test strength", and the one the README
// badge shows. Mutants nothing reaches are a coverage gap, already visible elsewhere; this one asks
// whether the tests that do run would notice the code being wrong.
//
//   ./gradlew pitest                                   # or one module: ./gradlew :domain:pitest
//   kotlin tools/mutation-strength.main.kts            # prints the table
//   kotlin tools/mutation-strength.main.kts --badge mutation.json
//
// Reads each module's build/reports/pitest/mutations.xml, so a module that was not run shows the date
// of whatever report is lying there instead: an old number is not presented as a fresh one.

import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import kotlin.system.exitProcess

val modules = listOf("domain", "server", "app")

data class Score(val module: String, val detected: Int, val covered: Int, val total: Int, val date: String)

fun score(module: String): Score? {
  val report = File("$module/build/reports/pitest/mutations.xml")
  if (!report.exists()) return null
  val mutations = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report)
    .getElementsByTagName("mutation")
  val all = (0 until mutations.length).map { mutations.item(it) as Element }
  val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(report.lastModified()).atZone(ZoneId.systemDefault()))
  return Score(
    module = module,
    detected = all.count { it.getAttribute("detected") == "true" },
    covered = all.count { it.getAttribute("status") != "NO_COVERAGE" },
    total = all.size,
    date = date,
  )
}

fun percent(detected: Int, covered: Int): Int = if (covered == 0) 0 else (detected * 100.0 / covered).roundToInt()

// Same bands shields.io uses for coverage, so the colour means what readers expect it to.
fun colour(strength: Int): String = when {
  strength >= 90 -> "brightgreen"
  strength >= 80 -> "green"
  strength >= 70 -> "yellowgreen"
  strength >= 60 -> "yellow"
  else -> "orange"
}

val scores = modules.map { it to score(it) }
val found = scores.mapNotNull { it.second }
if (found.isEmpty()) {
  System.err.println("No mutations.xml in any module. Run ./gradlew pitest first.")
  exitProcess(1)
}

println("| Módulo | Força dos testes | Mortos / alcançados | Mutantes | Relatório de |")
println("|---|---|---|---|---|")
for ((module, s) in scores) {
  if (s == null) {
    println("| `:$module` | sem relatório | | | |")
  } else {
    println("| `:$module` | ${percent(s.detected, s.covered)}% | ${s.detected} / ${s.covered} | ${s.total} | ${s.date} |")
  }
}
val overall = percent(found.sumOf { it.detected }, found.sumOf { it.covered })
println("| **total** | **$overall%** | ${found.sumOf { it.detected }} / ${found.sumOf { it.covered }} | ${found.sumOf { it.total }} | |")

val badgeIndex = args.indexOf("--badge")
if (badgeIndex >= 0) {
  val target = File(args.getOrNull(badgeIndex + 1) ?: error("--badge needs a file name"))
  // shields.io endpoint schema: https://shields.io/badges/endpoint-badge
  target.writeText(
    """{"schemaVersion":1,"label":"força dos testes (mutação)","message":"$overall%","color":"${colour(overall)}"}""" + "\n",
  )
}
