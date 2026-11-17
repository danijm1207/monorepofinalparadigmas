package edu.investigacion.analytics

object ScenarioGenerator:

  val Scenarios: Map[String, (Int, Int, Int)] = Map(
    "T" -> (20, 60, 200),
    "S" -> (1000, 3000, 10000),
    "M" -> (10000, 30000, 100000),
    "L" -> (50000, 150000, 500000),
    "XL" -> (100000, 300000, 1000000)
  )

  def generate(
      scenario: String,
      seed: Long = 42L
  ): (Vector[CaseRec], Vector[SuspectRec], Vector[EvidenceRec]) =
    val (c, s, e) = Scenarios.getOrElse(
      scenario,
      throw new IllegalArgumentException(s"Escenario no reconocido: $scenario")
    )
    val rng = new Rng(seed)

    val cases = (0 until c).map { i =>
      val st = CaseStatus.fromOrdinal(rng.nextInt(3))
      val day = AnalyticsConstants.BaseEpochDay + rng.nextInt(1096).toLong
      CaseRec(f"C$i%07d", st, day)
    }.toVector

    val suspects = (0 until s).map { j =>
      val ci = rng.nextInt(c)
      val caseId = f"C$ci%07d"
      val susp = rng.nextInt(101).toLong
      val st = SuspectStatus.fromOrdinal(rng.nextInt(3))
      SuspectRec(f"S$j%07d", caseId, susp, st)
    }.toVector

    val evidences = (0 until e).map { _ =>
      val ci = rng.nextInt(c)
      val caseId = f"C$ci%07d"
      val h = rng.nextInt(100)
      val si = rng.nextInt(s)
      val suspectId = if h < 60 then Some(f"S$si%07d") else None
      val t = EvidenceType.fromOrdinal(rng.nextInt(5))
      val rel = (1 + rng.nextInt(5)).toLong
      val day = AnalyticsConstants.BaseEpochDay + rng.nextInt(1096).toLong
      val a = rng.nextInt(100)
      val active = a < 95
      EvidenceRec(caseId, suspectId, t, rel, day, active)
    }.toVector

    (cases, suspects, evidences)
