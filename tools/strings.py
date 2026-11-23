"""Add or replace FemboyGram strings: apply(path, {name: text}) after the FemboyMaterialYouAboutOld anchor."""
import re
def apply(path, d):
    s=open(path,encoding='utf-8').read()
    add=[]
    for k,v in d.items():
        pat=re.compile(r'(<string name="%s"[^>]*>)(.*?)(</string>)'%k, re.S)
        if pat.search(s):
            s=pat.sub(lambda m: m.group(1)+v+m.group(3), s, count=1)
        else:
            add.append('    <string name="%s">%s</string>'%(k,v))
    if add:
        anchor=re.search(r'\n[^\n]*name="FemboyMaterialYouAboutOld"[^\n]*', s)
        s=s[:anchor.end()]+'\n'+'\n'.join(add)+s[anchor.end():]
    open(path,'w',encoding='utf-8').write(s)
    print(path,'added',len(add))
