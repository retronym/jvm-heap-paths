#!/bin/bash
# Compile a corpus N times through one Driver root context; print live heap and live Run count per run.
#
# usage: run-repro.sh <scala3-checkout> <corpus-dir> <N> [extra JVM args, e.g. -Drepro.dump=/tmp/x.hprof -Drepro.census -Drepro.gadt]
# Needs: `sbt scala3-compiler-nonbootstrapped/compile` done in <scala3-checkout>.
# Defaults are the scala3-benchmarks scalaz options; override with SCALAC_OPTS.
set -euo pipefail
SCALA3=$(cd "$1" && pwd); CORPUS=$2; N=$3; shift 3
HERE=$(cd "$(dirname "$0")" && pwd)
JAVA=${JAVA:-$HOME/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java}
WORK=${WORK:-$(mktemp -d)}
CP=$(cd "$SCALA3" && sbt --client "export scala3-compiler-nonbootstrapped/Runtime/fullClasspath" 2>&1 \
  | tr -d '\r' | sed 's/\x1b\[[0-9;]*[A-Za-z]//g' | grep '\.jar' | tail -1)
LIB=$(tr ':' '\n' <<<"$CP" | grep 'scala-library-nonbootstrapped/classes')
mkdir -p "$WORK/repro-out" "$WORK/out"
"$JAVA" -cp "$CP" dotty.tools.dotc.Main -classpath "$CP" -d "$WORK/repro-out" "$HERE/RunLeakRepro.scala"
find "$CORPUS" -name '*.scala' -not -path '*/target/*' > "$WORK/sources.txt"
SCALAC_OPTS=${SCALAC_OPTS:--nowarn -source 3.0 -Xkind-projector -language:implicitConversions -feature -deprecation}
exec "$JAVA" -Xmx4g -Xss8m "$@" -cp "$WORK/repro-out:$CP" dotty.tools.dotc.RunLeakRepro "$N" \
  -classpath "$LIB" -d "$WORK/out" $SCALAC_OPTS $(cat "$WORK/sources.txt")
