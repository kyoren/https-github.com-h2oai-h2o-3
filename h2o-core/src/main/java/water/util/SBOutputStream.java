package water.util;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import water.H2O;

/** An OutputStream wrapper that allows the use of most functions available in SB */

public final class SBOutputStream extends SBuild {
  public final OutputStream _os;
  public SBOutputStream() { _os = new ByteArrayOutputStream(); }
  public SBOutputStream(OutputStream os) { _os = os; }
  public OutputStream getOutputStream() { return _os; }
  public void closeStream() {
    try {
      _os.close();
    } catch (IOException io) {
      throw H2O.fail("Could not close stream", io);
    }
  }
  public SBOutputStream ps(String s) { p("\""); pj(s); p("\""); return this; }
  public SBOutputStream p(String s) {
    try {
      byte[] bytes = s.getBytes();
      _os.write(bytes, 0, bytes.length);
    return this;
    } catch (IOException io) {
      throw H2O.fail("OutputStream is closed", io);
    }
  }
  
  public SBOutputStream p(float s) {
    if( Float.isNaN(s) )
      p("Float.NaN");
    else if( Float.isInfinite(s) ) {
      p(s > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY");
    } else p(String.valueOf(s));
    return this;
  }

  public SBOutputStream p(double s) {
    if( Double.isNaN(s) )
      p("Double.NaN");
    else if( Double.isInfinite(s) ) {
      p(s > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY");
    } else p(String.valueOf(s));
    return this;
  }

  public SBOutputStream p(char s) { p(String.valueOf(s)); return this; }
  public SBOutputStream p(int s) { p(String.valueOf(s)); return this; }
  public SBOutputStream p(long s) { p(String.valueOf(s)); return this; }
  public SBOutputStream p(boolean s) { p(String.valueOf(s)); return this; }
  // Not spelled "p" on purpose: too easy to accidentally say "p(1.0)" and
  // suddenly call the the autoboxed version.
  public SBOutputStream pobj(Object s) { p(s.toString()); return this; }
  public SBOutputStream i(int d) { for( int i=0; i<d+_indent; i++ ) p("  "); return this; }
  public SBOutputStream i() { return i(0); }
  public SBOutputStream ip(String s) { return i().p(s); }
  public SBOutputStream s() { p(' '); return this; }

  // Java specific append of float
  public SBOutputStream pj(double s) {
    if (Double.isInfinite(s))
      p("Double.").p(s>0? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    else if (Double.isNaN(s))
      p("Double.NaN");
    else
      p(String.valueOf(s));
    return this;
  }
  // Java specific append of float
  public SBOutputStream pj(float s) {
    if (Float.isInfinite(s))
      p("Float.").p(s>0? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    else if (Float.isNaN(s))
      p("Float.NaN");
    else
      p(String.valueOf(s)).p('f');
    return this;
  }

  public SBOutputStream pj(String s) { p(escapeJava(s)); return this; }
  public SBOutputStream p(IcedBitSet ibs) { return ibs.toString(this); }
  // Increase indentation
  public SBOutputStream ii(int i) { _indent += i; return this; }
  // Decrease indentation
  public SBOutputStream di(int i) { _indent -= i; return this; }
  // Copy indent from given string buffer
  public SBOutputStream ci(SBuild sb) { _indent = sb._indent; return this; }
  public SBOutputStream nl() { return p('\n'); }

   public SBOutputStream toJavaStringInit(String[] ss) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ )  p('"').pj(ss[i]).p("\",");
    if( ss.length > 0 ) p('"').pj(ss[ss.length-1]).p('"');
    return p('}');
  }
  public SBOutputStream toJavaStringInit( float[] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) pj(ss[i]).p(',');
    if( ss.length > 0 ) pj(ss[ss.length-1]);
    return p('}');
  }
  public SBOutputStream toJavaStringInit( double[] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) pj(ss[i]).p(',');
    if( ss.length > 0 ) pj(ss[ss.length-1]);
    return p('}');
  }
  public SBOutputStream toJavaStringInit( double[][] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) toJavaStringInit(ss[i]).p(',');
    if( ss.length > 0 ) toJavaStringInit(ss[ss.length-1]);
    return p('}');
  }
  public SBOutputStream toJavaStringInit( double[][][] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) toJavaStringInit(ss[i]).p(',');
    if( ss.length > 0 ) toJavaStringInit(ss[ss.length-1]);
    return p('}');
  }
  public SBOutputStream toJSArray(float[] nums) {
    p('[');
    for (int i=0; i<nums.length; i++) {
      if (i>0) p(',');
      p(nums[i]);
    }
    return p(']');
  }
  public SBOutputStream toJSArray(String[] ss) {
    p('[');
    for (int i=0; i<ss.length; i++) {
      if (i>0) p(',');
      p('"').p(ss[i]).p('"');
    }
    return p(']');
  }

  // Write from a SB
  public SBOutputStream p( SB sb ) { p(sb.toString()); return this;  }
  @Override public String toString() { if (_os instanceof ByteArrayOutputStream) return _os.toString(); throw H2O.fail("Cannot convert to String"); }
  

}
