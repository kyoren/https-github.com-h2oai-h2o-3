package hex.schemas;


import hex.singlenoderf.Sampling;
import hex.singlenoderf.SpeeDRF;
import hex.singlenoderf.Tree;
import water.api.API;

public class SpeeDRFV2 extends SupervisedModelBuilderSchema<SpeeDRF,SpeeDRFV2,SpeeDRFV2.SpeeDRFParametersV2> {

  public static final class SpeeDRFParametersV2 extends SupervisedModelParametersSchema<SpeeDRFParameters, SpeeDRFParametersV2> {
    static public String[] own_fields = new String[] {
        "ntrees",
        "mtries",
        "max_depth",
        "select_stat_type",
        "sampling_strategy",
        "sample_rate",
        "oobee",
        "nbins",
        "seed"
    };

    @API(help = "Number of trees", level = API.Level.critical, direction=API.Direction.INOUT)
    public int ntrees   = 50;

    @API(help = "Number of features to randomly select at each split.", level = API.Level.critical, direction=API.Direction.INOUT)
    public int mtries = -1;

    @API(help = "Max Depth", level = API.Level.critical, direction=API.Direction.INOUT)
    public int max_depth = 20;

    @API(help = "Split Criterion Type", level = API.Level.critical, direction=API.Direction.INOUT)
    public Tree.SelectStatType select_stat_type = Tree.SelectStatType.ENTROPY;

    @API(help = "Sampling Strategy", level = API.Level.critical, direction=API.Direction.INOUT)
    public Sampling.Strategy sampling_strategy = Sampling.Strategy.RANDOM;

    @API(help = "Sampling Rate at each split.", level = API.Level.critical, direction=API.Direction.INOUT)
    public double sample_rate = 0.67;

    //  @API(help ="Score each iteration", level = API.Level.critical, direction=API.Direction.INOUT)
    public boolean score_each_iteration = false;

    @API(help = "Out of bag error estimate",  level = API.Level.critical, direction=API.Direction.INOUT)
    public boolean oobee = true;

    @API(help = "Variable Importance",  level = API.Level.critical, direction=API.Direction.INOUT)
    public boolean importance = false;

    @API(help = "bin limit",  level = API.Level.critical, direction=API.Direction.INOUT)
    public int nbins = 1024;

    @API(help = "seed",  level = API.Level.critical, direction=API.Direction.INOUT)
    public long seed = -1;

    @API(help = "Tree splits and extra statistics printed to stdout.",  level = API.Level.critical, direction=API.Direction.INOUT)
    public boolean verbose = false;

    @API(help = "split limit", level = API.Level.critical, direction=API.Direction.INOUT)
    public int _exclusiveSplitLimit = 0;
  }
}
