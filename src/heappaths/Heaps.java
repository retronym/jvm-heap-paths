package heappaths;

import org.netbeans.lib.profiler.heap.*;

import java.util.List;

/** Small helpers for reading instances in a heap dump, for use by describers. */
public final class Heaps {
  private Heaps() {}

  /** The value of field {@code name} (searching superclasses too): an {@link Instance}, a primitive as a String, or null. */
  public static Object field(Instance i, String name) {
    for (Object o : i.getFieldValues()) {
      FieldValue f = (FieldValue) o;
      if (f.getField().getName().equals(name))
        return (f instanceof ObjectFieldValue of) ? of.getInstance() : f.getValue();
    }
    return null;
  }

  public static Instance instanceField(Instance i, String name) {
    return field(i, name) instanceof Instance r ? r : null;
  }

  /** Follow a dotted field path, e.g. {@code refFn.arg$1}; null if any step is missing. */
  public static Object path(Instance i, String dotted) {
    Object cur = i;
    for (String step : dotted.split("\\.")) {
      if (!(cur instanceof Instance ci)) return null;
      cur = field(ci, step);
    }
    return cur;
  }

  /** The contents of a {@code java.lang.String} instance (Latin-1 or UTF-16 compact strings). */
  public static String string(Instance s) {
    if (s == null) return null;
    if (!(field(s, "value") instanceof PrimitiveArrayInstance a)) return null;
    List<?> vals = a.getValues();
    StringBuilder b = new StringBuilder();
    if (a.getJavaClass().getName().equals("byte[]")) {
      boolean utf16 = "1".equals(String.valueOf(field(s, "coder")));
      if (utf16)
        for (int k = 0; k + 1 < vals.size(); k += 2) {
          int lo = Byte.parseByte((String) vals.get(k)) & 0xff, hi = Byte.parseByte((String) vals.get(k + 1)) & 0xff;
          b.append((char) (lo | hi << 8)); // little-endian on all mainstream JVM platforms
        }
      else for (Object c : vals) b.append((char) (Byte.parseByte((String) c) & 0xff));
    } else for (Object c : vals) b.append(c); // char[] (JDK 8)
    return b.toString();
  }

  /** The class name with the hidden-class suffix of lambdas dropped, so that histograms group them. */
  public static String shortClassName(String name) {
    int k = name.indexOf("$$Lambda");
    return k < 0 ? name : name.substring(0, k + "$$Lambda".length());
  }
}
