package bagaturchess.deeplearning.impl.eval.allfeatures;


import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.search.api.IEvalConfig;
import bagaturchess.search.api.IEvaluator;
import bagaturchess.search.api.IEvaluatorFactory;
import bagaturchess.search.impl.evalcache.IEvalCache;


public class NeuralNetworkEvaluatorFactory implements IEvaluatorFactory {
	
	public NeuralNetworkEvaluatorFactory() {
	}
	
	public IEvaluator create(IBitBoard bitboard, IEvalCache evalCache) {
		return new NeuralNetworkEvaluator(bitboard, evalCache, null);
	}
	
	public IEvaluator create(IBitBoard bitboard, IEvalCache evalCache, IEvalConfig evalConfig) {
		return new NeuralNetworkEvaluator(bitboard, evalCache, evalConfig);
	}
	
}
