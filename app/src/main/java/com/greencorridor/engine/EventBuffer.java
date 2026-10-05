package com.greencorridor.engine;

import java.util.LinkedList;

/**
 * A bounded first-in-first-out buffer shared by many producer threads (vehicles and
 * signal controllers) and one consumer thread (the database logger).
 * This is the classic producer-consumer problem solved with wait() and notifyAll().
 *
 * @param <T> type of the items
 */
public class EventBuffer<T> {

    private final LinkedList<T> items = new LinkedList<T>();
    private final int capacity;

    public EventBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /** Adds an item, waiting while the buffer is full. */
    public synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();                 // producer waits for the consumer to make room
        }
        items.addLast(item);
        notifyAll();                // a consumer may be waiting for data
    }

    /** Removes the oldest item, waiting while the buffer is empty. */
    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();                 // consumer waits for a producer
        }
        T item = items.removeFirst();
        notifyAll();                // a producer may be waiting for room
        return item;
    }

    public synchronized int size() {
        return items.size();
    }

    public int getCapacity() {
        return capacity;
    }
}
