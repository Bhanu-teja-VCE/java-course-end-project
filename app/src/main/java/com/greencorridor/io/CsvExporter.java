package com.greencorridor.io;

import javax.swing.table.TableModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/** Writes the contents of a table to a CSV file that opens in Excel. */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static void export(TableModel model, File file) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            StringBuffer line = new StringBuffer();
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) {
                    line.append(',');
                }
                line.append(escape(model.getColumnName(c)));
            }
            out.println(line);
            for (int r = 0; r < model.getRowCount(); r++) {
                line.setLength(0);
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) {
                        line.append(',');
                    }
                    Object value = model.getValueAt(r, c);
                    line.append(escape(value == null ? "" : value.toString()));
                }
                out.println(line);
            }
            if (out.checkError()) {
                throw new IOException("Writing " + file.getName() + " failed");
            }
        }
    }

    /** Quotes a value if it contains a comma, quote or line break. */
    static String escape(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
