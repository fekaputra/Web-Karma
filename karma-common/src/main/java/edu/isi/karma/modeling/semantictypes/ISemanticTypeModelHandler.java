package edu.isi.karma.modeling.semantictypes;

import java.util.List;


public interface ISemanticTypeModelHandler {
	public boolean addType(String label, List<String> examples);
	public List<SemanticTypeLabel> predictType(List<String> examples, int numPredictions);
}
