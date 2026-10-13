package heappaths;

import org.netbeans.lib.profiler.heap.Instance;

/**
 * Adds a short, domain-specific label to an instance printed by heap-paths, e.g. the name and run id
 * of a compiler symbol. Implementations are found with {@link java.util.ServiceLoader} on the classpath
 * passed with {@code --plugin}.
 */
public interface Describer {
  /** A label for {@code i}, or {@code null} if this describer does not know its class. */
  String describe(Instance i);
}
