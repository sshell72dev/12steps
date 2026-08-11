# -*- coding: utf-8 -*-
from pathlib import Path
import zipfile
from xml.etree import ElementTree as ET

p = r"c:\Users\hp_ps\Downloads\Таблица_обид_Сергея.xlsx"
out = Path(r"d:/sites/step4obidy/tools/xlsx_probe.txt")
lines = []
with zipfile.ZipFile(p) as z:
    lines.append("names=" + str(z.namelist()))
    sheet = z.read("xl/worksheets/sheet1.xml")
    lines.append("sheet_len=" + str(len(sheet)))
    lines.append("sheet_head=" + repr(sheet[:200]))
    root = ET.fromstring(sheet)
    NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    rows = root.findall(f".//{NS}row")
    lines.append("rows=" + str(len(rows)))
    for row in rows[:3]:
        cells = []
        for c in row.findall(f"{NS}c"):
            v = c.find(f"{NS}v")
            is_el = c.find(f"{NS}is")
            cells.append(
                {
                    "r": c.get("r"),
                    "t": c.get("t"),
                    "v": None if v is None else v.text,
                    "is": None
                    if is_el is None
                    else "".join(t.text or "" for t in is_el.iter(f"{NS}t")),
                }
            )
        lines.append(repr(cells))
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out)
