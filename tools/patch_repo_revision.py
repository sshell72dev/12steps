# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")

repo = ROOT / "data/ResentmentRepository.kt"
t = repo.read_text(encoding="utf-8").replace("\r\n", "\n")
while "\n\n\n" in t:
    t = t.replace("\n\n\n", "\n\n")

if "observeTreeRevision" not in t:
    needle = "suspend fun getSituation(id: Long): Situation? = situationDao.getSituation(id)"
    insert = """fun observeTreeRevision(): Flow<Int> =
        combine(
            situationDao.observeTypeCount(),
            situationDao.observeSituationCount()
        ) { types, situations -> types + situations }

    suspend fun getSituation(id: Long): Situation? = situationDao.getSituation(id)"""
    if needle not in t:
        idx = t.find("getSituation")
        print("getSituation vicinity:", repr(t[idx - 40 : idx + 100]))
        raise SystemExit(1)
    t = t.replace(needle, insert, 1)
    repo.write_text(t.replace("\n", "\r\n"), encoding="utf-8")
    print("repo patched")
else:
    print("repo already has observeTreeRevision")

dao = ROOT / "data/SituationDao.kt"
d = dao.read_text(encoding="utf-8")
print("dao observeSituationCount", "observeSituationCount" in d)
print("dao observeTypeCount", "observeTypeCount" in d)

lvm_path = ROOT / "ui/ListViewModel.kt"
lvm = lvm_path.read_text(encoding="utf-8")
if "import kotlinx.coroutines.flow.combine" not in lvm:
    lvm = lvm.replace(
        "import kotlinx.coroutines.flow.SharingStarted",
        "import kotlinx.coroutines.flow.SharingStarted\nimport kotlinx.coroutines.flow.combine",
    )
    lvm_path.write_text(lvm, encoding="utf-8")
    print("added combine import")

ls = (ROOT / "ui/ListScreen.kt").read_text(encoding="utf-8")
print("ResentmentListItem", "ResentmentListItem" in ls)
print("item.preview", "item.preview" in ls)
print("item.whatHappened", "item.whatHappened" in ls)

ru = (ROOT / "Ru.kt").read_text(encoding="utf-8")
for k in ["customTypeTitle", "noTypesYet", "confirm", "noSituationsYet"]:
    print(k, f"const val {k}" in ru)

# Normalize EditViewModel / repository double spacing is fine
print("ListViewModel treeRevision", "observeTreeRevision" in lvm_path.read_text(encoding="utf-8"))
