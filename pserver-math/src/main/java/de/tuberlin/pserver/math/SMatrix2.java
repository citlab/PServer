package de.tuberlin.pserver.math;


import de.tuberlin.pserver.math.stuff.Utils;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SMatrix2 extends AbstractMatrix {

    // ---------------------------------------------------
    // Inner Classes.
    // ---------------------------------------------------

    public static final class MtxPos implements Serializable {

        public final long row;

        public final long col;


        public MtxPos(final long row, final long col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(row).append(col).build();
        }

        @Override
        public boolean equals(final Object o) {
            if (o instanceof MtxPos) {
                MtxPos that = (MtxPos) o;
                return (this.row == that.row && this.col == that.col);
            }
            return false;
        }
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final Map<MtxPos, Double> data;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public SMatrix2(final long rows, final long cols, final Layout layout) {
        super(rows, cols, layout);
        this.data = new HashMap<>();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public double get(long row, long col) {
        final Double value = data.get(new MtxPos(row, col));
        return (value == null) ? 0 : value;
    }

    @Override
    public void set(long row, long col, double value) {
        data.put(new MtxPos(row, col), value);
    }

    @Override
    public double atomicGet(long row, long col) {
        return 0;
    }

    @Override
    public void atomicSet(long row, long col, double value) {

    }

    @Override
    public double[] toArray() {
        return new double[0];
    }

    @Override
    public void setArray(double[] data) {

    }

    @Override
    public RowIterator rowIterator() {
        return new SMatrix2RowIterator(this);
    }

    @Override
    public RowIterator rowIterator(int startRow, int endRow) {
        return new SMatrix2RowIterator(this, startRow, endRow);
    }

    @Override
    public Matrix axpy(double alpha, Matrix B) {
        return null;
    }

    @Override
    public Matrix add(Matrix B) {
        return null;
    }

    @Override
    public Matrix sub(Matrix B) {
        return null;
    }

    @Override
    public Matrix mul(Matrix B) {
        return null;
    }

    @Override
    public Vector mul(Vector B) {
        return null;
    }

    @Override
    public void mul(Vector x, Vector y) {

    }

    @Override
    public Matrix scale(double alpha) {
        return null;
    }

    @Override
    public Matrix transpose() {
        return null;
    }

    @Override
    public void transpose(Matrix B) {

    }

    @Override
    public boolean invert() {
        return false;
    }

    @Override
    public Vector rowAsVector() {
        return null;
    }

    @Override
    public Vector rowAsVector(long row) {
        return null;
    }

    @Override
    public Vector rowAsVector(long row, long from, long to) {
        return null;
    }

    @Override
    public Vector colAsVector() {
        return null;
    }

    @Override
    public Vector colAsVector(long col) {
        return null;
    }

    @Override
    public Vector colAsVector(long col, long from, long to) {
        return null;
    }

    @Override
    public Matrix assign(Matrix v) {
        return null;
    }

    @Override
    public Matrix assign(double v) {
        return null;
    }

    @Override
    public Matrix assignRow(long row, Vector v) {
        return null;
    }

    @Override
    public Matrix assignColumn(long col, Vector v) {
        return null;
    }

    @Override
    public Matrix copy() {
        return null;
    }

    public static class SMatrix2RowIterator extends AbstractRowIterator {

        public SMatrix2RowIterator(AbstractMatrix mat) {
            super(mat);
        }

        public SMatrix2RowIterator(AbstractMatrix mat, int startRow, int endRow) {
            super(mat, startRow, endRow);
        }

        @Override
        public Vector getAsVector() {
            return getAsVector(0, Utils.toInt(numCols()));
        }

        @Override
        public Vector getAsVector(int from, int size) {
            System.out.println("curRow:" + currentRow);
            if(from < 0 || size > numCols()) {
                throw new IllegalArgumentException();
            }
            DVector rowVec = new DVector(size, Vector.Layout.ROW_LAYOUT);
            for(long col = from; col < size; col++) {
                double val = target.get(currentRow, col);
                rowVec.set(col, val);
            }
            return rowVec;
        }
    }
}
