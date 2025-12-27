package dev.cloudframe.cloudframe.util;

import java.util.LinkedList;
import java.util.List;

public class DebugBuffer {

    private static final int MAX_LINES = 500;
    private static final LinkedList<String> buffer = new LinkedList<>();

    public static synchronized void add(String line) {
        buffer.addLast(line);
        if (buffer.size() > MAX_LINES) {
            buffer.removeFirst();
        }
    }

    public static synchronized List<String> getLast(int count) {
        int start = Math.max(0, buffer.size() - count);
        return buffer.subList(start, buffer.size());
    }

    public static synchronized void clear() {
        buffer.clear();
    }
}
