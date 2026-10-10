package app.get1won;
import java.util.ArrayDeque;

/** Chronological physical lines, bounded even when an event contains newlines. */
final class RecentLog {
    private final ArrayDeque<String> lines=new ArrayDeque<>();
    private final int capacity;
    RecentLog(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
    void add(String event){for(String line:event.split("\\R",-1)){lines.addLast(line);while(lines.size()>capacity)lines.removeFirst();}}
    String text(){return String.join("\n",lines);}
}
