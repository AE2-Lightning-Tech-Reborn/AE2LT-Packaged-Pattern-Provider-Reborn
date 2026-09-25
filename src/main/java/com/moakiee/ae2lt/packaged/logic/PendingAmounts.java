package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Owned quantities awaiting delivery. A rejected delivery never removes an amount. */
public final class PendingAmounts<K> {
    public record Entry<K>(K key, long amount) {}

    // Split exceptionally large totals into long-sized chunks instead of overflowing.
    private final Map<K, ArrayDeque<Long>> amounts = new LinkedHashMap<>();

    public void add(K key, long amount) {
        if (key == null || amount <= 0) return;
        var chunks = amounts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        if (!chunks.isEmpty()) {
            long last = chunks.removeLast();
            long merged = Math.min(amount, Long.MAX_VALUE - last);
            chunks.addLast(last + merged);
            amount -= merged;
        }
        if (amount > 0) chunks.addLast(amount);
    }

    /**
     * The sink returns the quantity it actually accepted. Record each receipt
     * immediately, so a later failing sink cannot resurrect earlier deliveries.
     * The sink must not mutate this buffer or throw after accepting a quantity.
     */
    public void deliver(ToLongFunction<Entry<K>> sink) {
        var entries = amounts.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var chunks = entry.getValue();
            while (!chunks.isEmpty()) {
                long requested = chunks.getFirst();
                long accepted = sink.applyAsLong(new Entry<>(entry.getKey(), requested));
                if (accepted < 0 || accepted > requested) {
                    throw new IllegalStateException("Invalid delivery receipt: " + accepted + "/" + requested);
                }
                if (accepted == 0) break;
                chunks.removeFirst();
                if (accepted < requested) {
                    chunks.addFirst(requested - accepted);
                    break;
                }
            }
            if (chunks.isEmpty()) entries.remove();
        }
    }

    public List<Entry<K>> snapshot() {
        var result = new ArrayList<Entry<K>>();
        amounts.forEach((key, chunks) -> chunks.forEach(amount -> result.add(new Entry<>(key, amount))));
        return List.copyOf(result);
    }

    public boolean isEmpty() { return amounts.isEmpty(); }

    public void clear() { amounts.clear(); }
}
