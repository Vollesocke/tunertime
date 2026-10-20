#!/bin/sh
# tools/field/run.sh - the field recordings as a regression test for the
# detector.  Run from the repository root:
#
#   sh tools/field/run.sh                fetch (once), build, score
#   sh tools/field/run.sh score [-v] EXE...   score other test_pitch builds
#
# Needs curl, unzip, a C compiler (CC, default gcc) and Python 3 (PY,
# default python).  The recordings are attachments of issue #1 on
# GitHub (Vollesocke/tunertime); they go to tools/field/data/, the
# test_pitch build to tools/field/build/ (neither committed).
set -e
CC=${CC:-gcc}
PY=${PY:-python}
D=tools/field
URL=https://github.com/user-attachments/files
case $(uname -s) in MINGW*|MSYS*|CYGWIN*) X=.exe ;; *) X= ;; esac
ZIPS="33676618/tunertime-debug-2026-10-19-part1-early.zip
33676635/tunertime-debug-2026-10-19-part2-late-morning.zip
33676641/tunertime-debug-2026-10-19-part3-second-organ.zip"

fetch() {
	mkdir -p $D/data
	for z in $ZIPS; do
		n=$(basename $z .zip)
		[ -d $D/data/$n ] && continue
		curl -fL --retry 5 -o $D/data/$n.zip $URL/$z
		unzip -q -o $D/data/$n.zip -d $D/data/$n
		rm $D/data/$n.zip
	done
	$PY $D/rebuild_hints.py $D/data
}

build() {
	mkdir -p $D/build
	$CC -O2 -Ijni -o $D/build/test_pitch$X tools/test_pitch.c jni/pitch.c -lm
}

case "$1" in
score)	shift; $PY $D/score.py $D/data "$@" ;;
*)	fetch; build; $PY $D/score.py $D/data $D/build/test_pitch$X ;;
esac
