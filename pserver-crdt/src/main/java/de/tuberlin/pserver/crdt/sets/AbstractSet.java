package de.tuberlin.pserver.crdt.sets;


import de.tuberlin.pserver.crdt.AbstractCRDT;
import de.tuberlin.pserver.runtime.RuntimeManager;

// TODO: should this implement Set<E> from collections library?
public abstract class AbstractSet<T> extends AbstractCRDT<T> implements Set<T> {

    public AbstractSet(String id, RuntimeManager runtimeManager) {
        super(id, runtimeManager);
    }

    public boolean contains(T element) {
        return getSet().contains(element);
    }
}