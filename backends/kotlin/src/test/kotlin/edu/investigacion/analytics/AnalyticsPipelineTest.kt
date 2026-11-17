package edu.investigacion.analytics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalyticsPipelineTest {

    @Test
    fun `rng genera secuencia congruencial exacta`() {
        val rng = Rng(42L)
        val expectedFirst5 = listOf(1083814273L, 378494188L, 2479403867L, 955863294L, 1613448261L)
        val actual = (0 until 5).map { rng.next() }
        assertThat(actual).isEqualTo(expectedFirst5)
    }

    @Test
    fun `escenario T coincide con lineas canonicas y checksum esperado`() {
        val (cases, suspects, evidences) = ScenarioGenerator.generate("T", 42L)
        val report = AnalyticsPipeline.run(cases, suspects, evidences)
        val lines = ReportFormatter.formatLines(report)
        val chk = ReportFormatter.checksum(lines)

        assertThat(chk).isEqualTo("bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e")
        assertThat(lines).hasSize(67)
        assertThat(lines[0]).isEqualTo("totals|valid=159|score=5701|maxScore=75|casesWithEvidence=20")
        assertThat(lines[1]).isEqualTo("type|PHYSICAL|count=29|score=860")
        assertThat(lines[2]).isEqualTo("type|DIGITAL|count=22|score=1020")
        assertThat(lines[3]).isEqualTo("type|DOCUMENT|count=31|score=824")
        assertThat(lines[4]).isEqualTo("type|TESTIMONY|count=36|score=732")
        assertThat(lines[5]).isEqualTo("type|FORENSIC|count=41|score=2265")
    }

    @Test
    fun `escenario S coincide con checksum esperado`() {
        val (cases, suspects, evidences) = ScenarioGenerator.generate("S", 42L)
        val report = AnalyticsPipeline.run(cases, suspects, evidences)
        val lines = ReportFormatter.formatLines(report)
        val chk = ReportFormatter.checksum(lines)

        assertThat(chk).isEqualTo("a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9")
    }

    @Test
    fun `escenario M coincide con checksum esperado`() {
        val (cases, suspects, evidences) = ScenarioGenerator.generate("M", 42L)
        val report = AnalyticsPipeline.run(cases, suspects, evidences)
        val lines = ReportFormatter.formatLines(report)
        val chk = ReportFormatter.checksum(lines)

        assertThat(chk).isEqualTo("0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4")
    }
}
