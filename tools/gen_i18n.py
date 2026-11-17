#!/usr/bin/env python3
"""Erzeugt aus i18n/translations.ini alle values-XX/strings_i18n.xml (laeuft im Build, nichts committen).
Schluessel, die in einem Sprachordner schon existieren, werden uebersprungen (keine Duplikate)."""
import glob, os, re, sys
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT_NAME = "strings_i18n.xml"
COPIES = {"zh-rHK": "zh-rTW"}            # gleiche Texte, eigener Ordner
SS_VARIANTS = {"de-rCH": "de", "de-rLI": "de"}  # ß -> ss


def read_xml_dir(d):
    out = {}
    for f in sorted(glob.glob(os.path.join(d, "*.xml"))):
        if os.path.basename(f) == OUT_NAME:
            continue
        txt = open(f, encoding="utf-8").read()
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', txt, re.S):
            out[m.group(1)] = m.group(2)
    return out


def folder(q):
    return "values-b+" + q if re.fullmatch(r"[a-z]{3}", q) else "values-" + q


def esc(v):
    v = escape(v).replace("'", "\\'").replace('"', '\\"')
    if v[:1] in "@?":
        v = "\\" + v
    return v


def parse(path):
    data, cur = {}, None
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        m = re.fullmatch(r"\[([A-Za-z0-9+-]+)\]", line.strip())
        if m:
            cur = data.setdefault(m.group(1), {})
            continue
        if cur is None or "=" not in line:
            sys.exit("Fehler in translations.ini, Zeile %d: %s" % (n, line))
        k, v = line.split("=", 1)
        cur[k.strip()] = v
    return data


def autofill(entries, base):
    """Gleicher englischer Text -> gleiche Uebersetzung (spart Doppelungen); Markenname Timebox bleibt."""
    out = dict(entries)
    for k, v in base.items():
        if k in out:
            continue
        if v == "Timebox" and entries:
            out[k] = v
            continue
        for j, w in base.items():
            if j in entries and w == v:
                out[k] = entries[j]
                break
    return out


def write(q, entries, base):
    d = os.path.join(RES, folder(q))
    existing = read_xml_dir(d) if os.path.isdir(d) else {}
    entries = autofill(entries, base)
    rows = []
    for k, v in entries.items():
        if k not in base:
            print("WARNUNG [%s]: unbekannter Schluessel %s" % (q, k))
            continue
        if k in existing:
            continue
        ph = lambda s: sorted(re.findall(r"%\d\$[sd]", s))
        if ph(v) != ph(base[k]):
            print("WARNUNG [%s]: Platzhalter bei %s weichen ab" % (q, k))
        rows.append('    <string name="%s">%s</string>' % (k, esc(v)))
    if not rows:
        return 0
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, OUT_NAME), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(rows) + "\n</resources>\n")
    return len(rows)


def main():
    base = read_xml_dir(os.path.join(RES, "values"))
    data = parse(os.path.join(ROOT, "i18n", "translations.ini"))
    for dst, src in COPIES.items():
        data[dst] = dict(data[src])
    for dst, src in SS_VARIANTS.items():
        de = {**read_xml_dir(os.path.join(RES, "values-" + src)), **data.get(src, {})}
        data[dst] = {k: v.replace("ß", "ss") for k, v in de.items() if "ß" in v}
    total = 0
    for q, entries in data.items():
        n = write(q, entries, base)
        total += n
        print("%-8s %3d Texte" % (q, n))
    print("Fertig:", total, "Texte,", len(data), "Sprachen")


main()
