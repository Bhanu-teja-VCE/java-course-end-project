package com.greencorridor.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a MySQL script into single statements, understanding the DELIMITER command
 * that MySQL clients use around stored procedures. JDBC runs one statement at a time.
 */
public final class SqlScript {

    private SqlScript() {
    }

    public static List<String> parse(Reader source) throws IOException {
        List<String> statements = new ArrayList<String>();
        StringBuffer current = new StringBuffer();
        String delimiter = ";";
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (current.length() == 0 && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;                              // blank line or comment between statements
            }
            if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = current.toString().trim();
                sql = sql.substring(0, sql.length() - delimiter.length()).trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current.setLength(0);
            }
        }
        if (current.toString().trim().length() > 0) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
