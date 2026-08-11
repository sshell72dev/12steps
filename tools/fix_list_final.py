# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")

# Fix ListScreen
ls = ROOT / "ui/ListScreen.kt"
t = ls.read_text(encoding="utf-8")
if "import ru.na.step4.obidy.data.ResentmentListItem" not in t:
    t = t.replace(
        "import ru.na.step4.obidy.data.Resentment\n",
        "import ru.na.step4.obidy.data.Resentment\nimport ru.na.step4.obidy.data.ResentmentListItem\n",
    )
t = t.replace(
    "itemsIndexed(state.items, key = { _, item -> item.id })",
    "itemsIndexed(state.items, key = { _, item -> item.resentment.id })",
)
t = t.replace(
    "private fun ResentmentRow(\n    item: Resentment,",
    "private fun ResentmentRow(\n    item: ResentmentListItem,",
)
t = t.replace(
    ".background(if (item.isCompleted) Moss.copy(alpha = 0.25f) else Forest.copy(alpha = 0.1f))",
    ".background(if (item.resentment.isCompleted) Moss.copy(alpha = 0.25f) else Forest.copy(alpha = 0.1f))",
)
ls.write_text(t, encoding="utf-8")
print("ListScreen fixed")

# Improve revision to include max updatedAt
dao = ROOT / "data/SituationDao.kt"
d = dao.read_text(encoding="utf-8")
if "observeSituationStamp" not in d:
    d = d.replace(
        """    @Query("SELECT COUNT(*) FROM situations")
    fun observeSituationCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM situation_types")
    fun observeTypeCount(): Flow<Int>
}""",
        """    @Query("SELECT COUNT(*) FROM situations")
    fun observeSituationCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM situation_types")
    fun observeTypeCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM situations")
    fun observeSituationStamp(): Flow<Long>
}""",
    )
    dao.write_text(d, encoding="utf-8")
    print("dao stamp added")

repo = ROOT / "data/ResentmentRepository.kt"
r = repo.read_text(encoding="utf-8").replace("\r\n", "\n")
while "\n\n\n" in r:
    r = r.replace("\n\n\n", "\n\n")
old = """fun observeTreeRevision(): Flow<Int> =
        combine(
            situationDao.observeTypeCount(),
            situationDao.observeSituationCount()
        ) { types, situations -> types + situations }"""
new = """fun observeTreeRevision(): Flow<Long> =
        combine(
            situationDao.observeTypeCount(),
            situationDao.observeSituationCount(),
            situationDao.observeSituationStamp()
        ) { types, situations, stamp -> types + situations + stamp }"""
if old in r:
    r = r.replace(old, new)
    repo.write_text(r.replace("\n", "\r\n"), encoding="utf-8")
    print("repo revision updated")
elif "observeSituationStamp" in r:
    print("repo already stamped")
else:
    print("repo revision block missing/changed")
    idx = r.find("observeTreeRevision")
    print(repr(r[idx:idx+300]))
