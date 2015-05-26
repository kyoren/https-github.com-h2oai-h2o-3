package water.api;

import water.H2O;

public class GarbageCollectHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GarbageCollectV3 gc(int version, GarbageCollectV3 s) {
    H2O.gc();
    return s;
  }
}
