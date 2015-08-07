package water.util;

import jsr166y.CountedCompleter;
import water.H2O;
import water.Job;
import water.Key;
import water.api.FramesHandler;
import water.exceptions.H2OParseException;
import water.fvec.Frame;
import water.persist.PersistManager;

import java.io.InputStream;
import java.io.OutputStream;

public class ExportDataset extends Job<Frame> {

  private ExportDataset(Key dest) {
    super(dest, "Export");
  }

  public static ExportDataset export(Frame fr, String path, String frameName, boolean force) {
    InputStream is = (fr).toCSV(true, false);
    ExportDataset job = new ExportDataset(null);
    ExportTask t = new ExportTask(is, path, frameName, force, job);
    job.start(t, fr.anyVec().nChunks(), true);
    return job;
  }

  private static class ExportTask extends H2O.H2OCountedCompleter<ExportTask> {
    final InputStream _csv;
    final String _path;
    final String _frameName;
    final boolean _force;
    final Job _j;

    ExportTask(InputStream csv, String path, String frameName, boolean force, Job j) {
      _csv = csv;
      _path = path;
      _frameName = frameName;
      _force = force;
      _j = j;
    }

    private void copyStream(OutputStream os, final int buffer_size) {
      int curIdx = 0;
      try {
        byte[] bytes = new byte[buffer_size];
        for (; ; ) {
          int count = _csv.read(bytes, 0, buffer_size);
          if (count <= 0) break;
          os.write(bytes, 0, count);
          int workDone = ((Frame.CSVStream) _csv)._curChkIdx;
          if (curIdx != workDone) {
            _j.update(workDone - curIdx);
            curIdx = workDone;
          }
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public void compute2() {
      PersistManager pm = H2O.getPM();
      OutputStream os = null;
      try {
        os = pm.create(_path, _force);
        copyStream(os, 4 * 1024 * 1024);
      } finally {
        if (os != null) {
          try {
            os.close();
            Log.info("Key '" + _frameName + "' was written to " + _path + ".");
          } catch (Exception e) {
            Log.err(e);
          }
        }
      }
      tryComplete();
    }

    // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      if (_j != null) {
        _j.cancel();
        if (ex instanceof H2OParseException) throw (H2OParseException) ex;
        else _j.failed(ex);
      }
      return true;
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
      _j.done();
    }
  }
}
