# -*- coding: utf-8 -*-
from pathlib import Path
import re

M = chr(77)
p = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy/ui/EditScreen.kt")
text = p.read_text(encoding="utf-8")

# Fix import
text = re.sub(
    r"import androidx\.compose\.ui\.[Mm]odifier(\.[Mm]odifier)?\r?\n",
    f"import androidx.compose.ui.{M}odifier\r\n",
    text,
)

# Fix broken Column(modifier.fillMaxWidth()) patterns -> Column(modifier = Modifier.fillMaxWidth())
def fix_mod_calls(s: str) -> str:
    # Column(modifier.xxx) or Row(modifier.xxx) without =
    s = re.sub(
        rf"(Column|Row)\(modifier\.",
        rf"\1(modifier = {M}odifier.",
        s,
    )
    # Already has Modifier. as first positional arg without name - keep OK for Row(Modifier.fill...)
    # Row(Modifier.fillMaxWidth() -> already handled if lowercase modifier.
    s = re.sub(
        rf"(?<![A-Za-z=]){M}odifier\.fillMaxWidth\(\)\.clickable",
        f"modifier = {M}odifier.fillMaxWidth().clickable",
        s,
    )
    # Fix double modifier = 
    s = s.replace("modifier = modifier = ", "modifier = ")
    return s

text = fix_mod_calls(text)

# Explicit replacements for known broken snippets
replacements = [
    (
        "Column(modifier.fillMaxWidth())",
        f"Column(modifier = {M}odifier.fillMaxWidth())",
    ),
    (
        "Column(modifier.weight(1f))",
        f"Column(modifier = {M}odifier.weight(1f))",
    ),
    (
        "Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)",
        f"Row(modifier = {M}odifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)",
    ),
]
for a, b in replacements:
    text = text.replace(a, b)

# Row(Modifier.fillMaxWidth().clickable -> Row(modifier = Modifier.fillMaxWidth().clickable
text = re.sub(
    rf"Row\(\s*{M}odifier\.fillMaxWidth\(\)\.clickable",
    f"Row(\r\n                    modifier = {M}odifier.fillMaxWidth().clickable",
    text,
)
text = re.sub(
    r"Row\(\s*modifier\.fillMaxWidth\(\)\.clickable",
    f"Row(\r\n                    modifier = {M}odifier.fillMaxWidth().clickable",
    text,
)

p.write_text(text, encoding="utf-8", newline="")
print("EditScreen fixed")

# SituationEditScreen import
for name in ["SituationEditScreen.kt", "GuideScreen.kt", "ListScreen.kt", "AssistantScreen.kt", "CategoriesScreen.kt"]:
    sp = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy/ui") / name
    if not sp.exists():
        continue
    t = sp.read_text(encoding="utf-8")
    t2 = re.sub(
        r"import androidx\.compose\.ui\.[Mm]odifier(\.[Mm]odifier)?\r?\n",
        f"import androidx.compose.ui.{M}odifier\r\n",
        t,
    )
    if t2 != t:
        sp.write_text(t2, encoding="utf-8", newline="")
        print("fixed import", name)

# ExperimentalCoroutinesApi on EditViewModel
evm = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy/ui/EditViewModel.kt")
et = evm.read_text(encoding="utf-8")
if "ExperimentalCoroutinesApi" not in et:
    et = et.replace(
        "import kotlinx.coroutines.flow.flatMapLatest\n",
        "import kotlinx.coroutines.ExperimentalCoroutinesApi\nimport kotlinx.coroutines.flow.flatMapLatest\n",
    )
    et = et.replace(
        "class EditViewModel(",
        "@OptIn(ExperimentalCoroutinesApi::class)\nclass EditViewModel(",
    )
    evm.write_text(et.replace("\n", "\r\n") if "\r\n" not in et else et, encoding="utf-8", newline="")
    print("EditViewModel opt-in added")

print("done")
