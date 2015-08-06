package de.tuberlin.pserver.math.matrix;


import com.google.common.base.Preconditions;
import de.tuberlin.pserver.math.matrix.dense.DMatrix;
import de.tuberlin.pserver.math.matrix.sparse.SMutableMatrix;
import de.tuberlin.pserver.math.utils.Utils;

public class MatrixBuilder {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private long rows, cols;

    private Matrix.Format format;

    private Matrix.Layout layout;

    private boolean mutable;

    private double[] data;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public MatrixBuilder() {
        reset();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public MatrixBuilder dimension(final long rows, final long cols) {
        this.rows = rows;
        this.cols = cols;
        return this;
    }

    public MatrixBuilder format(final Matrix.Format format) {
        this.format = Preconditions.checkNotNull(format);
        return this;
    }

    public MatrixBuilder layout(final Matrix.Layout layout) {
        this.layout = Preconditions.checkNotNull(layout);
        return this;
    }

    public MatrixBuilder mutable(final boolean mutable) {
        this.mutable = mutable;
        return this;
    }

    public MatrixBuilder data(final double[] data) {
        this.data = Preconditions.checkNotNull(data);
        return this;
    }

    public Matrix build() {
        switch (format) {
            case SPARSE_MATRIX:
                if (mutable)
                    return new SMutableMatrix(rows, cols, layout);
                else
                    throw new UnsupportedOperationException("");
            case DENSE_MATRIX:
                if (mutable) {
                    if (data == null) {
                        return new DMatrix(rows, cols, new double[Utils.toInt(rows * cols)], layout);
                    }
                    return new DMatrix(rows, cols, data, layout);
                }
                else
                    throw new UnsupportedOperationException("");
        }
        throw new IllegalStateException();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    private void reset() {
        rows    = -1;
        cols    = -1;
        format  = Matrix.Format.DENSE_MATRIX;
        layout  = Matrix.Layout.ROW_LAYOUT;
        mutable = true;
    }
}