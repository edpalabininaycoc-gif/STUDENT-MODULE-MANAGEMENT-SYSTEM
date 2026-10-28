package moduledistributionsystem.data;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A small generic FIFO queue (First In, First Out) built on a singly linked list.
 *
 * <p><b>Data structure:</b> items are added at the <i>tail</i> ({@link #enqueue}) and removed from the
 * <i>head</i> ({@link #dequeue}), so the item that waited longest always leaves first. Both operations are
 * O(1) because the queue keeps a reference to the first and the last node, and nothing is ever shifted.
 *
 * <p><b>Where it is used:</b> the module distribution rule is "first come, first served". When staff mark
 * several approved students as picked up, {@code DemoDataStore.markPickedUpInOrder} puts them into a
 * {@code FifoQueue} in the order they asked, then serves them one by one - so when only one copy is left, the
 * student who asked first gets it. {@code DemoDataStore.pickupQueueFor} also exposes the line of approved
 * students waiting for one module.
 *
 * <p><b>How to customize:</b> this class knows nothing about modules or students, so it can be reused for any
 * waiting line. If you need a different order (for example "highest priority first"), use a
 * {@code java.util.PriorityQueue} with a comparator instead.
 *
 * @param <T> the type of item waiting in the queue
 */
public final class FifoQueue<T> implements Iterable<T> {

    /** One link of the chain: the stored item and a pointer to the next node (null at the tail). */
    private static final class Node<T> {
        final T item;
        Node<T> next;

        Node(T item) { this.item = item; }
    }

    /** The node that leaves next (null when the queue is empty). */
    private Node<T> head;

    /** The node added last (null when the queue is empty). */
    private Node<T> tail;

    /** How many items are waiting. */
    private int size;

    /** Adds an item at the back of the line. O(1). */
    public void enqueue(T item) {
        Node<T> node = new Node<>(item);
        if (tail == null) {
            head = node;            // first item: it is both the head and the tail
        } else {
            tail.next = node;       // link behind the current last item
        }
        tail = node;
        size++;
    }

    /**
     * Removes and returns the item at the front of the line (the one that waited longest). O(1).
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public T dequeue() {
        if (head == null) throw new NoSuchElementException("The queue is empty.");
        T item = head.item;
        head = head.next;
        if (head == null) tail = null;   // the line is empty again
        size--;
        return item;
    }

    /**
     * Returns the item at the front of the line without removing it.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public T peek() {
        if (head == null) throw new NoSuchElementException("The queue is empty.");
        return head.item;
    }

    /** True when nobody is waiting. */
    public boolean isEmpty() { return size == 0; }

    /** Number of items waiting. */
    public int size() { return size; }

    /** Walks the line from the front to the back WITHOUT removing anything. */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> current = head;

            @Override public boolean hasNext() { return current != null; }

            @Override public T next() {
                if (current == null) throw new NoSuchElementException();
                T item = current.item;
                current = current.next;
                return item;
            }
        };
    }
}
