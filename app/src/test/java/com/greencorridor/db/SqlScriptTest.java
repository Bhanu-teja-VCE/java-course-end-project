package com.greencorridor.db;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptTest {

    @Test
    void understandsDelimiter() throws Exception {
        String script = "-- comment\nCREATE TABLE t (id INT);\n\nDELIMITER $$\n"
                + "CREATE PROCEDURE p()\nBEGIN\n  SELECT 1;\n  SELECT 2;\nEND$$\nDELIMITER ;\nINSERT INTO t VALUES (1);\n";
        List<String> statements = SqlScript.parse(new StringReader(script));
        assertEquals(3, statements.size());
        assertTrue(statements.get(1).startsWith("CREATE PROCEDURE p()"));
        assertTrue(statements.get(1).endsWith("END"), "the $$ delimiter is removed");
        assertTrue(statements.get(1).contains("SELECT 1;"), "semicolons inside the body are kept");
    }

    @Test
    void bundledScriptParses() throws Exception {
        List<String> statements = SqlScript.parse(new InputStreamReader(
                SqlScriptTest.class.getResourceAsStream(SchemaInstaller.SCRIPT), StandardCharsets.UTF_8));
        long procedures = 0;
        for (String s : statements) {
            if (s.startsWith("CREATE PROCEDURE")) {
                procedures++;
            }
        }
        assertEquals(3, procedures);
        assertTrue(statements.get(statements.size() - 1).startsWith("INSERT INTO scenario"));
    }
}
