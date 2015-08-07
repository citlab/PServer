package de.tuberlin.pserver.runtime;


import com.google.common.base.Preconditions;
import de.tuberlin.pserver.core.config.IConfig;
import de.tuberlin.pserver.core.events.Event;
import de.tuberlin.pserver.core.events.EventDispatcher;
import de.tuberlin.pserver.core.events.IEventDispatcher;
import de.tuberlin.pserver.core.events.IEventHandler;
import de.tuberlin.pserver.core.infra.InfrastructureManager;
import de.tuberlin.pserver.core.infra.MachineDescriptor;
import de.tuberlin.pserver.core.net.NetEvents;
import de.tuberlin.pserver.core.net.NetManager;
import de.tuberlin.pserver.math.SharedObject;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.runtime.dht.DHTKey;
import de.tuberlin.pserver.runtime.dht.DHTManager;
import de.tuberlin.pserver.runtime.dht.DHTObject;
import de.tuberlin.pserver.runtime.dht.types.AbstractBufferedDHTObject;
import de.tuberlin.pserver.runtime.dht.types.EmbeddedDHTObject;
import de.tuberlin.pserver.runtime.filesystem.FileSystemManager;
import de.tuberlin.pserver.runtime.filesystem.record.RecordFormat;
import de.tuberlin.pserver.runtime.partitioning.IMatrixPartitioner;
import de.tuberlin.pserver.runtime.partitioning.MatrixByRowPartitioner;
import de.tuberlin.pserver.runtime.partitioning.MatrixPartitionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DataManager extends EventDispatcher {

    // ---------------------------------------------------
    // Constants.
    // ---------------------------------------------------

    public static enum CallType {

        SYNC,

        ASYNC
    }

    // ---------------------------------------------------
    // Inner Classes.
    // ---------------------------------------------------

    public static abstract class DataEventHandler implements IEventHandler {

        private CountDownLatch latch = null;

        private InfrastructureManager infraManager;

        private IEventDispatcher dispatcher;

        private boolean removeAfterAwait;

        public abstract void handleDataEvent(final int srcNodeID, final Object value);

        @Override
        public void handleEvent(final Event e) {
            final NetEvents.NetEvent event = (NetEvents.NetEvent) e;
            final int srcNodeID = infraManager.getNodeIDFromMachineUID(event.srcMachineID);
            handleDataEvent(srcNodeID, event.getPayload());
            latch.countDown();

            if (removeAfterAwait && latch.getCount() == 0)
                dispatcher.removeEventListener(event.type, this);
        }

        public void initLatch(final int n) { latch = new CountDownLatch(n); }

        public CountDownLatch getLatch() { return latch; }

        public void setDispatcher(final IEventDispatcher dispatcher) { this.dispatcher = dispatcher; }

        public void setInfraManager(final InfrastructureManager infraManager) { this.infraManager = infraManager; }

        public void setRemoveAfterAwait(final boolean removeAfterAwait) { this.removeAfterAwait = removeAfterAwait; }
    }

    public static interface PullRequestHandler {

        public abstract Object handlePullRequest(final String name);
    }

    public interface Merger<T extends SharedObject> {

        public abstract void merge(final T dst, final List<T> src);
    }

    // ---------------------------------------------------
    // Constants.
    // ---------------------------------------------------

    private static final String BSP_SYNC_BARRIER_EVENT = "bsp_sync_barrier_event";

    private static final String PUSH_EVENT_PREFIX = "push__";

    private static final String PULL_EVENT_PREFIX = "pull__";

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(DataManager.class);

    private final InfrastructureManager infraManager;

    private final NetManager netManager;

    private final FileSystemManager fileSystemManager;

    private final MatrixPartitionManager matrixPartitionManager;

    private final DHTManager dht;

    private final int nodeID;


    private final Map<UUID, List<Serializable>> resultObjects;

    private final Map<UUID, JobContext> jobContextMap;

    private final Map<Long, InstanceContext> instanceContextMap;

    // ---------------------------------------------------

    private final int[] nodeIDs;

    private final int[] remoteNodeIDs;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public DataManager(final IConfig config,
                       final InfrastructureManager infraManager,
                       final NetManager netManager,
                       final FileSystemManager fileSystemManager,
                       final DHTManager dht) {
        super(true);

        this.infraManager       = Preconditions.checkNotNull(infraManager);
        this.netManager         = Preconditions.checkNotNull(netManager);
        this.fileSystemManager  = fileSystemManager;
        this.dht                = Preconditions.checkNotNull(dht);
        this.nodeID             = infraManager.getNodeID();
        this.resultObjects      = new HashMap<>();
        this.jobContextMap      = new ConcurrentHashMap<>();
        this.instanceContextMap = new ConcurrentHashMap<>();

        this.matrixPartitionManager = new MatrixPartitionManager(
                config,
                infraManager,
                netManager,
                fileSystemManager,
                this
        );

        this.netManager.addEventListener(BSP_SYNC_BARRIER_EVENT, event -> {
            jobContextMap.get((UUID)event.getPayload()).globalSyncBarrier.countDown();
        });

        this.nodeIDs = IntStream.iterate(0, x -> x + 1).limit(infraManager.getMachines().size()).toArray();
        int numOfRemoteWorkers = infraManager.getMachines().size() - 1;
        this.remoteNodeIDs = new int[numOfRemoteWorkers];
        int i = 0, j = 0;
        for (MachineDescriptor md : infraManager.getMachines()) {
            if (!md.equals(infraManager.getMachine())) {
                remoteNodeIDs[j] = i;
                ++j;
            }
            ++i;
        }
    }

    // ---------------------------------------------------
    // EVENT HANDLING
    // ---------------------------------------------------

    public void addDataEventListener(final String name, final DataEventHandler handler) {
        addDataEventListener(remoteNodeIDs.length, name, handler);
    }
    public void addDataEventListener(final int n, final String name, final DataEventHandler handler) {
        handler.setInfraManager(infraManager);
        handler.initLatch(n);
        netManager.addEventListener(PUSH_EVENT_PREFIX + name, handler);
    }

    public void removeDataEventListener(final String name, final DataEventHandler handler) {
        netManager.removeEventListener(PUSH_EVENT_PREFIX + name, handler);
    }

    // ---------------------------------------------------
    // JOB MANAGEMENT
    // ---------------------------------------------------

    public void registerJob(final UUID jobID, final JobContext jobContext) {
        jobContextMap.put(Preconditions.checkNotNull(jobID), Preconditions.checkNotNull(jobContext));
    }

    public JobContext getJob(final UUID jobID) {
        return jobContextMap.get(Preconditions.checkNotNull(jobID));
    }

    public void unregisterJob(final UUID jobID) {
        jobContextMap.remove(Preconditions.checkNotNull(jobID));
    }

    // ------------------------------

    public void registerInstanceContext(final InstanceContext ic) {
        instanceContextMap.put(Thread.currentThread().getId(), Preconditions.checkNotNull(ic));
    }

    public InstanceContext getInstanceContext() {
        return instanceContextMap.get(Thread.currentThread().getId());
    }

    public void unregisterInstanceContext() {
        instanceContextMap.remove(Thread.currentThread().getId());
    }

    // ---------------------------------------------------
    // DATA LOADING
    // ---------------------------------------------------

    public void loadAsMatrix(final String filePath, long rows, long cols) {

        loadAsMatrix(filePath, rows, cols, RecordFormat.DEFAULT, Matrix.Format.DENSE_MATRIX, Matrix.Layout.ROW_LAYOUT,
                new MatrixByRowPartitioner(nodeID, nodeIDs.length, rows, cols));
    }

    public void loadAsMatrix(final String filePath, long rows, long cols, RecordFormat recordFormat) {

        loadAsMatrix(filePath, rows, cols, recordFormat, Matrix.Format.DENSE_MATRIX, Matrix.Layout.ROW_LAYOUT,
                new MatrixByRowPartitioner(nodeID, nodeIDs.length, rows, cols));
    }

    public void loadAsMatrix(final String filePath, long rows, long cols, RecordFormat recordFormat,
                             Matrix.Format matrixFormat, Matrix.Layout matrixLayout) {

        loadAsMatrix(filePath, rows, cols, recordFormat, matrixFormat, matrixLayout,
                new MatrixByRowPartitioner(nodeID, nodeIDs.length, rows, cols));
    }

    public void loadAsMatrix(final String filePath, long rows, long cols, RecordFormat recordFormat,
                             Matrix.Format matrixFormat, Matrix.Layout matrixLayout,
                             IMatrixPartitioner matrixPartitioner) {

        final InstanceContext instanceContext = getInstanceContext();
        matrixPartitionManager.load(filePath, rows, cols, recordFormat, matrixFormat, matrixLayout, matrixPartitioner, instanceContext.jobContext);
    }

    // ---------------------------------------------------
    // COMMUNICATION PRIMITIVES
    // ---------------------------------------------------

    public DHTObject[] pullFrom(final String name, final int[] nodeIDs) {
        Preconditions.checkNotNull(name);
        int idx = 0;
        final Set<DHTKey> keys = dht.getKey(name);
        Preconditions.checkState(nodeIDs.length <= keys.size());
        final DHTObject[] dhtObjects = new DHTObject[nodeIDs.length];
        for (final int id : nodeIDs) {
            for (final DHTKey key : keys) {
                if (key.getPartitionDescriptor(id) != null) {
                    dhtObjects[idx] = dht.get(key)[0];
                    dhtObjects[idx].setKey(key);
                    ++idx;
                    break;
                }
            }
        }
        return dhtObjects;
    }

    public DHTObject[] pullFromAll(final String name) {
        Preconditions.checkNotNull(name);
        int idx = 0;
        final Set<DHTKey> keys = dht.getKey(name);
        final DHTObject[] dhtObjects = new DHTObject[keys.size()];
        for (final DHTKey key : keys) {
            dhtObjects[idx] = dht.get(key)[0];
            dhtObjects[idx].setKey(key);
            ++idx;
        }
        return dhtObjects;
    }

    // ---------------------------------------------------

    public void pushTo(final String name, final Object value, final int[] nodeIDs) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(nodeIDs);
        final NetEvents.NetEvent event = new NetEvents.NetEvent(PUSH_EVENT_PREFIX + name, true);
        event.setPayload(value);
        netManager.sendEvent(nodeIDs, event);
    }

    public void pushTo(final String name, final Object value) {
        Preconditions.checkNotNull(name);
        final NetEvents.NetEvent event = new NetEvents.NetEvent(PUSH_EVENT_PREFIX + name, true);
        event.setPayload(value);
        // remote nodes.
        netManager.sendEvent(remoteNodeIDs, event);
        // local nodes.
        netManager.dispatchEvent(event);
    }

    public void awaitEvent(final CallType type, final String name, final DataEventHandler handler) {
        awaitEvent(type, remoteNodeIDs.length, name, handler); }
    public void awaitEvent(final CallType type, final int n, final String name, final DataEventHandler handler) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(handler);
        handler.setDispatcher(netManager);
        handler.setInfraManager(infraManager);
        handler.setRemoveAfterAwait(true);
        handler.initLatch(n);
        netManager.addEventListener(PUSH_EVENT_PREFIX + name, handler);
        if (type == CallType.SYNC) {
            try {
                handler.getLatch().await();
            } catch (InterruptedException e) {
                LOG.error(e.getLocalizedMessage());
            }
        }
    }

    public void registerPullRequestHandler(final String name, final PullRequestHandler handler) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(handler);
        final DataManager self = this;
        netManager.addEventListener(PULL_EVENT_PREFIX + name, e -> {
            final NetEvents.NetEvent event = (NetEvents.NetEvent) e;
            final int srcNodeID = infraManager.getNodeIDFromMachineUID(event.srcMachineID);
            final Object result = handler.handlePullRequest(name);
            self.pushTo(name, result, new int[]{srcNodeID});
        });
    }

    public Object[] pullRequest(final String name) { return pullRequest(name, remoteNodeIDs); }
    public Object[] pullRequest(final String name, final int[] nodeIDs) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(nodeIDs);

        final Object[] pullResponses = new Object[nodeIDs.length];
        final AtomicInteger responseCounter = new AtomicInteger(0);

        final DataEventHandler responseHandler = new DataEventHandler() {
            @Override
            public void handleDataEvent(int srcNodeID, final Object value) {
                pullResponses[responseCounter.getAndIncrement()] = value;
            }
        };

        responseHandler.setDispatcher(netManager);
        responseHandler.setInfraManager(infraManager);
        responseHandler.setRemoveAfterAwait(true);
        responseHandler.initLatch(nodeIDs.length);
        netManager.addEventListener(PUSH_EVENT_PREFIX + name, responseHandler);

        // send pull request to all nodes.
        NetEvents.NetEvent event = new NetEvents.NetEvent(PULL_EVENT_PREFIX + name, true);
        netManager.sendEvent(nodeIDs, event);

        try {
            responseHandler.getLatch().await();
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage());
        }

        return pullResponses;
    }

    // ---------------------------------------------------
    // OBJECT MANAGEMENT
    // ---------------------------------------------------

    public DHTKey putLocal(final String name, final AbstractBufferedDHTObject value) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        final DHTKey key = dht.createLocalKey(name);
        value.setValueMetadata(nodeID);
        dht.put(key, value);
        return key;
    }

    public DHTObject[] getLocal(final String name) {
        Preconditions.checkNotNull(name);
        DHTKey localKey = null;
        final Set<DHTKey> key = dht.getKey(name);
        for (final DHTKey k : key) {
            if (k.getPartitionDescriptor(nodeID) != null) {
                localKey = k;
                break;
            }
        }
        return getLocal(localKey);
    }

    public DHTObject[] getLocal(final DHTKey key) {
        Preconditions.checkNotNull(key);
        return dht.get(key);
    }

    public void removeLocal(final String name) {
        Preconditions.checkNotNull(name);
        DHTKey localKey = null;
        final Set<DHTKey> key = dht.getKey(name);
        for (final DHTKey k : key) {
            if (k.getPartitionDescriptor(nodeID) != null) {
                localKey = k;
                break;
            }
        }
        removeLocal(localKey);
    }

    public void removeLocal(final DHTKey key) {
        Preconditions.checkNotNull(key);
        dht.delete(key);
    }

    // ---------------------------------------------------

    public <T extends SharedObject> DHTKey putObject(final String name, final T obj) {
        return putLocal(name, new EmbeddedDHTObject<T>(obj));
    }

    @SuppressWarnings("unchecked")
    public <T extends SharedObject> T getObject(final String name) {
        return (T) ((EmbeddedDHTObject) getLocal(name)[0]).object;
    }

    // ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public <T extends SharedObject> void pullMerge(final T dstObj,
                                              final Merger<T> merger) {

        pullMerge(((EmbeddedDHTObject<T>) dstObj.getOwner()).getKey().name, nodeIDs, dstObj, merger);
    }


    public <T extends SharedObject> void pullMerge(final String name,
                                              final T dstObj,
                                              final Merger<T> merger) {

        pullMerge(name, nodeIDs, dstObj, merger);
    }

    public <T extends SharedObject> void pullMerge(final String name,
                                              final int[] nodeIDs,
                                              final T dstObj,
                                              final Merger<T> merger) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(nodeIDs);
        Preconditions.checkNotNull(dstObj);
        Preconditions.checkNotNull(merger);

        final DHTObject[] dhtObjects = pullFrom(name, nodeIDs);
        if (dhtObjects.length > 0) {
            //if (values.getClass().getComponentType() != dstObj.getClass())
            //    throw new IllegalStateException();
            final List<DHTObject> dhtObjectList = Arrays.asList(dhtObjects);
            Collections.sort(dhtObjectList,
                    (DHTObject o1, DHTObject o2) -> ((Integer) o1.getValueMetadata()).compareTo(((Integer) o2.getValueMetadata())));
            @SuppressWarnings("unchecked")
            final List<T> mObjects = dhtObjectList.stream().map(v -> ((EmbeddedDHTObject<T>) v).object).collect(Collectors.toList());
            merger.merge(dstObj, mObjects);
        } else
            throw new IllegalStateException();
    }

    // ---------------------------------------------------
    // CONTROL FLOW
    // ---------------------------------------------------

    public void globalSync() {
        final InstanceContext instanceContext = getInstanceContext();
        final NetEvents.NetEvent globalSyncEvent = new NetEvents.NetEvent(BSP_SYNC_BARRIER_EVENT);
        globalSyncEvent.setPayload(instanceContext.jobContext.jobUID);
        netManager.broadcastEvent(globalSyncEvent);
        try {
            instanceContext.jobContext.globalSyncBarrier.await();
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }
        if (instanceContext.jobContext.globalSyncBarrier.getCount() == 0) {
            instanceContext.jobContext.globalSyncBarrier.reset();
        } else {
            throw new IllegalStateException();
        }
    }

    public void localSync() {
        final InstanceContext instanceContext = getInstanceContext();
        try {
            instanceContext.jobContext.localSyncBarrier.await();
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage());
        }
    }

    // ---------------------------------------------------
    // THREAD PARALLEL PRIMITIVES
    // ---------------------------------------------------

    public Matrix.RowIterator createThreadPartitionedRowIterator(final Matrix matrix) {
        Preconditions.checkNotNull(matrix);
        final InstanceContext instanceContext = getInstanceContext();
        final int rowBlock = (int) matrix.numRows() / instanceContext.jobContext.numOfInstances;
        int end = (instanceContext.instanceID * rowBlock + rowBlock - 1);
        end = (instanceContext.instanceID == instanceContext.jobContext.numOfInstances - 1)
                ? end + (int) matrix.numRows() % instanceContext.jobContext.numOfInstances
                : end;
        return matrix.rowIterator(instanceContext.instanceID * rowBlock, end);
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public void setResults(final UUID jobUID, final List<Serializable> results) {
        Preconditions.checkNotNull(jobUID);
        Preconditions.checkNotNull(results);
        resultObjects.put(jobUID, results);
    }

    public List<Serializable> getResults(final UUID jobUID) {
        Preconditions.checkNotNull(jobUID);
        return resultObjects.get(jobUID);
    }

    public void postProloguePhase(final InstanceContext ctx) {
        Preconditions.checkNotNull(ctx);
        if (fileSystemManager != null) {
            if (ctx.instanceID == 0) {
                fileSystemManager.computeInputSplitsForRegisteredFiles();
                matrixPartitionManager.loadFilesIntoDHT();
            }
        }
    }
}
