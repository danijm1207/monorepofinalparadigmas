package edu.investigacion.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsTest {

    @Test
    @DisplayName("Rng genera valores congruenciales idénticos a la referencia Python")
    void rngDeterministicSequence() {
        Rng rng = new Rng(42);
        long v1 = rng.next();
        long v2 = rng.next();
        long v3 = rng.next();

        // Valores verificados contra referencia Python
        assertThat(v1).isEqualTo(1083814273L);
        assertThat(v2).isEqualTo(378494188L);
        assertThat(v3).isEqualTo(2479403867L);

        Rng rngInt = new Rng(42);
        int i1 = rngInt.nextInt(10);
        int i2 = rngInt.nextInt(100);

        assertThat(i1).isEqualTo(9);
        assertThat(i2).isEqualTo(92);
    }

    @Test
    @DisplayName("Escenario T produce exactamente las líneas canónicas de golden/T.txt")
    void scenarioTMatchesGoldenLines() throws IOException {
        var data = ScenarioGenerator.generate("T", 42);
        List<String> lines = AnalyticsPipeline.run(data.cases(), data.suspects(), data.evidences());

        Path goldenPath = Path.of("../../shared/golden/T.txt");
        if (Files.exists(goldenPath)) {
            List<String> goldenLines = Files.readAllLines(goldenPath);
            assertThat(lines).containsExactlyElementsOf(goldenLines);
        }

        String cs = ReportFormatter.computeChecksum(lines);
        assertThat(cs).isEqualTo("bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e");
    }

    @Test
    @DisplayName("Escenario S genera checksum esperado")
    void scenarioSChecksumMatch() {
        var data = ScenarioGenerator.generate("S", 42);
        List<String> lines = AnalyticsPipeline.run(data.cases(), data.suspects(), data.evidences());
        String cs = ReportFormatter.computeChecksum(lines);
        assertThat(cs).isEqualTo("a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9");
    }

    @Test
    @DisplayName("Escenario M genera checksum esperado")
    void scenarioMChecksumMatch() {
        var data = ScenarioGenerator.generate("M", 42);
        List<String> lines = AnalyticsPipeline.run(data.cases(), data.suspects(), data.evidences());
        String cs = ReportFormatter.computeChecksum(lines);
        assertThat(cs).isEqualTo("0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4");
    }
}
