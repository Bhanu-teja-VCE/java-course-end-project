package com.greencorridor.io;

import com.greencorridor.model.SimEvent;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A plain-text "black box" log of a run in the logs folder, one line per event.
 * It is written even when there is no database, so no run is ever lost.
 */
public class RunLogWriter implements Closeable {

    public static final String LOG_DIR = "logs";

    private final File file;
    private final BufferedWriter out;

    public RunLogWriter(File file, String header) throws IOException {
        this.file = file;
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create folder " + dir);
        }
        this.out = new BufferedWriter(new FileWriter(file));
        out.write(header);
        out.newLine();
    }

    /** logs/run-12-20261005-101500.log, or run-offline-... when not saved in MySQL. */
    public static File fileFor(int runId) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String id = runId > 0 ? String.valueOf(runId) : "offline";
        return new File(LOG_DIR, "run-" + id + "-" + stamp + ".log");
    }

    public void write(SimEvent event) throws IOException {
        out.write(event.toLogLine());
        out.newLine();
    }

    public File getFile() {
        return file;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
