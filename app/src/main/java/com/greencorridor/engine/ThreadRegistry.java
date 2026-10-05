package com.greencorridor.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps every thread of a run so the Thread Monitor can show its life cycle
 * (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED).
 * Finished threads stay visible for a couple of seconds before they are dropped.
 */
public class ThreadRegistry {

    /** How long (real time) a TERMINATED thread stays in the list. */
    private static final long KEEP_TERMINATED_MS = 2500;

    private final Map<Thread, Monitorable> entries = new LinkedHashMap<Thread, Monitorable>();
    private final Map<Thread, Long> terminatedAt = new HashMap<Thread, Long>();

    public synchronized void register(Thread thread, Monitorable owner) {
        purge(System.currentTimeMillis());
        entries.put(thread, owner);
    }

    /** Called by a thread as it ends, so it is shown as TERMINATED for a short while only. */
    public synchronized void markTerminated(Thread thread) {
        terminatedAt.put(thread, System.currentTimeMillis());
    }

    private void purge(long nowReal) {
        Iterator<Map.Entry<Thread, Long>> it = terminatedAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Thread, Long> e = it.next();
            if (nowReal - e.getValue() > KEEP_TERMINATED_MS && !e.getKey().isAlive()) {
                entries.remove(e.getKey());
                it.remove();
            }
        }
    }

    public synchronized List<Thread> threads() {
        return new ArrayList<Thread>(entries.keySet());
    }

    /** One row per thread, dropping threads that ended a while ago. */
    public synchronized List<Row> snapshot() {
        long nowReal = System.currentTimeMillis();
        purge(nowReal);
        List<Row> rows = new ArrayList<Row>();
        Iterator<Map.Entry<Thread, Monitorable>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Thread, Monitorable> e = it.next();
            Thread t = e.getKey();
            Thread.State state = t.getState();
            if (state == Thread.State.TERMINATED) {
                Long since = terminatedAt.get(t);
                if (since == null) {
                    terminatedAt.put(t, nowReal);
                } else if (nowReal - since > KEEP_TERMINATED_MS) {
                    it.remove();
                    terminatedAt.remove(t);
                    continue;
                }
            }
            rows.add(new Row(t.getName(), e.getValue().getRole(), t.getPriority(), state,
                    e.getValue().getStatusText()));
        }
        return rows;
    }

    public synchronized int liveCount() {
        int n = 0;
        for (Thread t : entries.keySet()) {
            if (t.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** Read-only description of one thread at one moment. */
    public static class Row {
        private final String name;
        private final String role;
        private final int priority;
        private final Thread.State state;
        private final String status;

        public Row(String name, String role, int priority, Thread.State state, String status) {
            this.name = name;
            this.role = role;
            this.priority = priority;
            this.state = state;
            this.status = status;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }

        public int getPriority() {
            return priority;
        }

        public Thread.State getState() {
            return state;
        }

        public String getStatus() {
            return status;
        }
    }
}
