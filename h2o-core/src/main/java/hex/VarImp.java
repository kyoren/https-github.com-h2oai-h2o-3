package hex;

import water.Iced;
import water.util.ArrayUtils;

public class VarImp extends Iced {
  final public float[] _varimp; // Variable importance of individual variables, unscaled
  final public String[] _names; // Names of variables.
  public VarImp(float[] varimp, String[] names) { _varimp = varimp; _names = names; }
  // Scaled, so largest value is 1.0
  public float[] scaled_values() { return ArrayUtils.div (_varimp.clone(),ArrayUtils.maxValue(_varimp)); }
  // Scaled so all elements total to 100%
  public float[] summary()       { return ArrayUtils.mult(_varimp.clone(),100.0f/ArrayUtils.sum(_varimp));
  }

  /** Variable importance measurement method. */
  enum VarImpMethod {
    PERMUTATION_IMPORTANCE("Mean decrease accuracy"),
    RELATIVE_IMPORTANCE("Relative importance");
    private final String title;
    VarImpMethod(String title) { this.title = title; }
    @Override public String toString() { return title; }
  }

  /** Variable importance measured as mean decrease in accuracy.
   * It provides raw variable importance measures, SD and z-scores. */
  public static class VarImpMDA extends VarImp {

    public final float[]  varimpSD;

    /** Number of trees participating for producing variable importance measurements */
    private final int ntrees;

    public VarImpMDA(float[] varimp, float[] varimpSD, int ntrees) {
      super(varimp,null);
      this.varimpSD = varimpSD;
      this.ntrees = ntrees;
    }

    public float[] z_score() {
      float[] zscores = new float[_varimp.length];
      double rnt = Math.sqrt(ntrees);
      for(int v = 0; v < _varimp.length ; v++) zscores[v] = (float) (_varimp[v] / (varimpSD[v] / rnt));
      return zscores;
    }
  }
}
