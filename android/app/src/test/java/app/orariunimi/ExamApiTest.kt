package app.orariunimi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamApiTest {
    @Test fun parsesAndSortsPublicExamResponseWithOptionalFields() {
        val body = """
            [{
              "codIns":"FAA0AN",
              "codW4":"FAA-17",
              "descrIns":"ARCHITETTURA DEGLI ELABORATORI I ",
              "appelli":[
                {
                  "dataStr":"22/09/2026",
                  "ora":"08:30",
                  "luogo":"Aula G09",
                  "prova":"Scritto",
                  "docente":{"nome":"NICOLA","cognome":"BASILICO"},
                  "rangeDa":"H","rangeA":"Z",
                  "idAppello":"18463261",
                  "tipoAppello":"Appello",
                  "aperturaStr":"01/08/2026",
                  "chiusuraStr":"15/09/2026"
                },
                {
                  "dataStr":"18/09/2026",
                  "prova":"Laboratorio",
                  "docente":{"nome":"GABRIELLA","cognome":"TRUCCO"},
                  "idAppello":"18462947"
                }
              ]
            }]
        """.trimIndent()

        val appeals = parseExamAppeals(body, "faa")

        assertEquals(listOf("18462947", "18463261"), appeals.map { it.id })
        assertEquals("FAA", appeals.first().courseCode)
        assertEquals("", appeals.first().time)
        assertEquals("", appeals.first().location)
        assertEquals("BASILICO NICOLA", appeals.last().teacher)
        assertEquals(LocalDate.of(2026, 8, 1), appeals.last().registrationOpen)
        assertEquals(LocalDate.of(2026, 9, 15), appeals.last().registrationClose)
    }

    @Test fun ignoresMalformedDatesAndDeduplicatesAppealIds() {
        val body = """
            [{"codIns":"X","descrIns":"Test","appelli":[
              {"dataStr":"20/09/2026","idAppello":"same"},
              {"dataStr":"20/09/2026","idAppello":"same"},
              {"dataStr":"not-a-date","idAppello":"invalid"}
            ]}]
        """.trimIndent()

        val appeals = parseExamAppeals(body, "FAA")

        assertEquals(1, appeals.size)
        assertTrue(appeals.none { it.id == "invalid" })
    }
}
