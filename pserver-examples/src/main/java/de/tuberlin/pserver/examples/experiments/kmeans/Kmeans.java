package de.tuberlin.pserver.examples.experiments.kmeans;

import de.tuberlin.pserver.client.PServerExecutor;
import de.tuberlin.pserver.dsl.controlflow.iteration.Iteration;
import de.tuberlin.pserver.dsl.controlflow.program.Program;
import de.tuberlin.pserver.dsl.state.GlobalScope;
import de.tuberlin.pserver.dsl.state.RemoteUpdate;
import de.tuberlin.pserver.dsl.state.SharedState;
import de.tuberlin.pserver.dsl.state.StateMerger;
import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.math.matrix.dense.DMatrix;
import de.tuberlin.pserver.math.vector.Vector;
import de.tuberlin.pserver.math.vector.dense.DVector;
import de.tuberlin.pserver.runtime.MLProgram;
import de.tuberlin.pserver.runtime.filesystem.record.config.RowRecordFormatConfig;
import de.tuberlin.pserver.runtime.state.merger.MatrixUpdateMerger;

import java.util.Random;

public class Kmeans extends MLProgram {

    private static final long ROWS = 1000;
    private static final long COLS = 2;
    private static final int K = 3;

    // loaded by pserver
    private static final String FILE = "datasets/stripes3.csv";


    @SharedState(
            globalScope = GlobalScope.PARTITIONED,
            rows = ROWS,
            cols = COLS,
            path = FILE,
            format = Format.DENSE_FORMAT,
            recordFormat = RowRecordFormatConfig.class
    )
    public Matrix matrix;

    @SharedState(
            globalScope = GlobalScope.REPLICATED,
            rows = K,
            cols = COLS + 1,
            remoteUpdate = RemoteUpdate.SIMPLE_MERGE_UPDATE
    )
    public Matrix centroidsUpdate;

    @StateMerger(stateObjects = "centroidsUpdate")
    public final MatrixUpdateMerger centroidsUpdateMerger = (i, j, val, remoteVal) -> val + remoteVal;


    @Override
    public void define(Program program) {

//        Random rand = new Random(42);
//        double[] data = new double[(int)(K * COLS)];
//        for (int i = 0; i < K * COLS; i++) {
//            data[i] = rand.nextDouble();
//        }
        final Matrix centroids = new DMatrix(K, COLS, new double[] {0, -1, 0, 0, 0, 1});


        program.initialize(() -> {
            for (int i = 0; i < K; i++) {
                int nodeId = slotContext.programContext.runtimeContext.nodeID;
                System.out.println("centroid[node:"+nodeId+",row:"+i+"]="+centroids.rowAsVector(i));
            }
        });



        program.process(() -> {

            CF.iterate().exe(10, (iteration) -> {
                Matrix.RowIterator iter = matrix.rowIterator();
                while(iter.hasNext()) {
                    Vector point = iter.asVector();
                    iter.next();
                    double closestDistance = Double.MAX_VALUE;
                    long closestCentroidId = -1;
                    for (long centroidId = 0; centroidId < K; centroidId++) {
                        Vector centroid = centroids.rowAsVector(centroidId);
                        Vector diff = centroid.sub(point);
                        double distance = diff.norm(2);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestCentroidId = centroidId;
                        }
                    }
                    Vector one = new DVector(1, new double[]{1});
                    Vector updateDelta = point.concat(one);
                    Vector update = centroidsUpdate.rowAsVector(closestCentroidId);
                    System.out.println(update);
                    centroidsUpdate.assignRow(closestCentroidId, update.add(updateDelta));
                    System.out.println(update.add(updateDelta));
                }
                //DF.publishUpdate();
                //DF.pullUpdate();
                for (int i = 0; i < K; i++) {
                    if(centroidsUpdate.get(i, COLS) > 0) {
                        System.out.println("centroidsUpdate("+i+")="+centroidsUpdate.rowAsVector(i));
                        centroids.assignRow(i, centroidsUpdate.rowAsVector(i, 0, COLS).div(centroidsUpdate.get(i, COLS)));
                    }
                }
                for (int i = 0; i < K; i++) {
                    int nodeId = slotContext.programContext.runtimeContext.nodeID;
                    System.out.println("centroid[node:"+nodeId+",row:"+i+"]="+centroids.rowAsVector(i));
                }
                centroidsUpdate.assign(0);
            });

        });

        program.postProcess(() -> {
            for (int i = 0; i < K; i++) {
                int nodeId = slotContext.programContext.runtimeContext.nodeID;
                System.out.println("centroid[node:"+nodeId+",row:"+i+"]="+centroids.rowAsVector(i));
            }
        });

    }

    public static void main(String[] args) {
        local();
    }

    public static void cluster() {
        System.setProperty("pserver.profile", "wally");
        PServerExecutor.DISTRIBUTED
                .run(Kmeans.class, 1)
                .done();
    }

    public static void local() {
        System.setProperty("simulation.numNodes", "1");
        PServerExecutor.LOCAL
                .run(Kmeans.class, 1)
                .done();
    }
}

