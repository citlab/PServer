package de.tuberlin.pserver.examples.playground;


import de.tuberlin.pserver.app.PServerJob;
import de.tuberlin.pserver.client.PServerExecutor;
import de.tuberlin.pserver.math.DMatrix;

import java.text.DecimalFormat;

public final class ThreadedMatrixIterationTestJob extends PServerJob {

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public void prologue() {
        dataManager.loadDMatrix("datasets/demo_dataset.csv");
    }

    @Override
    public void compute() {

        final DMatrix data = dataManager.getLocalMatrix("demo_dataset.csv");

        final DMatrix.RowIterator iter = dataManager.threadPartitionedRowIterator(data);

        final DecimalFormat numberFormat = new DecimalFormat("###.###");

        if (ctx.instanceID == 0) {
            while (iter.hasNextRow()) {
                iter.nextRow();
                for (int i = 0; i < iter.numCols(); ++i) {
                    System.out.print("THREAD ID [" + ctx.threadID + "] " + numberFormat.format(iter.getValueOfColumn(i)) + "\t\t");
                }
                System.out.println();
            }
        }
    }

    // ---------------------------------------------------
    // Entry Point.
    // ---------------------------------------------------

    public static void main(final String[] args) {
        PServerExecutor.LOCAL
                .run(ThreadedMatrixIterationTestJob.class, 3)
                .done();

    }
}