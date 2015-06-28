package hex.tree;

import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.IcedBitSet;
import water.util.MathUtils;

/** A Histogram, computed in parallel over a Vec.
 *
 *  <p>Sums and sums-of-squares of floats
 *
 *  @author Cliff Click
 */
public class DRealHistogram extends DHistogram<DRealHistogram> {
  private float _sums[], _ssqs[]; // Sums & square-sums, shared, atomically incremented

  public DRealHistogram(String name, final int nbins, int nbins_cats, byte isInt, float min, float maxEx) {
    super(name,nbins, nbins_cats, isInt, min, maxEx);
  }

  @Override public double mean(int b) {
    double n = _bins[b];
    return n>0 ? (double)_sums[b]/n : 0;
  }
  @Override public double var (int b) {
    double n = _bins[b];
    if( n==0 ) return 0;
    return ((double)_ssqs[b] - (double)_sums[b]*_sums[b]/n)/(n-1);
  }

  // Big allocation of arrays
  @Override void init0() {
    _sums = MemoryManager.malloc4f(_nbin);
    _ssqs = MemoryManager.malloc4f(_nbin);
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute response mean & variance.
  // Done racily instead F/J map calls, so atomic
  @Override void incr0( int b, float y, float w ) {
    AtomicUtils.FloatArray.add(_sums,b,w*y);
    AtomicUtils.FloatArray.add(_ssqs,b,w*y*y);
  }
  // Same, except square done by caller
  void incr1( int b, float y, float yy) {
    AtomicUtils.FloatArray.add(_sums,b,y);
    AtomicUtils.FloatArray.add(_ssqs,b,yy);
  }

  // Merge two equal histograms together.
  // Done in a F/J reduce, so no synchronization needed.
  @Override void add0( DRealHistogram dsh ) {
    ArrayUtils.add(_sums,dsh._sums);
    ArrayUtils.add(_ssqs,dsh._ssqs);
  }

  // Compute a "score" for a column; lower score "wins" (is a better split).
  // Score is the sum of the MSEs when the data is split at a single point.
  // mses[1] == MSE for splitting between bins  0  and 1.
  // mses[n] == MSE for splitting between bins n-1 and n.
  @Override public DTree.Split scoreMSE( int col, double min_rows ) {
    final int nbins = nbins();
    assert nbins > 1;

    // Histogram arrays used for splitting, these are either the original bins
    // (for an ordered predictor), or sorted by the mean response (for an
    // unordered predictor, i.e. categorical predictor).
    float[] sums = _sums;
    float[] ssqs = _ssqs;
    float[] bins = _bins;
    int idxs[] = null;          // and a reverse index mapping

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.  Currently limited to enums
    // where we're one-per-bin.  No point for 3 or fewer bins as all possible
    // combinations (just 3) are tested without needing to sort.
    if( _isInt == 2 && _step == 1.0f && nbins >= 4 ) {
      // Sort the index by average response
      idxs = MemoryManager.malloc4(nbins+1); // Reverse index
      for( int i=0; i<nbins+1; i++ ) idxs[i] = i;
      final float[] avgs = MemoryManager.malloc4f(nbins+1);
      for( int i=0; i<nbins; i++ ) avgs[i] = _bins[i]==0 ? 0 : _sums[i]/_bins[i]; // Average response
      avgs[nbins] = Float.MAX_VALUE;
      ArrayUtils.sort(idxs, avgs);
      // Fill with sorted data.  Makes a copy, so the original data remains in
      // its original order.
      sums = MemoryManager.malloc4f(nbins);
      ssqs = MemoryManager.malloc4f(nbins);
      bins = MemoryManager.malloc4f(nbins);
      for( int i=0; i<nbins; i++ ) {
        sums[i] = _sums[idxs[i]];
        ssqs[i] = _ssqs[idxs[i]];
        bins[i] = _bins[idxs[i]];
      }
    }

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    float sums0[] = MemoryManager.malloc4f(nbins+1);
    float ssqs0[] = MemoryManager.malloc4f(nbins+1);
    float   ns0[] = MemoryManager.malloc4f(nbins+1);
    for( int b=1; b<=nbins; b++ ) {
      float m0 = sums0[b-1],  m1 = sums[b-1];
      float s0 = ssqs0[b-1],  s1 = ssqs[b-1];
      float k0 = ns0  [b-1],  k1 = bins[b-1];
      if( k0==0 && k1==0 ) continue;
      sums0[b] = m0+m1;
      ssqs0[b] = s0+s1;
      ns0  [b] = k0+k1;
    }
    float tot = ns0[nbins];
    // Is any split possible with at least min_obs?
    if( tot < 2*min_rows ) return null;
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    double var = (double)ssqs0[nbins]*tot - (double)sums0[nbins]*sums0[nbins];
    if( var == 0 ) { assert isConstantResponse(); return null; }
    // If variance is really small, then the predictions (which are all at
    // single-precision resolution), will be all the same and the tree split
    // will be in vain.
    if( ((float)var) == 0f ) return null;

    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    float sums1[] = MemoryManager.malloc4f(nbins+1);
    float ssqs1[] = MemoryManager.malloc4f(nbins+1);
    float   ns1[] = MemoryManager.malloc4f(nbins+1);
    for( int b=nbins-1; b>=0; b-- ) {
      float m0 = sums1[b+1], m1 = sums[b];
      float s0 = ssqs1[b+1], s1 = ssqs[b];
      float k0 = ns1  [b+1], k1 = bins[b];
      if( k0==0 && k1==0 ) continue;
      sums1[b] = m0+m1;
      ssqs1[b] = s0+s1;
      ns1  [b] = k0+k1;
      assert MathUtils.compare(ns0[b]+ns1[b],tot,1e-5,1e-5);
    }

    // Now roll the split-point across the bins.  There are 2 ways to do this:
    // split left/right based on being less than some value, or being equal/
    // not-equal to some value.  Equal/not-equal makes sense for categoricals
    // but both splits could work for any integral datatype.  Do the less-than
    // splits first.
    int best=0;                         // The no-split
    float best_se0=Float.MAX_VALUE;   // Best squared error
    float best_se1=Float.MAX_VALUE;   // Best squared error
    byte equal=0;                       // Ranged check
    for( int b=1; b<=nbins-1; b++ ) {
      if( bins[b] == 0 ) continue; // Ignore empty splits
      if( ns0[b] < min_rows ) continue;
      if( ns1[b] < min_rows ) break; // ns1 shrinks at the higher bin#s, so if it fails once it fails always
      // We're making an unbiased estimator, so that MSE==Var.
      // Then Squared Error = MSE*N = Var*N
      //                    = (ssqs/N - mean^2)*N
      //                    = ssqs - N*mean^2
      //                    = ssqs - N*(sum/N)(sum/N)
      //                    = ssqs - sum^2/N
      float se0 = ssqs0[b] - sums0[b]*sums0[b]/ns0[b];
      float se1 = ssqs1[b] - sums1[b]*sums1[b]/ns1[b];
      if( se0 < 0 ) se0 = 0;    // Roundoff error; sometimes goes negative
      if( se1 < 0 ) se1 = 0;    // Roundoff error; sometimes goes negative
      if( (se0+se1 < best_se0+best_se1) || // Strictly less error?
          // Or tied MSE, then pick split towards middle bins
          (se0+se1 == best_se0+best_se1 &&
           Math.abs(b -(nbins>>1)) < Math.abs(best-(nbins>>1))) ) {
        best_se0 = se0;   best_se1 = se1;
        best = b;
      }
    }

    // If the bin covers a single value, we can also try an equality-based split
    if( _isInt > 0 && _step == 1.0f &&    // For any integral (not float) column
        _maxEx-_min > 2 && idxs==null ) { // Also need more than 2 (boolean) choices to actually try a new split pattern
      for( int b=1; b<=nbins-1; b++ ) {
        if( bins[b] < min_rows ) continue; // Ignore too small splits
        float N = ns0[b] + ns1[b+1];
        if( N < min_rows ) continue; // Ignore too small splits
        float sums2 = sums0[b  ]+sums1[b+1];
        float ssqs2 = ssqs0[b  ]+ssqs1[b+1];
        float si =    ssqs2     -sums2  *sums2  /   N   ; // Left+right, excluding 'b'
        float sx =    ssqs [b]  -sums[b]*sums[b]/bins[b]; // Just 'b'
        if( si < 0 ) si = 0;    // Roundoff error; sometimes goes negative
        if( sx < 0 ) sx = 0;    // Roundoff error; sometimes goes negative
        if( si+sx < best_se0+best_se1 ) { // Strictly less error?
          best_se0 = si;   best_se1 = sx;
          best = b;        equal = 1; // Equality check
        }
      }
    }

    // For categorical (unordered) predictors, we sorted the bins by average
    // prediction then found the optimal split on sorted bins
    IcedBitSet bs = null;       // In case we need an arbitrary bitset
    if( idxs != null ) {        // We sorted bins; need to build a bitset
      int min=Integer.MAX_VALUE;// Compute lower bound and span for bitset
      int max=Integer.MIN_VALUE;
      for( int i=best; i<nbins; i++ ) {
        min=Math.min(min,idxs[i]);
        max=Math.max(max,idxs[i]);
      }
      bs = new IcedBitSet(max-min+1,min); // Bitset with just enough span to cover the interesting bits
      for( int i=best; i<nbins; i++ ) bs.set(idxs[i]); // Reverse the index then set bits
      equal = (byte)(bs.max() <= 32 ? 2 : 3); // Flag for bitset split; also check max size
    }

    if( best==0 ) return null;  // No place to split
    float se = ssqs1[0] - sums1[0]*sums1[0]/ns1[0]; // Squared Error with no split
    if( se <= best_se0+best_se1) return null; // Ultimately roundoff error loses, and no split actually helped
    float n0 = equal != 1 ?   ns0[best] :   ns0[best]+  ns1[best+1];
    float n1 = equal != 1 ?   ns1[best] :  bins[best]              ;
    float p0 = equal != 1 ? sums0[best] : sums0[best]+sums1[best+1];
    float p1 = equal != 1 ? sums1[best] :  sums[best]              ;
    if( MathUtils.equalsWithinOneSmallUlp((p0/n0),(p1/n1)) ) return null; // No difference in predictions, which are all at 1 float ULP
    return new DTree.Split(col,best,bs,equal,se,best_se0,best_se1,n0,n1,p0/n0,p1/n1);
  }

  @Override public long byteSize0() {
    return 8*2 +                // 2 more internal arrays
      24+_sums.length<<3 +
      24+_ssqs.length<<3 ;
  }
}
