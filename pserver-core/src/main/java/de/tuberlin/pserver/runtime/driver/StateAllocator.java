package de.tuberlin.pserver.runtime.driver;


import de.tuberlin.pserver.compiler.StateDescriptor;
import de.tuberlin.pserver.runtime.core.remoteobj.GlobalObject;
import de.tuberlin.pserver.runtime.state.matrix.rpc.GlobalStateMatrixProxy;
import de.tuberlin.pserver.types.matrix.properties.ElementType;
import de.tuberlin.pserver.types.matrix.MatrixBuilder;
import de.tuberlin.pserver.types.matrix.f32.Matrix32F;
import de.tuberlin.pserver.types.matrix.partitioner.PartitionType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.tukaani.xz.UnsupportedOptionsException;

public final class StateAllocator {

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public Pair<Matrix32F, Matrix32F> alloc(final ProgramContext programContext, final StateDescriptor state)
            throws Exception {

        Matrix32F stateObj = null, proxyObj = null;

            PartitionType partitionType;

            switch (state.scope) {
                case SINGLETON:
                    partitionType = PartitionType.NO_PARTITIONER;
                    break;
                case REPLICATED:
                    partitionType = PartitionType.NO_PARTITIONER;
                    break;
                case PARTITIONED:
                    partitionType = PartitionType.ROW_PARTITIONER;
                    break;
                default:
                    throw new UnsupportedOptionsException();
            }

            if (ArrayUtils.contains(state.atNodes, programContext.nodeID)) {

                stateObj = new MatrixBuilder()
                        .dimension(state.rows, state.cols)
                        .matrixFormat(state.matrixFormat)
                        .elementType(ElementType.getElementTypeFromClass(state.stateType))
                        .build(programContext.nodeID, state.atNodes, partitionType);

                new GlobalObject<>(programContext.runtimeContext.netManager, stateObj, state.stateName);

            } else {

                proxyObj = GlobalStateMatrixProxy.create(programContext, state);
            }

        return Pair.of(stateObj, proxyObj);
    }
}
