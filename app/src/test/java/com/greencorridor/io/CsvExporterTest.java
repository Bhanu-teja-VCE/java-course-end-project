package com.greencorridor.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvExporterTest {

    @Test
    void quotesOnlyWhenNeeded() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""));
    }

    @Test
    void writesHeaderAndRows(@TempDir Path dir) throws Exception {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Run", "Scenario"}, 0);
        model.addRow(new Object[]{1, "Morning Peak, corridor"});
        File file = dir.resolve("runs.csv").toFile();
        CsvExporter.export(model, file);
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals("Run,Scenario", lines.get(0));
        assertEquals("1,\"Morning Peak, corridor\"", lines.get(1));
    }
}
