package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.record.Csv
import com.eirahoutmoss.nextools.core.record.Downsample
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun numbersAreLocaleIndependent() {
        assertEquals("12.34", Csv.number(12.34))
        assertEquals("5", Csv.number(5.0))
        assertEquals("0", Csv.number(-0.00001))
        assertEquals("", Csv.number(Double.NaN))
        assertEquals("120,1.5,-2", Csv.row(120, doubleArrayOf(1.5, -2.0)))
    }

    @Test fun escaping() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
    }

    @Test fun roundTrip() {
        val text = listOf(Csv.comment("araç: level"), Csv.header(listOf("t_ms", "x_deg")), Csv.row(0, doubleArrayOf(1.0)), "bozuk", Csv.row(10, doubleArrayOf(2.5)))
        val t = Csv.parse(text.asSequence())
        assertEquals(listOf("t_ms", "x_deg"), t.columns)
        assertEquals(2, t.rows.size)
        assertEquals(2.5, t.rows[1][1], 0.0)
    }

    @Test fun downsampleKeepsPeaks() {
        val n = 10_000
        val x = DoubleArray(n) { it.toDouble() }
        val y = DoubleArray(n) { if (it == 5_000) 100.0 else 0.0 }
        val (_, oy) = Downsample.minMax(x, y, 200)
        assertEquals(100.0, oy.max(), 0.0)
        assert(oy.size <= 200)
    }
}
