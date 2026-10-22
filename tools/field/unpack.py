#!/usr/bin/env python3
"""Unpacks the zips the app's "Recordings to GitHub" makes
(ShareRecordings.java) and joins files that were split over them.

    python tools/field/unpack.py ZIPDIR [OUTDIR]

ZIPDIR holds the zips downloaded from the issue (since 18.8 one or more
per run, run_<time>_<stop>[_<n>].zip; before tunertime_<time>_<n>.zip); OUTDIR
(default ZIPDIR/unpacked) gets runs/ and recordings/ as on the phone.  A
file too big for one zip is in pieces name.001, name.002 ... in
consecutive zips; they are joined in order and removed.
"""
import glob
import os
import re
import sys
import zipfile


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(src, 'unpacked')
    zips = sorted(glob.glob(os.path.join(src, '*.zip')),
                  key=lambda p: [int(t) if t.isdigit() else t
                                 for t in re.split(r'(\d+)', p)])
    if not zips:
        sys.exit('no *.zip in ' + src)
    pieces = {}
    for z in zips:
        with zipfile.ZipFile(z) as f:
            for n in f.namelist():
                if os.path.isabs(n) or '..' in n.split('/'):
                    continue
                f.extract(n, out)
                m = re.match(r'(.*)\.(\d{3})$', n)
                if m:
                    pieces.setdefault(m.group(1), []).append(n)
    for name, ps in sorted(pieces.items()):
        ps.sort()
        with open(os.path.join(out, name), 'wb') as o:
            for p in ps:
                path = os.path.join(out, p)
                with open(path, 'rb') as i:
                    o.write(i.read())
                os.remove(path)
        print('joined %s from %d pieces' % (name, len(ps)))
    print('%d zips unpacked to %s' % (len(zips), out))


if __name__ == '__main__':
    main()
