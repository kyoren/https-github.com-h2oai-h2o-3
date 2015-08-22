package water.util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SBuild {

  protected int _indent = 0;
  public abstract SBuild ps( String s );
  public abstract SBuild p( String s );
  public abstract SBuild p( float  s );
  public abstract SBuild p( double s );
  public abstract SBuild p( char   s );
  public abstract SBuild p( int    s );
  public abstract SBuild p( long   s );
  public abstract SBuild p( boolean s);
  // Not spelled "p" on purpose: too easy to accidentally say "p(1.0)" and
  // suddenly call the the autoboxed version.
  public abstract SBuild pobj( Object s );
  public abstract SBuild i( int d );
  public abstract SBuild i( );
  public abstract SBuild ip(String s);
  public abstract SBuild s();
  // Java specific append of double
  public abstract SBuild pj( double  s );
  // Java specific append of float
  public abstract SBuild pj( float  s );
  /* Append Java string - escape all " and \ */
  public abstract SBuild pj( String s );
  public abstract SBuild p( IcedBitSet ibs );
  // Increase indentation
  public abstract SBuild ii( int i);
  // Decrease indentation
  public abstract SBuild di( int i);
  // Copy indent from given string buffer
  public abstract SBuild ci( SBuild sb);
  public abstract SBuild nl( );
  // Convert a String[] into a valid Java String initializer
  public abstract SBuild toJavaStringInit( String[] ss );
  public abstract SBuild toJavaStringInit( double[] ss );
  public abstract SBuild toJavaStringInit( double[][] ss );
  public abstract SBuild toJavaStringInit( double[][][] ss );
  public abstract SBuild toJSArray(float[] nums);
  public abstract SBuild toJSArray(String[] ss);

  // Mostly a fail, since we should just dump into the same SB.
  public abstract SBuild p( SB sb );
  @Override public abstract String toString();

    /** Java-string illegal characters which need to be escaped */
  public static final Pattern[] ILLEGAL_CHARACTERS = new Pattern[] { Pattern.compile("\\",Pattern.LITERAL), Pattern.compile("\"",Pattern.LITERAL) };
  public static final String[]  REPLACEMENTS       = new String [] { "\\\\\\\\", "\\\\\"" };

  /** Escape all " and \ characters to provide a proper Java-like string
   * Does not escape unicode characters.
   */
  public static String escapeJava(String s) {
    assert ILLEGAL_CHARACTERS.length == REPLACEMENTS.length;
    for (int i=0; i<ILLEGAL_CHARACTERS.length; i++ ) {
      Matcher m = ILLEGAL_CHARACTERS[i].matcher(s);
      s = m.replaceAll(REPLACEMENTS[i]);
    }
    return s;
  }
}
