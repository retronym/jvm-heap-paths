package dotty.tools.dotc

import dotty.tools.dotc.{Compiler, Driver, Run}
import dotty.tools.dotc.core.Contexts.*
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import java.lang.ref.WeakReference

/** Reuse one Driver root context across N runs; report live heap and live Run count.
 *  args: N <scalac args...>
 */
object RunLeakRepro:
  val runs = collection.mutable.ArrayBuffer[WeakReference[Run]]()

  object D extends Driver:
    override protected def newCompiler(using Context): Compiler = new Compiler:
      override def newRun(using Context): Run =
        val r = super.newRun
        runs += WeakReference(r)
        r
    def go(n: Int, args: Array[String]): Unit =
      val Some((files, ctx0)) = setup(args, initCtx.fresh): @unchecked
      val compiler = newCompiler(using ctx0)
      for i <- 1 to n do
        val t0 = System.nanoTime()
        val rep = doCompile(compiler, files)(using ctx0)
        val ms = (System.nanoTime() - t0) / 1000000
        gc()
        val used = usedMB()
        val live = runs.count(_.get() != null)
        if sys.props.contains("repro.census") then census(ctx0)
        if sys.props.contains("repro.gadt") then
          val syms = ctx0.gadt.symbols
          given Context = runs.last.get().runContext
          println(s"  run ctx gadt: symbols=${ctx.gadt.symbols.size} isNarrowing=${ctx.gadt.isNarrowing}; root isNarrowing=${ctx0.gadt.isNarrowing}")
          println(s"  root gadt symbols: ${syms.size} " + syms.take(8).map(s => s"${s.name} owner=${s.maybeOwner.showFullName} ${scala.util.Try(s.sourcePos.toString).getOrElse("?")}"))
        println(f"run $i%2d: ${ms}%6d ms, errors=${rep.errorCount}, live heap=$used%5d MB, live Runs (weak)=$live, histo Run=${histoCount("dotty.tools.dotc.Run")}")
      // dump while the root context and compiler are still reachable
      sys.props.get("repro.dump").foreach { f =>
        ManagementFactory.getPlatformMXBean(classOf[com.sun.management.HotSpotDiagnosticMXBean]).dumpHeap(f, true)
        println(s"dumped $f")
      }
      java.lang.ref.Reference.reachabilityFence(compiler)
      java.lang.ref.Reference.reachabilityFence(ctx0)

  def census(root: Context): Unit =
    import dotty.tools.dotc.core.Comments.docCtx
    import dotty.tools.dotc.core.Symbols.*
    val run = runs.last.get()
    given Context = run.runContext
    for d <- root.docCtx do
      val keys = d.docstrings.keysIterator.toList
      val byRun = keys.groupBy(_.defRunId).view.mapValues(_.size).toList.sorted
      val staleValid = keys.filter(k => k.defRunId < ctx.runId && k.isValidInCurrentRun)
      // top-level keys that are no longer the symbol entered in their package
      val topStale = keys.filter(k => k.owner.is(dotty.tools.dotc.core.Flags.PackageClass) && (k.owner.unforcedDecls.lookup(k.name) ne k))
      val inStalePkgObj = keys.filter(k => k.owner.isPackageObject && (k.owner.owner.unforcedDecls.lookup(k.owner.name) ne k.owner))
      println(s"  topStale=${topStale.size} ${topStale.take(3).map(_.showFullName)} inStalePkgObj=${inStalePkgObj.size} ${inStalePkgObj.take(3).map(_.showFullName)}")
      println(s"  docstrings=${keys.size} byRun=$byRun currentRun=${ctx.runId} oldButValid=${staleValid.size} e.g. ${staleValid.take(5).map(s => s.showFullName + " in " + s.associatedFile)}")

  def gc(): Unit = for _ <- 1 to 3 do { System.gc(); Thread.sleep(50) }
  def usedMB(): Long =
    val m = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
    m.getUsed / (1024 * 1024)
  def histoCount(cls: String): Long =
    val server = ManagementFactory.getPlatformMBeanServer
    val out = server.invoke(ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
      Array[AnyRef](Array.empty[String]), Array("[Ljava.lang.String;")).asInstanceOf[String]
    out.linesIterator.map(_.trim.split("\\s+")).collectFirst {
      case a if a.length >= 4 && a(3) == cls => a(1).toLong
    }.getOrElse(0L)

  def main(args: Array[String]): Unit =
    D.go(args(0).toInt, args.drop(1))
