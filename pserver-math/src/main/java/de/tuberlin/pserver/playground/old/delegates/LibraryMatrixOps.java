package de.tuberlin.pserver.playground.old.delegates;

public interface LibraryMatrixOps<TM, TV> {

    public abstract TM add(final TM B, final TM A);             // A = B + A

    public abstract TM sub(final TM B, final TM A);             // A = B - A

    public abstract TV mul(final TM A, final TV x, final TV y); // y = A * x

    public abstract TM scale(final double alpha, final TM A);   // A = alpha * A

    public abstract TM transpose(final TM A);                   // A = A^T

    public abstract TM transpose(final TM B, final TM A);       // B = A^T

    public abstract boolean invert(final TM A);                 // A = A^-1
}