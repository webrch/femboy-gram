"""pyc_check.py PYC_DIR SRC_DIR [module ...]: does each .py compile to the bytecode in the apk?

Run with python3.11, the Python the app ships: bytecode differs between versions.
PYC_DIR is assets/chaquopy/app.imy of the apk, unzipped. Line numbers are not
compared: comments and blank lines may move freely, code may not.
"""
import marshal, sys, types, hashlib, os
def load_pyc(p):
    with open(p,'rb') as f: f.read(16); return marshal.load(f)
def sig(c):
    consts = tuple(x if not isinstance(x, types.CodeType) else ('<code>', x.co_name) for x in c.co_consts)
    return hashlib.sha1(repr((c.co_code, consts, c.co_names, c.co_varnames, c.co_freevars, c.co_cellvars)).encode()).hexdigest()[:10]
def walk(co, pre=''):
    out = {pre + co.co_name: sig(co)}
    for c in co.co_consts:
        if isinstance(c, types.CodeType): out.update(walk(c, pre + co.co_name + '.'))
    return out
pycdir, srcdir = sys.argv[1:3]
mods = sys.argv[3:] or sorted(os.path.relpath(os.path.join(d, f), pycdir)[:-4] for d, _, fs in os.walk(pycdir) for f in fs if f.endswith('.pyc'))
for m in mods:
    src = os.path.join(srcdir, m + '.py')
    if not os.path.exists(src): print('%-24s MISSING' % m); continue
    a = walk(load_pyc(os.path.join(pycdir, m + '.pyc')))
    b = walk(compile(open(src, encoding='utf-8').read(), os.path.basename(src), 'exec'))
    bad = sorted(k for k in set(a) | set(b) if a.get(k) != b.get(k))
    print('%-24s %s' % (m, 'OK (%d)' % len(a) if not bad else 'DIFF ' + ', '.join(x.replace('<module>.', '') for x in bad)))
