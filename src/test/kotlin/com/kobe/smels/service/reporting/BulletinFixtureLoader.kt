package com.kobe.smels.service.reporting

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

object BulletinFixtureLoader {
    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    fun load(name: String): JsonNode {
        val candidates = listOf(
            Path.of("docs/fixtures/$name.json"),
            Path.of("../docs/fixtures/$name.json"),
            Path.of("back-office/docs/fixtures/$name.json")
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("Fixture not found: $name.json (cwd=${Path.of("").toAbsolutePath()})")
        return mapper.readTree(Files.readString(path))
    }

    fun studentSubjects(root: JsonNode): GradeCalculationEngine.StudentSubjects {
        val subjects = mutableListOf<GradeCalculationEngine.SubjectInput>()
        root.path("groups").forEachIndexed { gIdx, group ->
            val groupCode = group.path("groupCode").asString()
            val groupName = group.path("groupName").asString()
            group.path("lines").forEach { line ->
                val marksNode = line.path("sequenceMarks")
                val marks = (0 until marksNode.size()).map { i ->
                    val n = marksNode[i]
                    if (n == null || n.isNull) null else BigDecimal(n.asString())
                }
                subjects += GradeCalculationService.subjectFromFixtureLine(
                    groupCode = groupCode,
                    groupName = groupName,
                    groupDisplayOrder = group.path("displayOrder").asInt(gIdx + 1),
                    subjectCode = line.path("subjectCode").asString(),
                    subjectName = line.path("subjectName").asString(),
                    coefficient = BigDecimal(line.path("coefficient").asString()),
                    sequenceMarks = marks,
                    teacherName = line.path("teacherCode").asString(null),
                    competency = line.path("competency").asString(null)
                )
            }
        }
        return GradeCalculationEngine.StudentSubjects("fixture-student", subjects)
    }

    fun schemeFromFixture(root: JsonNode): GradeCalculationEngine.SchemeParams {
        val observed = root.path("gradingSchemeObserved")
        val passMark = BigDecimal(observed.path("passMark").asString("10"))
        val threshold = BigDecimal(observed.path("classSuccessThreshold").asString("12"))
        val effort = if (observed.has("effortSubjectThreshold")) {
            BigDecimal(observed.path("effortSubjectThreshold").asString())
        } else BigDecimal("8")
        val letterBands = listOf(
            GradeCalculationEngine.ScoreBandInput("C", BigDecimal.ZERO, BigDecimal("12")),
            GradeCalculationEngine.ScoreBandInput("B", BigDecimal("12"), BigDecimal("14")),
            GradeCalculationEngine.ScoreBandInput("B+", BigDecimal("14"), BigDecimal("16")),
            GradeCalculationEngine.ScoreBandInput("A", BigDecimal("16"), null)
        )
        val competencyBands = listOf(
            GradeCalculationEngine.ScoreBandInput("NA", BigDecimal.ZERO, BigDecimal("10")),
            GradeCalculationEngine.ScoreBandInput("ECA", BigDecimal("10"), BigDecimal("14")),
            GradeCalculationEngine.ScoreBandInput("A", BigDecimal("14"), null)
        )
        val scales = listOf(
            GradeCalculationEngine.ScaleInput("LETTER", letterBands),
            GradeCalculationEngine.ScaleInput("COMPETENCY", competencyBands)
        )
        return GradeCalculationEngine.SchemeParams(
            passMark = passMark,
            classSuccessThreshold = threshold,
            roundTermSubjectAverages = true,
            effortSubjectThreshold = effort,
            appreciationScales = scales,
            overallMentionBands = listOf(
                GradeCalculationEngine.ScoreBandInput("WEAK", BigDecimal.ZERO, BigDecimal("9")),
                GradeCalculationEngine.ScoreBandInput("MEDIOCRE", BigDecimal("9"), BigDecimal("10")),
                GradeCalculationEngine.ScoreBandInput("FAIR", BigDecimal("10"), BigDecimal("12")),
                GradeCalculationEngine.ScoreBandInput("ASSEZ_BON", BigDecimal("12"), BigDecimal("14")),
                GradeCalculationEngine.ScoreBandInput("BON", BigDecimal("14"), BigDecimal("16")),
                GradeCalculationEngine.ScoreBandInput("TRES_BON", BigDecimal("16"), BigDecimal("18")),
                GradeCalculationEngine.ScoreBandInput("EXCELLENT", BigDecimal("18"), null)
            )
        )
    }
}
