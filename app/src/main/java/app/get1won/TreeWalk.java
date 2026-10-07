package app.get1won;
import java.util.*;
import java.util.function.LongSupplier;
/** Bounded, partial-result BFS. Visibility controls collection, never child traversal. */
public final class TreeWalk {
    public interface Access<T> {
        int children(T node);
        T child(T node,int index);
        void visit(T node,int id,int parent);
        void release(T node);
    }
    private record Entry<T>(T node,int id,int parent) {}
    public record Result(int visited,boolean partial) {}
    public static <T> Result collect(T root,Access<T> access,int budget,LongSupplier clock,long milliseconds) {
        ArrayDeque<Entry<T>> queue=new ArrayDeque<>();queue.add(new Entry<>(root,0,-1));
        int visited=0,allocated=1;boolean partial=false;long start=clock.getAsLong();
        try {
            while(!queue.isEmpty()) {
                if(visited>=budget || clock.getAsLong()-start>=milliseconds) { partial=true;break; }
                Entry<T> e=queue.removeFirst();
                try {
                    access.visit(e.node,e.id,e.parent);visited++;
                    int children=access.children(e.node);
                    for(int i=0;i<children;i++) {
                        if(allocated>=budget || clock.getAsLong()-start>=milliseconds) { partial=true;break; }
                        T child=access.child(e.node,i);
                        if(child!=null)queue.addLast(new Entry<>(child,allocated++,e.id));
                    }
                } finally { access.release(e.node); }
            }
        } finally { while(!queue.isEmpty())access.release(queue.removeFirst().node); }
        return new Result(visited,partial);
    }
}
