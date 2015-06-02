package de.tuberlin.pserver.ml.playground.mahout;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Implements the Laplacian or bi-exponential prior.  This prior has a strong tendency to set coefficients to zero
 * and thus is useful as an alternative to variable selection.  This version implements truncation which prevents
 * a coefficient from changing sign.  If a correction would change the sign, the coefficient is truncated to zero.
 *
 * Note that it doesn't matter to have a scale for this distribution because after taking the derivative of the logP,
 * the lambda coefficient used to combine the prior with the observations has the same effect.  If we had a scale here,
 * then it would be the same effect as just changing lambda.
 */
public class L1 implements PriorFunction {
	@Override
	public double age(double oldValue, double generations, double learningRate) {
		double newValue = oldValue - Math.signum(oldValue) * learningRate * generations;
		if (newValue * oldValue < 0) {
			// don't allow the value to change sign
			return 0;
		} else {
			return newValue;
		}
	}

	@Override
	public double logP(double betaIJ) {
		return -Math.abs(betaIJ);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		// stateless class has nothing to serialize
	}

	@Override
	public void readFields(DataInput dataInput) throws IOException {
		// stateless class has nothing to serialize
	}
}
