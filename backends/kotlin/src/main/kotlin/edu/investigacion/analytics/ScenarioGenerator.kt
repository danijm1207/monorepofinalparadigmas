package edu.investigacion.analytics

object ScenarioGenerator {
    private const val BASE_DAY = 19358L // (date(2023, 1, 1) - date(1970, 1, 1)).days

    data class ScenarioConfig(val cases: Int, val suspects: Int, val evidences: Int)

    val SCENARIOS = mapOf(
        "T" to ScenarioConfig(20, 60, 200),
        "S" to ScenarioConfig(1000, 3000, 10000),
        "M" to ScenarioConfig(10000, 30000, 100000),
        "L" to ScenarioConfig(50000, 150000, 500000),
        "XL" to ScenarioConfig(100000, 300000, 1000000)
    )

    private val CASE_STATUS_ARRAY = arrayOf(CaseStatus.OPEN, CaseStatus.IN_PROGRESS, CaseStatus.CLOSED)
    private val SUSPECT_STATUS_ARRAY = arrayOf(SuspectStatus.PERSON_OF_INTEREST, SuspectStatus.DETAINED, SuspectStatus.CLEARED)
    private val EVIDENCE_TYPES_ARRAY = arrayOf(EvidenceType.PHYSICAL, EvidenceType.DIGITAL, EvidenceType.DOCUMENT, EvidenceType.TESTIMONY, EvidenceType.FORENSIC)

    fun generate(scenario: String, seed: Long = 42L): Triple<List<CaseRec>, List<SuspectRec>, List<EvidenceRec>> {
        val config = SCENARIOS[scenario] ?: throw IllegalArgumentException("Escenario desconocido: $scenario")
        val rng = Rng(seed)

        val cases = (0 until config.cases).map { i ->
            val st = CASE_STATUS_ARRAY[rng.nextInt(3)]
            val day = BASE_DAY + rng.nextInt(1096).toLong()
            CaseRec(
                id = String.format("C%07d", i),
                status = st,
                incidentEpochDay = day
            )
        }

        val suspects = (0 until config.suspects).map { j ->
            val ci = rng.nextInt(config.cases)
            val sus = rng.nextInt(101).toLong()
            val st = SUSPECT_STATUS_ARRAY[rng.nextInt(3)]
            SuspectRec(
                id = String.format("S%07d", j),
                caseId = String.format("C%07d", ci),
                suspicionLevel = sus,
                status = st
            )
        }

        val evidences = (0 until config.evidences).map {
            val ci = rng.nextInt(config.cases)
            val h = rng.nextInt(100)
            val si = rng.nextInt(config.suspects)
            val t = EVIDENCE_TYPES_ARRAY[rng.nextInt(5)]
            val rel = (1 + rng.nextInt(5)).toLong()
            val day = BASE_DAY + rng.nextInt(1096).toLong()
            val a = rng.nextInt(100)

            val suspectId = if (h < 60) String.format("S%07d", si) else null
            val active = a < 95

            EvidenceRec(
                caseId = String.format("C%07d", ci),
                suspectId = suspectId,
                type = t,
                relevance = rel,
                collectedEpochDay = day,
                active = active
            )
        }

        return Triple(cases, suspects, evidences)
    }
}
