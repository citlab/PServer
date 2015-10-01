package de.tuberlin.pserver.sets;

import de.tuberlin.pserver.crdt.IllegalOperationException;
import de.tuberlin.pserver.crdt.Operation;
import de.tuberlin.pserver.runtime.DataManager;

import java.util.HashSet;
import java.util.Set;

/**
 * In a Two-Phase Set an element may be added and removed but never added again thereafter.
 */

public class TwoPSet<T> extends AbstractSet<T> {
    private final Set<T> set;
    private final Set<T> tombstone;

    public TwoPSet(String id, DataManager dataManager) {
        super(id, dataManager);

        this.set = new HashSet<>();
        this.tombstone = new HashSet<>();

        run(dataManager);
    }

    @Override
    protected boolean update(int srcNodeId, Operation<T> op, DataManager dm) {
        // TODO: I hate this cast....
        SetOperation<T> sop = (SetOperation<T>)op;

        if(sop.getType() == SetOperation.ADD) {
            return addElement(op.getValue());
        }
        else if(sop.getType() == SetOperation.REMOVE) {
            return removeElement(op.getValue());
        }
        else {
            // TODO: specifiy exception.
            throw new IllegalOperationException("Blub");
        }
    }

    @Override
    public boolean add(T element, DataManager dataManager) {
        if(addElement(element)) {
            broadcast(new SetOperation<T>(SetOperation.ADD, element), dataManager);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(T element, DataManager dataManager) {
        if(removeElement(element)) {
            broadcast(new SetOperation<T>(SetOperation.REMOVE, element), dataManager);
            return true;
        }
        return false;
    }

    @Override
    public Set<T> getSet() {
        return this.set;
    }

    private boolean addElement(T element) {
        if(!tombstone.contains(element)) {
            return set.add(element);
        }
        return false;
    }

    private boolean removeElement(T element) {
        if(set.remove(element)){
            tombstone.add(element);
            return true;
        }
        return false;
    }
}
