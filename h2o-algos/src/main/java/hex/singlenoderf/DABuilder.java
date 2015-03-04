package hex.singlenoderf;

import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.DKV;
import water.Job;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.Log;

import java.util.ArrayList;


public class DABuilder {
    protected final SpeeDRF.SpeeDRFDriver.DRFParams _rfParams;
    protected final Key _rfModel;

    static DABuilder create(final SpeeDRF.SpeeDRFDriver.DRFParams rfParams, final Key rfModel) {
      switch( rfParams.sampling_strategy ) {
        case RANDOM                :
        default                    : return new DABuilder(rfParams, rfModel);
      }
    }

    DABuilder(final SpeeDRF.SpeeDRFDriver.DRFParams rfparams, final Key rfmodel) { _rfParams = rfparams; _rfModel = rfmodel; }

    final DataAdapter build(Frame fr, boolean useNonLocal) { return inhaleData(fr, useNonLocal); }

    /** Check that we have proper number of valid columns vs. features selected, if not cap*/
    private void checkAndLimitFeatureUsedPerSplit(Frame fr) {
      int validCols = fr.numCols()-1; // for classIdx column
      if (validCols < _rfParams.num_split_features) {
        Log.info("Limiting features from " + _rfParams.num_split_features +
                " to " + validCols + " because there are no more valid columns in the dataset");
        _rfParams.num_split_features= validCols;
      }
    }

    /** Return the number of rows on this node. */
    private int getRowCount(Frame fr) { return (int)fr.numRows(); }

    /** Return chunk index of the first chunk on this node. Used to identify the trees built here.*/
    private long getChunkId(final Frame fr) {
      Key[] keys = new Key[fr.anyVec().nChunks()];
      for(int i = 0; i < fr.anyVec().nChunks(); ++i) {
        keys[i] = fr.anyVec().chunkKey(i);
      }
      for(int i = 0; i < keys.length; ++i) {
        if (keys[i].home()) return i;
      }
      return -99999; //throw new Error("No key on this node");
    }

    private static int find(String n, String[] names) {
      if( n == null ) return -1;
      for( int j = 0; j<names.length; j++ )
        if( n.equals(names[j]) )
          return j;
      return -1;
    }

    public static int[] colMap( String[] frame_names, String[] model_names ) {
      int mapping[] = new int[frame_names.length];
      for( int i = 0; i<mapping.length; i++ )
        mapping[i] = find(frame_names[i],model_names);
      return mapping;
    }

    /** Build data adapter for given frame */
    protected DataAdapter inhaleData(Frame fr, boolean useNonLocal) {
      Log.info("Prepping for data inhale.");
      long id = getChunkId(fr);
      if (id == -99999) {
        return null;
      }
      water.util.Timer t_inhale = new water.util.Timer();
      final SpeeDRFModel rfmodel = DKV.getGet(_rfModel);
      boolean[] _isByteCol = new boolean[fr.numCols()];
      long[] _naCnts = new long[fr.numCols()];
      for (int i = 0; i < _isByteCol.length; ++i) {
        _isByteCol[i] = DataAdapter.isByteCol(fr.vecs()[i], (int)fr.numRows(), i == _isByteCol.length - 1, !rfmodel._parms._convert_to_enum);
        _naCnts[i] = fr.vecs()[i].naCnt();
      }
      // The model columns are dense packed - but there will be columns in the
      // data being ignored.  This is a map from the model's columns to the
      // building dataset's columns.
      final int[] modelDataMap = colMap(fr._names, rfmodel._output._names);
      final int totalRows = getRowCount(fr);
      final DataAdapter dapt = new DataAdapter(fr, rfmodel, modelDataMap,
              totalRows,
              getChunkId(fr),
              _rfParams.seed,
              _rfParams.bin_limit,
              _rfParams.class_weights);
      // Check that we have proper number of valid columns vs. features selected, if not cap.
      checkAndLimitFeatureUsedPerSplit(fr);

      // Collects jobs loading local chunks
      ArrayList<RecursiveAction> dataInhaleJobs = new ArrayList<RecursiveAction>();

      Log.info("\n\nTotal Number of Chunks: " + fr.anyVec().nChunks()+"\n\n");

      int cnter_local = 0;
      int cnter_remote = 0;
      for(int i = 0; i < fr.anyVec().nChunks(); ++i) {
        if (useNonLocal) {
          if (fr.anyVec().chunkKey(i).home()) { cnter_local++; } else { cnter_remote++; }
          dataInhaleJobs.add(loadChunkAction(dapt, fr, i, _isByteCol, _naCnts, !rfmodel._parms._convert_to_enum));
        } else if (fr.anyVec().chunkKey(i).home()) {
          cnter_local++;
          dataInhaleJobs.add(loadChunkAction(dapt, fr, i, _isByteCol, _naCnts, !rfmodel._parms._convert_to_enum));
        }
      }

      Log.info("\n\nTotal local  chunks to load: "+cnter_local+"\n\nTotal remote chunks to load:" +cnter_remote);

      SpeeDRF.SpeeDRFDriver.DRFTask.updateRFModelStatus(_rfModel, "Inhaling Data.");
      Log.info("Beginning Random Forest Inhale.");
      ForkJoinTask.invokeAll(dataInhaleJobs);

      if(dapt._jobKey != null && !Job.isRunning(dapt._jobKey)) throw new Job.JobCancelledException();

      // Shrink data
      dapt.shrink();
      if(dapt._jobKey != null && !Job.isRunning(dapt._jobKey)) throw new Job.JobCancelledException();
      Log.info("Inhale done in " + t_inhale);
      return dapt;
    }

    static RecursiveAction loadChunkAction(final DataAdapter dapt, final Frame fr, final int cidx, final boolean[] isByteCol, final long[] naCnts, boolean regression) {
      return new RecursiveAction() {
        @Override protected void compute() {
          if(dapt._jobKey != null && !Job.isRunning(dapt._jobKey)) throw new Job.JobCancelledException();
          try {
            Chunk[] chks = new Chunk[fr.numCols()];
            int ncolumns = chks.length;
            for(int i = 0; i < chks.length; ++i) {
              chks[i] = fr.vecs()[i].chunkForChunkIdx(cidx);
            }
            for (int j = 0; j < chks[0]._len; ++j) {
              if(dapt._jobKey != null && !Job.isRunning(dapt._jobKey)) throw new Job.JobCancelledException();
              int rowNum = (int)chks[0]._start + j;
              boolean rowIsValid = false;
              for(int c = 0; c < chks.length; ++c) {
                if(naCnts[c] > 0) {
                  if(chks[c].isNA(j)) {
                    if (c == ncolumns - 1) rowIsValid = false;
                    dapt.addBad(rowNum, c); continue;
                  }
                }
                if (isByteCol[c]) {
                  int val = (int)chks[c].at8(rowNum);
                  dapt.add1(val, rowNum, c);
                } else {
                  float f = (float)chks[c].at_abs((long) rowNum);
                  if(!dapt.isValid(c, f)) { dapt.addBad(rowNum, c); continue; }
                  dapt.add(f, rowNum, c);
                }
                if (c != ncolumns - 1) {
                  rowIsValid |= true;
                }

              }
              if (!rowIsValid) dapt.markIgnoredRow(j);
            }
          } catch (Throwable t) {
            //
          }
        }
      };
    }
}
