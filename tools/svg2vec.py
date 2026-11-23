"""Material Symbols SVG -> Android vector: python3 svg2vec.py <svg dir> <res/drawable dir> name..."""
import sys,re,os
src,dst=sys.argv[1],sys.argv[2]
for n in sys.argv[3:]:
    t=open(os.path.join(src,n+'.svg')).read()
    minx,miny,w,h=map(float,re.search(r'viewBox="([^"]+)"',t).group(1).split())
    paths=re.findall(r'<path[^>]*\sd="([^"]+)"',t)
    body=''.join(f'\n        <path android:fillColor="#FFFFFFFF" android:pathData="{p}"/>' for p in paths)
    open(os.path.join(dst,'femboy_ic_'+n+'.xml'),'w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded «{n}», Google, Apache 2.0 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="{w:g}" android:viewportHeight="{h:g}">
    <group android:translateX="{-minx:g}" android:translateY="{-miny:g}">{body}
    </group>
</vector>
''')
    print('wrote',n)
