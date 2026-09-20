// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.io.File

private fun expand(dicLines: List<String>, affLines: List<String>): Set<String> {
  val dictionary = DicFileParser.parse(dicLines)
  val affixes = AffixFileParser.parse(affLines)
  return HunspellExpander.expand(dictionary, affixes).toSet()
}

class HunspellExpanderTest : FunSpec({
  test("A stem with no flags produces only itself") {
    expand(listOf("1", "casa"), emptyList()) shouldContainExactlyInAnyOrder listOf("casa")
  }

  test("A suffix rule fires when its condition matches the stem's end") {
    val forms = expand(
      listOf("1", "gato/A"),
      listOf("SFX A Y 1", "SFX A   0     s        [^s]"),
    )
    forms shouldContainExactlyInAnyOrder listOf("gato", "gatos")
  }

  test("A suffix rule does not fire when its condition fails") {
    val forms = expand(
      listOf("1", "pires/A"),
      listOf("SFX A Y 1", "SFX A   0     s        [^s]"),
    )
    forms shouldContainExactlyInAnyOrder listOf("pires")
  }

  test("Strip removes characters from the stem before adding, per the condition's own length") {
    // SFX D o a [^ã]o: strips only "o" (1 char) though the condition looks at 2 characters.
    val forms = expand(
      listOf("1", "limo/D"),
      listOf("SFX D Y 1", "SFX D   o     a        [^ã]o"),
    )
    forms shouldContain "lima"
  }

  test("Strip length exactly equal to the stem's length still fires the rule, one shorter does not") {
    val forms = expand(
      listOf("2", "ar/A", "a/A"),
      listOf("SFX A Y 1", "SFX A   ar    o        ."),
    )
    // "ar" is exactly as long as the strip: the rule fires and yields "o". "a" is one shorter: no rule fires.
    forms shouldContainExactlyInAnyOrder listOf("ar", "a", "o")
  }

  test("A prefix rule adds at the start of the stem") {
    val forms = expand(
      listOf("1", "fazer/A"),
      listOf("PFX A Y 1", "PFX A   0     des      ."),
    )
    forms shouldContainExactlyInAnyOrder listOf("fazer", "desfazer")
  }

  test("Prefix and suffix cross product fires when both classes are cross-product Y") {
    val forms = expand(
      listOf("1", "fazer/AB"),
      listOf(
        "PFX A Y 1",
        "PFX A   0     des      .",
        "SFX B Y 1",
        "SFX B   0     mos      r",
      ),
    )
    forms shouldContain "desfazermos"
  }

  test("No cross product when the suffix class is not cross-product") {
    val forms = expand(
      listOf("1", "fazer/AB"),
      listOf(
        "PFX A Y 1",
        "PFX A   0     des      .",
        "SFX B N 1",
        "SFX B   0     mos      r",
      ),
    )
    forms shouldNotContain "desfazermos"
    forms shouldContainExactlyInAnyOrder listOf("fazer", "desfazer", "fazermos")
  }

  test("No cross product when the prefix class is not cross-product") {
    val forms = expand(
      listOf("1", "fazer/AB"),
      listOf(
        "PFX A N 1",
        "PFX A   0     des      .",
        "SFX B Y 1",
        "SFX B   0     mos      r",
      ),
    )
    forms shouldNotContain "desfazermos"
  }

  test("The one extra affixation level also applies to a prefix+suffix cross product") {
    val forms = expand(
      listOf("1", "fazer/AB"),
      listOf(
        "PFX A Y 1",
        "PFX A   0     des      .",
        "SFX B Y 1",
        "SFX B   0     mos/C    r",
        "SFX C Y 1",
        "SFX C   0     zinho    s",
      ),
    )
    // fazer -[B]-> fazermos, cross product with A -> desfazermos, then B's continuation flag C applies
    // one more level to THAT combined word specifically (not just to the plain suffixed "fazermos").
    forms shouldContain "desfazermoszinho"
  }

  test("A continuation flag applies one more level of affixation to the derived form") {
    val forms = expand(
      listOf("1", "casar/A"),
      listOf(
        "SFX A Y 1",
        "SFX A   r     0/B      [aei]r",
        "SFX B Y 1",
        "SFX B   0     mente    a",
      ),
    )
    // casar -[A]-> casa -[B, via A's continuation flag]-> casamente
    forms shouldContain "casa"
    forms shouldContain "casamente"
  }

  test("Continuation flags never recurse past one extra level") {
    val forms = expand(
      listOf("1", "casar/A"),
      listOf(
        "SFX A Y 1",
        "SFX A   r     0/B      [aei]r",
        "SFX B Y 1",
        "SFX B   0     mente/C  a",
        "SFX C Y 1",
        "SFX C   0     zinho    e",
      ),
    )
    // casa -[B]-> casamente carries C as ITS continuation flag, but C is never applied: that would be
    // a second extra level. This also proves the algorithm terminates on a chain, not just a cycle.
    forms shouldContain "casamente"
    forms shouldNotContain "casamentezinho"
  }

  test("A continuation flag naming an undeclared class is simply inert") {
    val forms = expand(
      listOf("1", "casar/A"),
      listOf("SFX A Y 1", "SFX A   r     0/Z      [aei]r"),
    )
    forms shouldContain "casa"
  }

  test("A self-referencing continuation flag terminates: no unbounded loop even if it matched forever") {
    val forms = expand(
      listOf("1", "casar/A"),
      listOf("SFX A Y 1", "SFX A   r     0/A      [aei]r"),
    )
    // "casa" does not itself end in "[aei]r", so this would terminate either way; the point is that
    // expand() returns at all, and produces nothing beyond the one legitimate derivation.
    forms shouldContainExactlyInAnyOrder listOf("casar", "casa")
  }

  test("FORBIDDENWORD drops exactly the flagged occurrence") {
    val forms = expand(listOf("1", "ague/F"), listOf("FORBIDDENWORD F"))
    forms shouldNotContain "ague"
  }

  test("NOSUGGEST drops exactly the flagged occurrence") {
    val forms = expand(listOf("1", "palavraobscura/N"), listOf("NOSUGGEST N"))
    forms shouldNotContain "palavraobscura"
  }

  test("FORBIDDENWORD on the stem does not block its other, unrelated derived forms") {
    val forms = expand(
      listOf("1", "teste/FA"),
      listOf(
        "FORBIDDENWORD F",
        "SFX A Y 1",
        "SFX A   0     s        .",
      ),
    )
    forms shouldNotContain "teste"
    forms shouldContain "testes"
  }

  test("A continuation flag can mark only the derived form as forbidden, keeping the base") {
    val forms = expand(
      listOf("1", "estado/A"),
      listOf(
        "FORBIDDENWORD F",
        "SFX A Y 1",
        "SFX A   0     zinho/F  .",
      ),
    )
    forms shouldContain "estado"
    forms shouldNotContain "estadozinho"
  }

  test("Real dictionary: common inflections and a base word are all found, a nonsense string is not") {
    val dicFile = File("src/lexicon/pt_BR.dic")
    val affFile = File("src/lexicon/pt_BR.aff")
    val dictionary = DicFileParser.parse(dicFile.readLexiconLines())
    val affixes = AffixFileParser.parse(affFile.readLexiconLines())

    // The unfiltered expansion briefly explodes into tens of millions of candidates (mostly hyphenated
    // clitic combinations off the verb suffix class alone, see HunspellExpander's kdoc), which the
    // production pipeline never materializes because it filters candidate by candidate. This test only
    // needs a handful of specific words, so it checks membership on the fly instead of collecting
    // everything into a Set first, which would multiply both the time and the memory of this test by
    // the size of that discarded explosion for no benefit.
    val expectedPresent = setOf("casa", "casas", "cantávamos", "falaremos", "ações", "pêssegos", "limo", "via")
    val foundPresent = mutableSetOf<String>()
    var sawAbsentProbe = false
    for (candidate in HunspellExpander.expand(dictionary, affixes)) {
      if (candidate in expectedPresent) foundPresent += candidate
      if (candidate == "xyzw") sawAbsentProbe = true
    }

    foundPresent shouldContainExactlyInAnyOrder expectedPresent
    sawAbsentProbe shouldBe false
  }
})
