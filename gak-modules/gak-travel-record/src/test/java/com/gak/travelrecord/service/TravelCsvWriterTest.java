package com.gak.travelrecord.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelCsvWriterTest {
    @Test void escapesSpreadsheetFormulasQuotesAndKeepsUnknownEmpty() {
        TravelCsvWriter writer = new TravelCsvWriter();
        assertEquals("\"' =SUM(1,2)\"", writer.escape(" =SUM(1,2)"));
        assertEquals("\"站点\"\"名称\"", writer.escape("站点\"名称"));
        assertEquals("\"\"", writer.escape(null));
    }
}
