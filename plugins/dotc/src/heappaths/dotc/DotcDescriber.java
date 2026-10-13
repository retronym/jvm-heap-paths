package heappaths.dotc;

import heappaths.Describer;
import org.netbeans.lib.profiler.heap.Instance;

import static heappaths.Heaps.*;

/**
 * Labels Scala 3 compiler (dotc) objects: a Run with its run id, a Symbol or Denotation with its name,
 * owner and the run of its last denotation, a Context with its run id and phase id. Run ids come from
 * {@code Period} codes (run id in the high bits, phase ids below, PhaseWidth = 8).
 */
public final class DotcDescriber implements Describer {
  private static final String CORE = "dotty.tools.dotc.core.";

  @Override public String describe(Instance i) {
    String c = i.getJavaClass().getName();
    if (c.equals("dotty.tools.dotc.Run")) {
      Instance ctx = instanceField(i, "myCtx");
      return ctx == null ? "runContext=null (reset)" : "runId=" + runId(field(ctx, "_period"));
    }
    if (c.equals(CORE + "Contexts$FreshContext") || c.equals(CORE + "Contexts$InitialContext")) {
      Object p = field(i, "_period");
      return p == null ? null : "runId=" + runId(p) + " phaseId=" + phaseId(p);
    }
    if (c.startsWith(CORE + "Symbols$")) return symbol(i);
    if (c.startsWith(CORE + "SymDenotations$") && field(i, "name") != null)
      return denotation(i) + " owner=" + symbol(instanceField(i, "maybeOwner"));
    if (c.startsWith(CORE + "Names$")) return name(i);
    return null;
  }

  static String symbol(Instance s) {
    if (s == null) return "null";
    Instance d = instanceField(s, "lastDenot");
    return d == null ? "?" : denotation(d);
  }

  static String denotation(Instance d) {
    return name(instanceField(d, "name")) + "@run" + runId(field(d, "myValidFor"));
  }

  static String name(Instance n) {
    if (n == null) return "?";
    String c = n.getJavaClass().getName();
    if (c.endsWith("Names$SimpleName")) return string(instanceField(n, "value"));
    if (c.endsWith("Names$TypeName")) return name(instanceField(n, "toTermName")) + "(type)";
    if (c.endsWith("Names$DerivedName")) {
      Instance info = instanceField(n, "info");
      String kind = info == null ? "?" : info.getJavaClass().getName().replaceAll(".*[.$]", "");
      return name(instanceField(n, "underlying")) + "[" + kind + "]";
    }
    return c;
  }

  static String runId(Object period) { return period == null ? "?" : String.valueOf(Integer.parseInt(period.toString()) >>> 16); }
  static String phaseId(Object period) { return period == null ? "?" : String.valueOf((Integer.parseInt(period.toString()) >>> 8) & 0xff); }
}
