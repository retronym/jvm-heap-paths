package heappaths;

import org.netbeans.lib.profiler.heap.*;

import java.io.File;
import java.util.*;

/**
 * Answers "why is this still alive?" for an .hprof heap dump, from the command line.
 *
 * <pre>
 * heap-paths paths  DUMP CLASS [--limit N] [--max-depth N]
 *     For each instance of CLASS (and subclasses with --subclasses), the chain of referrers to its
 *     nearest GC root, with the field (or array) each reference goes through.
 * heap-paths histo  DUMP CLASS --by FIELD.PATH [--by ...] [--non-null FIELD.PATH]
 *     Count instances of CLASS grouped by the class (or primitive value) found at each field path.
 * </pre>
 *
 * Describers found on the {@code --plugin} classpath add domain-specific labels to each printed instance.
 * Uses the NetBeans profiler heap library, which indexes the dump into {@code DUMP.nbcache} on first use;
 * delete that directory if the dump file is replaced.
 */
public final class Main {
  public static void main(String[] args) throws Exception {
    if (args.length < 3) { usage(); return; }
    String cmd = args[0];
    File dump = new File(args[1]);
    String cls = args[2];
    Map<String, List<String>> opts = options(Arrays.copyOfRange(args, 3, args.length));
    Heap heap = HeapFactory.createHeap(dump);
    JavaClass jc = heap.getJavaClassByName(cls);
    if (jc == null) { System.err.println("no class " + cls + " in " + dump); System.exit(1); }
    List<Instance> instances = new ArrayList<>();
    instances.addAll(jc.getInstances());
    if (opts.containsKey("subclasses"))
      for (Object sub : jc.getSubClasses()) instances.addAll(((JavaClass) sub).getInstances());
    List<Describer> describers = new ArrayList<>();
    ServiceLoader.load(Describer.class).forEach(describers::add);
    switch (cmd) {
      case "paths" -> paths(heap, instances, describers, intOpt(opts, "limit", Integer.MAX_VALUE), intOpt(opts, "max-depth", 1000));
      case "histo" -> histo(instances, opts.getOrDefault("by", List.of()), opts.getOrDefault("non-null", List.of()));
      default -> usage();
    }
  }

  static void paths(Heap heap, List<Instance> instances, List<Describer> ds, int limit, int maxDepth) {
    System.out.println(instances.size() + " instances");
    int n = 0;
    for (Instance i : instances) {
      if (n++ >= limit) break;
      System.out.println("== " + i.getJavaClass().getName() + "#" + i.getInstanceNumber() + label(i, ds));
      Instance cur = i;
      for (int depth = 0; depth < maxDepth; depth++) {
        Instance next = cur.getNearestGCRootPointer();
        if (next == null || next == cur) {
          GCRoot root = heap.getGCRoot(cur);
          System.out.println("   ROOT " + (root == null ? "" : root.getKind()));
          break;
        }
        System.out.println("   <- " + next.getJavaClass().getName() + "." + via(next, cur) + label(next, ds));
        cur = next;
      }
    }
  }

  /** The field of {@code from} that refers to {@code to}, or {@code []} for an array element. */
  static String via(Instance from, Instance to) {
    if (from instanceof ObjectArrayInstance arr) {
      List<?> vals = arr.getValues();
      for (int k = 0; k < vals.size(); k++)
        if (vals.get(k) instanceof Instance v && v.getInstanceId() == to.getInstanceId()) return "[" + k + "]";
      return "[]";
    }
    for (Object o : from.getFieldValues())
      if (o instanceof ObjectFieldValue f && f.getInstance() != null && f.getInstance().getInstanceId() == to.getInstanceId())
        return f.getField().getName();
    return "?";
  }

  static String label(Instance i, List<Describer> ds) {
    if (i.getJavaClass().getName().equals("java.lang.String")) return " \"" + Heaps.string(i) + "\"";
    for (Describer d : ds) {
      try {
        String s = d.describe(i);
        if (s != null) return " {" + s + "}";
      } catch (RuntimeException e) {
        return " {describer failed: " + e + "}";
      }
    }
    return "";
  }

  static void histo(List<Instance> instances, List<String> by, List<String> nonNull) {
    Map<String, Integer> counts = new HashMap<>();
    int total = 0;
    outer:
    for (Instance i : instances) {
      for (String p : nonNull) if (Heaps.path(i, p) == null) continue outer;
      total++;
      StringBuilder key = new StringBuilder();
      for (String p : by) {
        Object v = Heaps.path(i, p);
        String s = v instanceof Instance vi ? Heaps.shortClassName(vi.getJavaClass().getName()) : String.valueOf(v);
        key.append(key.length() == 0 ? "" : "  ").append(p).append('=').append(s);
      }
      counts.merge(key.toString(), 1, Integer::sum);
    }
    System.out.println(total + " of " + instances.size() + " instances");
    counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
      .forEach(e -> System.out.printf("%8d  %s%n", e.getValue(), e.getKey()));
  }

  static Map<String, List<String>> options(String[] args) {
    Map<String, List<String>> m = new HashMap<>();
    for (int k = 0; k < args.length; k++) {
      if (!args[k].startsWith("--")) throw new IllegalArgumentException("unexpected argument " + args[k]);
      String name = args[k].substring(2);
      if (name.equals("subclasses")) { m.put(name, List.of()); continue; }
      m.computeIfAbsent(name, x -> new ArrayList<>()).add(args[++k]);
    }
    return m;
  }

  static int intOpt(Map<String, List<String>> opts, String name, int dflt) {
    List<String> v = opts.get(name);
    return v == null ? dflt : Integer.parseInt(v.get(0));
  }

  static void usage() {
    System.err.println("""
      usage: heap-paths paths DUMP CLASS [--subclasses] [--limit N] [--max-depth N]
             heap-paths histo DUMP CLASS [--subclasses] --by FIELD.PATH [--by ...] [--non-null FIELD.PATH]
      options before the command: --plugin CLASSPATH (describers), -J<jvm-arg>""");
  }
}
