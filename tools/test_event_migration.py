"""Host SQLite migration/constraint checks using real Room v1 schema and production SQL.
No emulator/device. Room's Android migration validator still belongs to the Gradle/device checks.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.lazarus.personalojtdtr.data.local.OjtDatabase"
SOURCE = (ROOT / "app/src/main/java/com/lazarus/personalojtdtr/data/local/EventSchema.kt").read_text()
SQL = dict(re.findall(r'const val (\w+) = """(.*?)"""', SOURCE, re.DOTALL))
INDEXES = re.findall(r'"(CREATE (?:UNIQUE )?INDEX [^"\n]+)"', SOURCE)
DAY = (datetime(2026, 10, 24).date() - datetime(1970, 1, 1).date()).days


def millis(time, day="2026-10-24"):
    return int(datetime.fromisoformat(f"{day}T{time}+08:00").astimezone(timezone.utc).timestamp() * 1000)


def migrate(db):
    db.execute(SQL["CREATE_TABLE"])
    for index in INDEXES:
        db.execute(index)
    for name in ["INSERT_GUARD", "UPDATE_GUARD", "DELETE_GUARD", "LEGACY_INSERT_GUARD", "LEGACY_UPDATE_GUARD"]:
        db.execute(SQL[name])


def insert(db, code=0, time="07:45:00", day=DAY, ident=None, original=None, stamp=None, captured=None):
    timestamp = millis(time, (datetime(1970, 1, 1).date() + timedelta(days=day)).isoformat())
    ident = ident or f"{day}-{code}"
    db.execute("INSERT INTO attendance_events VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
               (ident, day, code, timestamp, timestamp - 1000 if captured is None else captured,
                original or f"{ident}/original.jpg", stamp or f"{ident}/timestamped.jpg", timestamp))


class EventMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.v1 = json.loads((SCHEMAS / "1.json").read_text())["database"]
        for entity in self.v1["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for index in entity.get("indices", []):
                self.db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        # Keep real P1 guard behavior during the upgrade.
        old_source = (ROOT / "app/src/main/java/com/lazarus/personalojtdtr/data/local/OjtDatabase.kt").read_text()
        for operation in ("INSERT", "UPDATE"):
            for sql in re.findall(r'"""\s*(CREATE TRIGGER.*?)"""', old_source, re.DOTALL):
                self.db.execute(sql.replace("${operation.lowercase()}", operation.lower()).replace("$operation", operation))
        self.db.execute("INSERT INTO personal_profile VALUES (1, 'Test fixture', 500, 20000, 3600)")
        self.db.execute("INSERT INTO attendance_sessions VALUES ('legacy', 1000, 61000, 30, 'old/in.jpg', 'old/out.jpg', 1000, 61000)")
        self.db.execute("INSERT INTO daily_activities VALUES ('activity', 20000, 'Fixture description', '[\"old/activity.jpg\"]', 1000, 1000)")
        self.db.commit()
        self.before = {entity["tableName"]: self.db.execute("SELECT * FROM " + entity["tableName"]).fetchall() for entity in self.v1["entities"]}
        migrate(self.db)
        self.db.commit()

    def test_migration_preserves_all_v1_data_without_inventing_events(self):
        for table, rows in self.before.items():
            self.assertEqual(rows, self.db.execute("SELECT * FROM " + table).fetchall())
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM attendance_events").fetchone()[0])
        self.assertIn("db.execSQL(CREATE_TABLE)", SOURCE)
        self.assertIn("indexes.forEach(db::execSQL)", SOURCE)
        self.assertIn("installGuards(db)", SOURCE)

    def test_four_event_workflow_and_noon_independence(self):
        for code, time in enumerate(["07:00:00", "10:45:00", "11:15:00", "17:00:00"]):
            insert(self.db, code, time)
        self.assertEqual(4, self.db.execute("SELECT count(*) FROM attendance_events").fetchone()[0])

    def test_morning_time_out_after_noon(self):
        insert(self.db)
        insert(self.db, 1, "12:30:00")

    def test_duplicate_confirm_aborts_and_preserves_original_photo_metadata(self):
        insert(self.db)
        original = self.db.execute("SELECT * FROM attendance_events").fetchone()
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db, ident="another-photo")
        self.assertEqual([original], self.db.execute("SELECT * FROM attendance_events").fetchall())

    def test_missing_predecessors_are_rejected(self):
        for code in (1, 2, 3):
            with self.subTest(code=code), self.assertRaises(sqlite3.IntegrityError):
                insert(self.db, code, "12:00:00")

    def test_reverse_and_equal_timestamps_rejected(self):
        insert(self.db)
        for time in ("07:44:00", "07:45:00"):
            with self.subTest(time=time), self.assertRaises(sqlite3.IntegrityError):
                insert(self.db, 1, time)

    def test_prior_open_day_blocks_new_day(self):
        insert(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db, day=DAY + 1)

    def test_photo_reuse_across_original_and_stamp_is_rejected(self):
        insert(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db, 1, "12:00:00", original=f"{DAY}-0/timestamped.jpg")

    def test_confirmed_event_cannot_be_rewritten_or_silently_deleted(self):
        insert(self.db)
        for sql in ("DELETE FROM attendance_events", "UPDATE attendance_events SET occurredAt = occurredAt + 1"):
            with self.subTest(sql=sql), self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(sql)

    def test_future_capture_time_is_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db, captured=millis("07:46:00"))

    def test_invalid_event_code_is_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db, code=4)

    def test_legacy_open_record_blocks_new_events(self):
        self.db.execute("UPDATE attendance_sessions SET timeOut=NULL")
        with self.assertRaises(sqlite3.IntegrityError):
            insert(self.db)

    def test_legacy_writes_cannot_overlap_new_event_sessions(self):
        insert(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO attendance_sessions VALUES ('overlap', ?, ?, 0, NULL, NULL, 1, 1)", (millis("07:50:00"), millis("08:00:00")))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE attendance_sessions SET timeIn=?,timeOut=?", (millis("07:50:00"), millis("08:00:00")))

    def test_unique_date_type_and_photo_indexes(self):
        indexes = {row[1]: row[2] for row in self.db.execute("PRAGMA index_list(attendance_events)")}
        for name in ("date_type", "originalPhotoPath", "timestampedPhotoPath"):
            self.assertEqual(1, indexes["index_attendance_events_" + name])

    def test_concurrent_confirmations_commit_exactly_one_event(self):
        output = ROOT / "app/build/local-validation"
        output.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=output) as temp:
            path = Path(temp) / "concurrent.db"
            with sqlite3.connect(path) as destination:
                self.db.backup(destination)
            def attempt(_):
                connection = sqlite3.connect(path, timeout=5)
                try:
                    with connection:
                        insert(connection)
                    return True
                except sqlite3.IntegrityError:
                    return False
                finally:
                    connection.close()
            with ThreadPoolExecutor(max_workers=2) as pool:
                self.assertEqual(1, sum(pool.map(attempt, range(2))))

    def test_migration_matches_exported_room_v2_schema(self):
        schema = json.loads((SCHEMAS / "2.json").read_text())["database"]
        for entity in schema["entities"]:
            actual = {row[1]: (row[2], bool(row[3])) for row in self.db.execute("PRAGMA table_info(" + entity["tableName"] + ")")}
            expected = {field["columnName"]: (field["affinity"], field.get("notNull", False)) for field in entity["fields"]}
            self.assertEqual(expected, actual, entity["tableName"])
            key_columns = [row[1] for row in self.db.execute("PRAGMA table_info(" + entity["tableName"] + ")") if row[5]]
            self.assertEqual(entity["primaryKey"]["columnNames"], key_columns)
            for index in entity.get("indices", []):
                columns = [row[2] for row in self.db.execute("PRAGMA index_info(" + index["name"] + ")")]
                self.assertEqual(index["columnNames"], columns)
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())


if __name__ == "__main__":
    unittest.main()
