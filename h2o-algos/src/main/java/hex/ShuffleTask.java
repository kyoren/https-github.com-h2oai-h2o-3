package hex;

import java.util.Random;
import water.MRTask;
import water.fvec.*;
import static water.util.RandomUtils.getRNG;

/** Simple shuffle task based on Fisher/Yates algo.
 *
 * WARNING: It shuffles data only inside the chunk.
 */
public class ShuffleTask extends MRTask<ShuffleTask> {

  @Override public void map(Chunk ic, Chunk oc) {
    if (ic._len==0) return;
    // Each vector is shuffled in the same way
    Random rng = getRNG(seed(ic.cidx()));
    oc.set(0,ic.atd(0));
    for (int row=1; row<ic._len; row++) {
      int j = rng.nextInt(row+1); // inclusive upper bound <0,row>
      // Arghhh: expand the vector into double
      if (j!=row) oc.set(row, oc.atd(j));
      oc.set(j, ic.atd(row));
    }
  }

  public static long seed(int cidx) { return (0xe031e74f321f7e29L + (cidx << 32L)); }

  public static Vec shuffle(Vec ivec) {
    Vec ovec = ivec.makeZero();
    new ShuffleTask().doAll(ivec, ovec);
    return ovec;
  }
}
