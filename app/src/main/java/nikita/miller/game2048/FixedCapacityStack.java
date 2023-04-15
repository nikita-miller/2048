package nikita.miller.game2048;

import java.util.LinkedList;

public class FixedCapacityStack<T> {
    private final int capacity;
    private final LinkedList<T> stack;

    public FixedCapacityStack(int capacity) {
        this.capacity = capacity > 0 ? capacity : 1;
        stack = new LinkedList<>();
    }

    public void push(T item) {
        if (stack.size() == capacity) {
            stack.removeFirst();
        }

        stack.addLast(item);
    }

    public T pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Can't pop from empty stack");
        }

        return stack.removeLast();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public void clear() {
        stack.clear();
    }
}
