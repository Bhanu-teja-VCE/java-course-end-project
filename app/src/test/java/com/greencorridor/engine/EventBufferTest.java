package com.greencorridor.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBufferTest {

    @Test
    void manyProducersOneConsumerLoseNothing() throws Exception {
        final EventBuffer<Integer> buffer = new EventBuffer<Integer>(5);   // small, so producers must wait
        final int producers = 4;
        final int each = 500;
        List<Thread> threads = new ArrayList<Thread>();
        for (int p = 0; p < producers; p++) {
            final int base = p * each;
            Thread t = new Thread(() -> {
                try {
                    for (int i = 0; i < each; i++) {
                        buffer.put(base + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(t);
            t.start();
        }
        long sum = 0;
        for (int i = 0; i < producers * each; i++) {
            sum += buffer.take();
        }
        for (Thread t : threads) {
            t.join(2000);
        }
        int n = producers * each;
        assertEquals((long) n * (n - 1) / 2, sum);
        assertEquals(0, buffer.size());
    }

    @Test
    void takeWaitsUntilSomethingIsPut() throws Exception {
        final EventBuffer<String> buffer = new EventBuffer<String>(1);
        final String[] got = new String[1];
        Thread consumer = new Thread(() -> {
            try {
                got[0] = buffer.take();
            } catch (InterruptedException ignored) {
                // test failed anyway
            }
        });
        consumer.start();
        Thread.sleep(150);
        assertEquals(Thread.State.WAITING, consumer.getState(), "an empty buffer makes the consumer wait()");
        buffer.put("hello");
        consumer.join(2000);
        assertEquals("hello", got[0]);
        assertTrue(!consumer.isAlive());
    }
}
