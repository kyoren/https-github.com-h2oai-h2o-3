package hex.singlenoderf;

import hex.*;
import hex.schemas.SpeeDRFModelV2;
import water.*;
import water.api.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ModelUtils;

import java.util.Arrays;
import java.util.Random;

import static hex.singlenoderf.VariableImportance.asVotes;
import static water.util.UnsafeUtils.get8;


public class SpeeDRFModel extends SupervisedModel<SpeeDRFModel, SpeeDRFModel.SpeeDRFParameters,SpeeDRFModel.SpeeDRFModelOutput> {

  public static class Counter {
    double _min = Double.MAX_VALUE, _max = Double.MIN_VALUE;
    int    _count;
    double _total;
    public void add(double what) {
      _total += what;
      _min = Math.min(what, _min);
      _max = Math.max(what, _max);
      ++_count;
    }
    public double mean() { return _total / _count; }
    @Override public String toString() {
      return _count==0 ? " / / " : String.format("%4.1f / %4.1f / %4.1f", _min, mean(), _max);
    }
  }

  public static class SpeeDRFParameters extends SupervisedModel.SupervisedParameters {
    public int ntrees   = 50;
    public int mtries = -1;
    public int max_depth = 20;
    public Tree.SelectStatType select_stat_type = Tree.SelectStatType.ENTROPY;
    public Sampling.Strategy sampling_strategy = Sampling.Strategy.RANDOM;
    public double sample_rate = 0.67;
    public boolean score_each_iteration = false;
    public boolean oobee = true;
    public boolean importance = false;
    public int nbins = 1024;
    public long seed = -1;
    public boolean verbose = false;
    public int _exclusiveSplitLimit = 0;

    public void validate(SpeeDRF builder, boolean expensive) {
      assert 0 <= ntrees && ntrees < 1000000; // Sanity check
      Vec response  = train().vec(_response_column);
      // Not enough rows to run
      if (train().numRows() - response.naCnt() <= 0)
        throw new IllegalArgumentException("Dataset contains too many NAs!");

      if (!_convert_to_enum && (!(response.isEnum() || response.isInt())))
        throw new IllegalArgumentException("Classification cannot be performed on a float column!");

      if (_convert_to_enum) {
        if (0.0f > sample_rate || sample_rate > 1.0f)
          throw new IllegalArgumentException("Sampling rate must be in [0,1] but found " + sample_rate);
      }

      if (!_convert_to_enum) throw new IllegalArgumentException("SpeeDRF does not currently support regression.");

//      if (arg._name.equals("classification")) {
//        arg._hideInQuery = true;
//      }
//
//      if (arg._name.equals("balance_classes")) {
//        arg.setRefreshOnChange();
//        if(regression) {
//          arg.disable("Class balancing is only for classification.");
//        }
//      }
//
//      // Regression is selected if classification is false and vice-versa.
//      if (arg._name.equals("classification")) {
//        regression = !this.classification;
//      }
//
//      // Regression only accepts the MSE stat type.
//      if (arg._name.equals("select_stat_type")) {
//        if(regression) {
//          arg.disable("Minimize MSE for regression.");
//        }
//      }
//
//      // Class weights depend on the source data set an response value to be specified and are invalid for regression
//      if (arg._name.equals("class_weights")) {
//        if (source == null || response == null) {
//          arg.disable("Requires source and response to be specified.");
//        }
//        if (regression) {
//          arg.disable("No class weights for regression.");
//        }
//      }
//
//      // Prevent Stratified Local when building regression tress.
//      if (arg._name.equals("sampling_strategy")) {
//        arg.setRefreshOnChange();
//        if (regression) {
//          arg.disable("Random Sampling for regression trees.");
//        }
//      }
//
//      // Variable Importance disabled in SpeeDRF regression currently
//      if (arg._name.equals("importance")) {
//        if (regression) {
//          arg.disable("Variable Importance not supported in SpeeDRF regression.");
//        }
//      }
//
//      // max balance size depends on balance_classes to be enabled
//      if(classification) {
//        if(arg._name.equals("max_after_balance_size") && !balance_classes) {
//          arg.disable("Requires balance classes flag to be set.", inputArgs);
//        }
//      }
    }
  }

  public static class SpeeDRFModelOutput extends SupervisedModel.SupervisedOutput {
    public SpeeDRFModelOutput() { super(); }
  }

  // Default publicly visible Schema is V2
  public ModelSchema schema() { return new SpeeDRFModelV2(); }

  private float _ss; private float _cnt;
  /**
   * Extra helper variables.
   */
  private transient VariableImportance.TreeMeasures[/*features*/] _treeMeasuresOnOOB;
  // Tree votes/SSE per individual features on permutated OOB rows
  private transient VariableImportance.TreeMeasures[/*features*/] _treeMeasuresOnSOOB;

  float sample;
  Key[] t_keys;
  Key[][] local_forests;
  transient byte[][] trees;
  Tree.StatType statType;
  double[] errs;
  public Key[/*ntree*/][/*nclass*/] dtreeKeys;
  public String[] verbose_output;
  ConfusionMatrix[] cms;
  int[] node_split_features;
  String current_status;

  public SpeeDRFModel(Key selfKey, SpeeDRFParameters parms, SpeeDRFModelOutput output) {
    super(selfKey, parms, output);
  }

  protected SpeeDRFModel(SpeeDRFModel model) {
    super(model._key, model._parms, model._output);

//    this.features = model.features;
//    this.sampling_strategy = model.sampling_strategy;
    this.sample = model.sample;
//    this.strata_samples = model.strata_samples;
//    this.mtry = model.mtry;
    this.node_split_features = model.node_split_features;
//    this.N = model.N;
//    this.max_depth = model.max_depth;
    this.t_keys = model.t_keys;
    this.local_forests = model.local_forests;
//    this.time = model.time;
//    this.weights = model.weights;
//    this.nbins = model.nbins;
    this.trees = model.trees;
//    this.jobKey = model.jobKey;
//    this.dest_key = model.dest_key;
    this.current_status = model.current_status;
    this.errs = model.errs;
    this.statType = model.statType;
//    this.testKey = model.testKey;
//    this.oobee = model.oobee;
//    this.zeed = model.zeed;
//    this.importance = model.importance;
//    this.confusion = model.confusion;
//    this.cms = Arrays.copyOf(model.cms, model.cms.length+1);
//    this.cms[this.cms.length-1] = cm;
//    this.parameters = model.parameters;
//    this.cm = cm._arr;
//    this.treeStats = model.treeStats;
//    this.cmDomain = model.cmDomain;
//    this.validAUC = auc;
//    this.varimp = varimp;
//    this.regression = model.regression;
//    this.score_each = model.score_each;
//    this.cv_error = err;
//    this.verbose = model.verbose;
    this.verbose_output = model.verbose_output;
//    this.useNonLocal = model.useNonLocal;
//    this.errorsPerTree = model.errorsPerTree;
//    this.resp_min = model.resp_min;
//    this.validation = model.validation;
//    this.src_key = model.src_key;
//    this.score_pojo = model.score_pojo;
  }

  public int treeCount() { return t_keys.length; }
  public int size()      { return t_keys.length; }
  public int classes()   { return _output.nclasses(); }

//  @Override public ConfusionMatrix cm() { return validAUC == null ? cms[cms.length-1] : validAUC.CM(); }

  void variableImportanceCalc(Frame fr, Vec modelResp) {
    VarImp varimp = doVarImpCalc(fr, this, modelResp);
    //TODO: Store varimp
  }

  public static SpeeDRFModel make(SpeeDRFModel old, Key tkey, Key dtKey, int nodeIdx, String tString, int tree_id) {

    // Create a new model for atomic update
    SpeeDRFModel m = old.clone();

    // Update the tree keys with the new one (tkey)
    m.t_keys = Arrays.copyOf(old.t_keys, old.t_keys.length + 1);
    m.t_keys[m.t_keys.length-1] = tkey;

    // Update the dtree keys with the new one (dtkey)
    m.dtreeKeys[tree_id][0] = dtKey;

    // Update the local_forests
    m.local_forests[nodeIdx][tree_id] = tkey;

    // Update the treeStrings?
    if (old.verbose_output.length < 2) {
      m.verbose_output = Arrays.copyOf(old.verbose_output, old.verbose_output.length + 1);
      m.verbose_output[m.verbose_output.length - 1] = tString;
    }

    m.errs = Arrays.copyOf(old.errs, old.errs.length+1);
    m.errs[m.errs.length - 1] = -1.0;
    m.cms = Arrays.copyOf(old.cms, old.cms.length+1);
    m.cms[m.cms.length-1] = null;

    return m;
  }

  public String name(int atree) {
    if( atree == -1 ) atree = size();
    assert atree <= size();
    return _key.toString() + "[" + atree + "]";
  }

  /** Return the bits for a particular tree */
  public byte[] tree(int tree_id) {
    byte[][] ts = trees;
    if( ts == null ) trees = ts = new byte[tree_id+1][];
    if( tree_id >= ts.length ) trees = ts = Arrays.copyOf(ts,tree_id+1);
    if( ts[tree_id] == null ) ts[tree_id] = DKV.get(t_keys[tree_id]).memOrLoad();
    return ts[tree_id];
  }

  /** Free all internal tree keys. */
  public Futures delete_impl(Futures fs) {
    for( Key k : t_keys ) DKV.remove(k,fs);
    for (Key[] ka : local_forests) for (Key k : ka) if (k != null) DKV.remove(k, fs);
    return fs;
  }

  /**
   * Classify a row according to one particular tree.
   * @param tree_id  the number of the tree to use
   * @param chunks    the chunk we are using
   * @param row      the row number in the chunk
   * @param modelDataMap  mapping from model/tree columns to data columns
   * @return the predicted response class, or class+1 for broken rows
   */
  public float classify0(int tree_id, Chunk[] chunks, int row, int modelDataMap[], short badrow, boolean regression) {
    return Tree.classify(new AutoBuffer(tree(tree_id)), chunks, row, modelDataMap, badrow, regression);
  }

  private void vote(Chunk[] chks, int row, int modelDataMap[], int[] votes) {
    int numClasses = classes();
    assert votes.length == numClasses + 1 /* +1 to catch broken rows */;
    for( int i = 0; i < treeCount(); i++ )
      votes[(int)classify0(i, chks, row, modelDataMap, (short) numClasses, false)]++;
  }

  public short classify(Chunk[] chks, int row, int modelDataMap[], int[] votes, double[] classWt, Random rand ) {
    // Vote all the trees for the row
    vote(chks, row, modelDataMap, votes);
    return classify(votes, classWt, rand);
  }

  public short classify(int[] votes, double[] classWt, Random rand) {
    // Scale the votes by class weights: it as-if rows of the weighted classes
    // were replicated many times so get many votes.
    if( classWt != null )
      for( int i=0; i<votes.length-1; i++ )
        votes[i] = (int) (votes[i] * classWt[i]);
    // Tally results
    int result = 0;
    int tied = 1;
    for( int i = 1; i < votes.length - 1; i++ )
      if( votes[i] > votes[result] ) { result=i; tied=1; }
      else if( votes[i] == votes[result] ) { tied++; }
    if( tied == 1 ) return (short) result;
    // Tie-breaker logic
    int j = rand == null ? 0 : rand.nextInt(tied); // From zero to number of tied classes-1
    int k = 0;
    for( int i = 0; i < votes.length - 1; i++ )
      if( votes[i]==votes[result] && (k++ >= j) )
        return (short)i;
    throw H2O.unimpl();
  }

  // The seed for a given tree
  long seed(int ntree) { return get8(tree(ntree), 4); }
  // The producer for a given tree
  byte producerId(int ntree) { return tree(ntree)[12]; }

  // Lazy initialization of tree leaves, depth
  private transient Counter _tl, _td;

  /** Internal computation of depth and number of leaves. */
  public void find_leaves_depth() {
//    if( _tl != null ) return;
    _td = new Counter();
    _tl = new Counter();
    for( Key tkey : t_keys ) {
      long dl = Tree.depth_leaves(new AutoBuffer(DKV.get(tkey).memOrLoad()), !_parms._convert_to_enum);
      _td.add((int) (dl >> 32));
      _tl.add((int) dl);
    }
  }
  public Counter leaves() { find_leaves_depth(); return _tl; }
  public Counter depth()  { find_leaves_depth(); return _td; }


  private static int find(String n, String[] names) {
    if( n == null ) return -1;
    for( int j = 0; j<names.length; j++ )
      if( n.equals(names[j]) )
        return j;
    return -1;
  }

  public int[] colMap(Frame df) {
    int res[] = new int[df._names.length]; //new int[names.length];
    for(int i = 0; i < res.length; i++) {
      res[i] = find(df.names()[i], _output._names);
    }
    return res;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override protected float[] score0(double[] data, float[] preds) {
    int numClasses = classes();
    if (numClasses == 1) {
      float p = 0.f;
      for (int i = 0; i < treeCount(); ++i) {
        p += Tree.classify(new AutoBuffer(tree(i)), data, 0.0, true) / (1. * treeCount());
      }
      return new float[]{p};
    } else {
      int votes[] = new int[numClasses + 1/* +1 to catch broken rows */];
      preds = new float[numClasses + 1];
      for( int i = 0; i < treeCount(); i++ ) {
//        DTree.TreeModel.CompressedTree t = UKV.get(dtreeKeys[i][0]);
        votes[(int) Tree.classify(new AutoBuffer(tree(i)), data, numClasses, false)]++;
      }

      float s = 0.f;
      for (int v : votes) s += (float)v;

      if (_parms._balance_classes) {
        for (int i = 0; i  < votes.length - 1; ++i)
          preds[i+1] = ( (float)votes[i] / treeCount());
        return preds;
      }

      for (int i = 0; i  < votes.length - 1; ++i)
        preds[i+1] = ( (float)votes[i] / (float)treeCount());
      preds[0] = ModelUtils.getPrediction(preds, data);
      float[] rawp = new float[preds.length + 1];
      for (int i = 0; i < votes.length; ++i) rawp[i+1] = (float)votes[i];
      return preds;
    }
  }

  protected VarImp doVarImpCalc(final Frame fr, final SpeeDRFModel model, final Vec resp) {
    _treeMeasuresOnOOB  = new VariableImportance.TreeVotes[fr.numCols() - 1];
    _treeMeasuresOnSOOB = new VariableImportance.TreeVotes[fr.numCols() - 1];
    for (int i=0; i<fr.numCols() - 1; i++) _treeMeasuresOnOOB[i] = new VariableImportance.TreeVotes(treeCount());
    for (int i=0; i<fr.numCols() - 1; i++) _treeMeasuresOnSOOB[i] = new VariableImportance.TreeVotes(treeCount());
    final int ncols = fr.numCols();
    final int trees = treeCount();
    for (int i=0; i<ncols - 1; i++) _treeMeasuresOnSOOB[i] = new VariableImportance.TreeVotes(trees);
    Futures fs = new Futures();
    for (int var=0; var<ncols - 1; var++) {
      final int variable = var;
      H2O.H2OCountedCompleter task4var = new H2O.H2OCountedCompleter() {
        @Override public void compute2() {
          VariableImportance.TreeVotes[] cd = VariableImportance.collectVotes(trees, classes(), fr, ncols - 1, sample, variable, model, resp);
          asVotes(_treeMeasuresOnOOB[variable]).append(cd[0]);
          asVotes(_treeMeasuresOnSOOB[variable]).append(cd[1]);
          tryComplete();
        }
      };
      H2O.submitTask(task4var);
      fs.add(task4var);
    }
    fs.blockForPending();

    // Compute varimp for individual features (_ncols)
    final float[] varimp   = new float[ncols - 1]; // output variable importance
    float[] varimpSD = new float[ncols - 1]; // output variable importance sd
    for (int var=0; var<ncols - 1; var++) {
      long[] votesOOB = asVotes(_treeMeasuresOnOOB[var]).votes();
      long[] votesSOOB = asVotes(_treeMeasuresOnSOOB[var]).votes();
      float imp = 0.f;
      float v = 0.f;
      long[] nrows = asVotes(_treeMeasuresOnOOB[var]).nrows();
      for (int i = 0; i < votesOOB.length; ++i) {
        double delta = ((float) (votesOOB[i] - votesSOOB[i])) / (float) nrows[i];
        imp += delta;
        v  += delta * delta;
      }
      imp /= model.treeCount();
      varimp[var] = imp;
      varimpSD[var] = (float)Math.sqrt( (v/model.treeCount() - imp*imp) / model.treeCount() );
    }
    return new VarImp.VarImpMDA(varimp, varimpSD, model.treeCount());
  }

  public static float[] computeVarImpSD(float[][] vote_diffs) {
    float[] res = new float[vote_diffs.length];
    for (int var = 0; var < vote_diffs.length; ++var) {
      float mean_diffs = 0.f;
      float r = 0.f;
      for (float d: vote_diffs[var]) mean_diffs += d / (float) vote_diffs.length;
      for (float d: vote_diffs[var]) {
        r += (d - mean_diffs) * (d - mean_diffs);
      }
      r *= 1.f / (float)vote_diffs[var].length;
      res[var] = (float) Math.sqrt(r);
    }
    return res;
  }
//
//  @Override protected void setCrossValidationError(Job.ValidatedJob job, double cv_error, water.api.ConfusionMatrix cm, AUCData auc, HitRatio hr) {
//    _have_cv_results = true;
//    SpeeDRFModel drfm = ((SpeeDRF)job).makeModel(this, cv_error, cm.cm == null ? null : new ConfusionMatrix(cm.cm, this.nclasses()), this.varimp, auc);
//    drfm._have_cv_results = true;
//    DKV.put(this._key, drfm); //overwrite this model
//  }

}
