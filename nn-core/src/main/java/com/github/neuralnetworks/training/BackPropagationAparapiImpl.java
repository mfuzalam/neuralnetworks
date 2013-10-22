package com.github.neuralnetworks.training;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.github.neuralnetworks.architecture.Connections;
import com.github.neuralnetworks.architecture.InputOutputLayers;
import com.github.neuralnetworks.architecture.Layer;
import com.github.neuralnetworks.architecture.Matrix;
import com.github.neuralnetworks.calculation.ConnectionCalculator;
import com.github.neuralnetworks.calculation.LayerCalculatorImpl;
import com.github.neuralnetworks.calculation.neuronfunctions.AparapiWeightedSumByColumns;
import com.github.neuralnetworks.calculation.neuronfunctions.AparapiWeightedSumByRows;
import com.github.neuralnetworks.calculation.neuronfunctions.ConstantConnectionCalculator;
import com.github.neuralnetworks.util.UniqueList;
import com.github.neuralnetworks.util.Util;

/**
 * Aparapi implementation of the backpropagation algorithm
 */
public class BackPropagationAparapiImpl extends LayerCalculatorImpl implements BackPropagation, ConnectionCalculator {

    private static final long serialVersionUID = 1L;

    private Map<Layer, Matrix> results;
    private AparapiRmsDerivativeByRows forward;
    private AparapiRmsDerivativeByColumns backward;

    public BackPropagationAparapiImpl(float learningRate, float momentum) {
	super();
	this.results = new HashMap<Layer, Matrix>();
	forward = new AparapiRmsDerivativeByRows();
	backward = new AparapiRmsDerivativeByColumns();
	forward.learningRate = backward.learningRate = learningRate;
	forward.momentum = backward.momentum = momentum;
    }

    @Override
    public void backPropagate(Map<Layer, Matrix> activations, Matrix outputError, InputOutputLayers layers) {
	Set<Layer> calculatedLayers = new UniqueList<Layer>();
	calculatedLayers.add(layers.getOutputLayer());
	for (Matrix m : results.values()) {
	    Util.fillArray(m.getElements(), 0);
	}

	results.put(layers.getOutputLayer(), outputError);
	forward.activations = backward.activations = activations;

	calculate(calculatedLayers, results, layers.getInputLayer());
    }

    @Override
    protected ConnectionCalculator getConnectionCalculator(Connections connection, Layer layer ){
	return this;
    }

    @Override
    public void calculate(Connections connection, Matrix input, Matrix output, Layer targetLayer) {
	if (connection.getInputLayer().getConnectionCalculator() instanceof ConstantConnectionCalculator) {
	    connection.getInputLayer().getConnectionCalculator().calculate(connection, output, input, targetLayer);
	    backward.calculate(connection, input, output);
	} else if (connection.getOutputLayer().getConnectionCalculator() instanceof ConstantConnectionCalculator) {
	    connection.getOutputLayer().getConnectionCalculator().calculate(connection, output, input, targetLayer);
	    forward.calculate(connection, input, output);
	} else if (targetLayer == connection.getOutputLayer()) {
	    forward.calculate(connection, input, output);
	} else if (targetLayer == connection.getInputLayer()) {
	    backward.calculate(connection, input, output);
	}
    }

    @Override
    public Matrix getOutputErrorDerivative(Matrix activation, Matrix target) {
	if (activation.getElements().length != target.getElements().length || activation.getColumns() != target.getColumns()) {
	    throw new IllegalArgumentException("Matrices are not the same");
	}

	Matrix result = new Matrix(activation);
	for (int i = 0; i < activation.getElements().length; i++) {
	    result.getElements()[i] = (target.getElements()[i] - activation.getElements()[i]) * activation.getElements()[i] * (1 - activation.getElements()[i]);
	}

	return result;
    }

    private static class AparapiRmsDerivativeByRows extends AparapiWeightedSumByRows {

	private static final long serialVersionUID = -5101971690861270462L;

	private float[] outputActivation;
	private float[] weightUpdates;
	private Map<Connections, float[]> storedWeightUpdates = new HashMap<>();
	private float learningRate;
	private float momentum;
	private Map<Layer, Matrix> activations;

	@Override
	protected void init(Connections graph, Matrix inputMatrix, Matrix outputMatrix) {
	    super.init(graph, inputMatrix, outputMatrix);

	    weightUpdates = storedWeightUpdates.get(graph);

	    if (weightUpdates == null) {
		weightUpdates = new float[graph.getConnectionGraph().getElements().length];
		storedWeightUpdates.put(graph, weightUpdates);
	    }

	    outputActivation = activations.get(graph.getOutputLayer()).getElements();
	}

	@Override
	protected void after(int row, int column) {
	    output[outputIndex(row, column)] *= outputActivation[outputIndex(row, column)] * (1 - outputActivation[outputIndex(row, column)]);

	    for (int j = 0; j < weightsColumns; j++) {
		float weightUpdate = learningRate * input[inputIndex(j, column)] * outputActivation[outputIndex(row, column)] + momentum * weightUpdates[weightIndex(row, j)];
		weights[weightIndex(row, j)] += weightUpdate;
		weightUpdates[weightIndex(row, j)] = weightUpdate;
	    }
	}
    }

    private static class AparapiRmsDerivativeByColumns extends AparapiWeightedSumByColumns {

	private static final long serialVersionUID = -5101971690861270462L;

	private float[] outputActivation;
	private Map<Connections, float[]> storedWeightUpdates = new HashMap<>();
	private float[] weightUpdates;
	private float learningRate;
	private float momentum;
	private Map<Layer, Matrix> activations;

	@Override
	protected void init(Connections graph, Matrix inputMatrix, Matrix outputMatrix) {
	    super.init(graph, inputMatrix, outputMatrix);

	    weightUpdates = storedWeightUpdates.get(graph);

	    if (weightUpdates == null) {
		weightUpdates = new float[graph.getConnectionGraph().getElements().length];
		storedWeightUpdates.put(graph, weightUpdates);
	    }

	    outputActivation = activations.get(graph.getInputLayer()).getElements();
	}

// Although wrong this implementation also works
//	@Override
//	protected void before(int row, int column) {
//	    for (int j = 0; j < weightsRows; j++) {
//		float weightUpdate = learningRate * input[inputIndex(j, column)] * outputActivation[inputIndex(row, column)] + momentum * weightUpdates[weightIndex(j, row)];
//		weights[weightIndex(j, row)] += weightUpdate;
//		weightUpdates[weightIndex(j, row)] = weightUpdate;
//	    }
//	}

	@Override
	protected void after(int row, int column) {
	    output[outputIndex(row, column)] *= outputActivation[outputIndex(row, column)] * (1 - outputActivation[outputIndex(row, column)]);

	    for (int j = 0; j < weightsRows; j++) {
		float weightUpdate = learningRate * input[inputIndex(j, column)] * outputActivation[inputIndex(row, column)] + momentum * weightUpdates[weightIndex(j, row)];
		weights[weightIndex(j, row)] += weightUpdate;
		weightUpdates[weightIndex(j, row)] = weightUpdate;
	    }
	}
    }
}