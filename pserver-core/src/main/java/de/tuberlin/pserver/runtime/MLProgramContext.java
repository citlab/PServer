package de.tuberlin.pserver.runtime;


import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import de.tuberlin.pserver.commons.ds.ResettableCountDownLatch;
import de.tuberlin.pserver.commons.json.GsonUtils;
import de.tuberlin.pserver.core.infra.MachineDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public final class MLProgramContext {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private transient static Gson gson = GsonUtils.createPrettyPrintAndAnnotationExclusionGson();

    @GsonUtils.Exclude
    public final RuntimeContext runtimeContext;

    public final MachineDescriptor clientMachine;

    public final UUID programID;

    public final String className;

    public final String simpleClassName;

    public final int nodeDOP;

    public final int perNodeDOP;

    // ---------------------------------------------------

    @GsonUtils.Exclude
    public final ResettableCountDownLatch globalSyncBarrier;

    @GsonUtils.Exclude
    public final CyclicBarrier localSyncBarrier;

    @GsonUtils.Exclude
    public final List<SlotContext> slots;

    @GsonUtils.Exclude
    public final CountDownLatch programLoadBarrier;

    @GsonUtils.Exclude
    public final CountDownLatch programInitBarrier;

    @GsonUtils.Exclude
    public final CountDownLatch programDoneBarrier;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public MLProgramContext(final RuntimeContext runtimeContext,
                            final MachineDescriptor clientMachine,
                            final UUID programID,
                            final String className,
                            final String simpleClassName,
                            final int nodeDOP,
                            final int perNodeDOP) {

        this.runtimeContext     = Preconditions.checkNotNull(runtimeContext);
        this.clientMachine      = Preconditions.checkNotNull(clientMachine);
        this.programID          = Preconditions.checkNotNull(programID);
        this.className          = Preconditions.checkNotNull(className);
        this.simpleClassName    = Preconditions.checkNotNull(simpleClassName);
        this.nodeDOP            = nodeDOP;
        this.perNodeDOP         = perNodeDOP;

        this.globalSyncBarrier  = new ResettableCountDownLatch(nodeDOP);
        this.localSyncBarrier   = new CyclicBarrier(perNodeDOP);
        this.slots = new ArrayList<>();

        this.programLoadBarrier = new CountDownLatch(1);
        this.programInitBarrier = new CountDownLatch(1);
        this.programDoneBarrier = new CountDownLatch(perNodeDOP);
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public void addSlot(final SlotContext ic) {
        slots.add(Preconditions.checkNotNull(ic));
    }

    public void removeSlot(final SlotContext ic) {
        slots.remove(Preconditions.checkNotNull(ic));
    }

    @Override
    public String toString() {
        return "\nMLProgramContext " + gson.toJson(this);
    }
}