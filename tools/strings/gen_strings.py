#!/usr/bin/env python3
"""values/strings.xml (EN, varsayılan) ve values-tr/strings.xml (TR) üretir ve denetler.

Kullanım (proje kökünde):
    python tools/strings/gen_strings.py app/src/main/res app/src/main/java

Metinler strings_main.py ve strings_tools.py içindedir; XML dosyalarını elle değil buradan değiştirin.
Betik iki dilde aynı anahtarların ve aynı biçim argümanlarının (%1$s …) bulunduğunu denetler.
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from strings_main import S, COMPASS_POINTS
from strings_tools import TOOLS, INFO, NO_LIMIT
from strings_part2 import S2, INFO2, NOTE_NAMES, COLORS
from strings_part3 import S3, INFO3
from strings_part4 import S4, INFO4
from strings_part5 import S5, INFO5
from strings_part6 import S6, INFO6

RES = sys.argv[1]
SRC = sys.argv[2]

entries = {}  # key -> (tr, en)
entries.update(S)
entries.update(S2)
entries.update(S3)
entries.update(S4)
entries.update(S5)
entries.update(S6)
for k, v in COLORS.items():
    entries[f"color_{k}"] = v
for key, ((tn, td, tl), (en, ed, el)) in TOOLS.items():
    entries[f"tool_{key}_name"] = (tn, en)
    entries[f"tool_{key}_desc"] = (td, ed)
    entries[f"tool_{key}_limits"] = (NO_LIMIT[0] if tl == "—" else tl, NO_LIMIT[1] if el == "—" else el)
for key, (ti, ei) in list(INFO.items()) + list(INFO2.items()) + list(INFO3.items()) + list(INFO4.items()) + list(INFO5.items()) + list(INFO6.items()):
    entries[f"tool_{key}_info"] = (ti, ei)

def esc(s: str) -> str:
    s = s.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s

FMT = re.compile(r"%(\d+)\$([sdf])")

def placeholders(s):
    return sorted(set(FMT.findall(s)))

problems = []
for k, (tr, en) in entries.items():
    if placeholders(tr) != placeholders(en):
        problems.append(f"{k}: biçim uyuşmuyor TR={placeholders(tr)} EN={placeholders(en)}")
    for lang, s in (("tr", tr), ("en", en)):
        rest = FMT.sub("", s).replace("%%", "")
        if placeholders(s) and "%" in rest:
            problems.append(f"{k} [{lang}]: biçimli metinde çıplak %")

# Koddaki anahtarlar
used = set()
for root, _, files in os.walk(SRC):
    for f in files:
        if f.endswith(".kt"):
            used |= set(re.findall(r"R\.string\.([a-z0-9_]+)", open(os.path.join(root, f), encoding="utf-8").read()))
missing = sorted(used - set(entries))
unused = sorted(k for k in set(entries) - used - {"app_name"} if not k.startswith("color_"))
if missing:
    problems.append("Kodda kullanılıp tanımlanmayan: " + ", ".join(missing))

def write(path, idx):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- tools/strings/gen_strings.py ile üretilir; elle düzenlemeyin. -->", "<resources>"]
    for k in sorted(entries):
        v = entries[k][idx]
        attrs = ""
        if k == "app_name":
            attrs = ' translatable="false"' if idx == 1 else None
            if attrs is None:
                continue  # app_name yalnızca varsayılanda
        if "%" in v and not placeholders(v):
            attrs += ' formatted="false"'
        lines.append(f'    <string name="{k}"{attrs}>{esc(v)}</string>')
    lines.append('    <string-array name="compass_points">')
    for p in COMPASS_POINTS[idx]:
        lines.append(f"        <item>{esc(p)}</item>")
    lines.append("    </string-array>")
    lines.append('    <string-array name="note_names">')
    for p in NOTE_NAMES[idx]:
        lines.append(f"        <item>{esc(p)}</item>")
    lines.append("    </string-array>")
    lines.append("</resources>")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")

write(os.path.join(RES, "values", "strings.xml"), 1)
write(os.path.join(RES, "values-tr", "strings.xml"), 0)
print(f"{len(entries)} metin yazıldı; kodda kullanılan {len(used)}.")
if unused:
    print("Kullanılmayan (zararsız):", ", ".join(unused))
if problems:
    print("SORUNLAR:")
    for p in problems:
        print("  ", p)
    sys.exit(1)
