# heap-paths

Command-line answers to "why is this object still alive?" for `.hprof` heap dumps, built on the NetBeans profiler heap library (the engine behind VisualVM's heap walker). It's for when you want the answer in a terminal, a script or a transcript rather than in a GUI.

```
bin/heap-paths [--plugin NAME|CLASSPATH]... [-J<jvm-arg>]... paths DUMP CLASS [--subclasses] [--limit N] [--max-depth N]
bin/heap-paths [--plugin NAME|CLASSPATH]... [-J<jvm-arg>]... histo DUMP CLASS [--subclasses] --by FIELD.PATH [--by ...] [--non-null FIELD.PATH]
```

- `paths` prints, for each instance of `CLASS`, the chain of referrers to its nearest GC root, naming the field (or array index) of every link. Strings are shown inline.
- `histo` counts instances of `CLASS` grouped by the class (or primitive value) found at one or more dotted field paths. Lambda classes are grouped regardless of their hidden-class suffix, so `--by refFn --by 'refFn.arg$1'` tells you which closures, capturing what, are keeping things alive.

The script compiles itself on first use (JDK 17+) and fetches the NetBeans library with [coursier](https://get-coursier.io). The library indexes a dump into `DUMP.nbcache/` the first time it reads it; delete that directory if the dump file is replaced.

## Plugins

A plugin is a classpath with implementations of `heappaths.Describer` registered in `META-INF/services/heappaths.Describer`. A describer returns a short label for instances it understands, which is printed next to each link of a path. `--plugin NAME` compiles and loads `plugins/NAME`.

- `plugins/dotc`: the Scala 3 compiler. It labels a `Run` with its run id, a `Context` with run and phase id, and a `Symbol`/`SymDenotation` with its name, owner and the run of its last denotation. Use it to see which compilation run a retained object belongs to.

## Example: compiler runs retained across a long-lived driver

`examples/dotc-run-retention/` is the investigation that motivated this tool. It compiles a corpus N times through one Scala 3 `Driver` root context, as a REPL, IDE or compile server does. After each run it prints the live heap and how many `Run`s are still reachable, and it can dump the heap. See retronym/scala3#17.

```
examples/dotc-run-retention/run-repro.sh ~/code/scala3 path/to/corpus 4 -Drepro.dump=/tmp/runs.hprof
bin/heap-paths --plugin dotc paths /tmp/runs.hprof dotty.tools.dotc.Run
bin/heap-paths histo /tmp/runs.hprof 'dotty.tools.dotc.core.Types$LazyRef' --non-null refFn --by refFn --by 'refFn.arg$1'
```

```
== dotty.tools.dotc.Run#1 {runId=2}
   <- java.lang.Object[].[5]
   <- dotty.tools.dotc.core.Contexts$FreshContext._store {runId=2 phaseId=1}
   <- dotty.tools.dotc.core.Types$LambdaType$IntegrateMap.x$3
   <- dotty.tools.dotc.core.Types$TypeMap$$Lambda+0x00000080014524a8.arg$1
   <- dotty.tools.dotc.core.Types$LazyRef.refFn
   ...
   <- dotty.tools.dotc.core.SymDenotations$SymDenotation.myInfo {<init>@run5 owner=Enum(type)@run5}
   ...
   <- dotty.tools.dotc.core.Definitions.JavaEnumClass$lzy1
   <- dotty.tools.dotc.core.Contexts$ContextBase.definitions
   ROOT Java frame
```

`RunLeakRepro.scala` options: `-Drepro.dump=FILE` writes a heap dump after the last run while the root context is still reachable; `-Drepro.census` reports docstring-map entries for stale symbols; `-Drepro.gadt` reports symbols in the root's and the last run's GADT constraint.
