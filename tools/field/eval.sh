#!/bin/sh
# tools/field/eval.sh - one detector build (jni/pitch.c) against every
# bench there is, in one summary.  Run from the repository root:
#
#   sh tools/field/eval.sh [OUT]       (OUT: build and log dir,
#                                       default tools/field/build/eval)
#   PITCH=other/pitch.c sh tools/field/eval.sh OUT   a variant of it
#
#  bench   tools/test_pitch.c's synthetic cases (TOTAL)
#  sim     tools/sim_organ.c -f new: the summary, follow, the Field
#          scenarios (calibrated on the church runs) and the noise ones
#  field   score.py on the 19 Oct debug recordings (issue #1)
#  runs    run_score.py, the 21 Oct runs replayed with the app's hints
#  warped  run_score.py on tools/field/runs/eval (warp_run.py variants:
#          make them as tools/field/README.md "Warped runs" says)
#
# Needs CC (default gcc; on Windows "python -m ziglang cc" from a venv)
# and Python 3 with numpy (PY).  The data: tools/field/data (run.sh
# fetches the 19 Oct recordings; the runs are issue #4's zips).
set -e
CC=${CC:-gcc}
PY=${PY:-python}
D=tools/field
OUT=${1:-$D/build/eval}
PITCH=${PITCH:-jni/pitch.c}
case $(uname -s) in MINGW*|MSYS*|CYGWIN*) X=.exe ;; *) X= ;; esac
mkdir -p "$OUT"
$CC -O2 -Ijni -o "$OUT/test_pitch$X" tools/test_pitch.c "$PITCH" -lm
$CC -O2 -Ijni -Itools/hostjni -o "$OUT/sim_organ$X" tools/sim_organ.c \
	"$PITCH" jni/strobe.c -lm
TP="$OUT/test_pitch$X"
"$TP" > "$OUT/bench.txt" 2>&1 &
"$OUT/sim_organ$X" -f new > "$OUT/sim.txt" 2>&1 &
$PY $D/score.py $D/data "$TP" > "$OUT/field.txt" 2>&1 &
$PY $D/run_score.py --hints log --test-pitch "$TP" \
	$D/data/runs-2026-10-21 > "$OUT/runs.txt" 2>&1 &
if [ -d $D/runs/eval ]; then
	$PY $D/run_score.py --hints log --test-pitch "$TP" \
		$D/runs/eval > "$OUT/warped.txt" 2>&1 &
fi
wait
echo "bench:  $(grep '^TOTAL' "$OUT/bench.txt")"
grep '^SUMMARY' "$OUT/sim.txt" | sed 's/^/sim:    /' | cut -c1-150
grep '^FOLLOW' "$OUT/sim.txt" | sed 's/^/sim:    /' | cut -c1-150
grep '^Field\|^noise' "$OUT/sim.txt" | sed -e 's/^/sim:    /' \
	-e 's/ |.*| empty/ ... empty/' | cut -c1-110
grep '^  all' "$OUT/field.txt" | sed 's/^/field:  /'
for f in runs warped; do
	[ -f "$OUT/$f.txt" ] || continue
	# the replay's table: its last block, the "all" line
	awk -v t="$f" '/^==/{b=$0} /^  all/{a=$0} END{print t ":   " a}' \
		"$OUT/$f.txt"
done
