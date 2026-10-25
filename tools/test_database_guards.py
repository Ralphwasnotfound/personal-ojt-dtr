"""Host SQLite checks for the actual trigger SQL in OjtDatabase.kt (no Android runtime)."""
from pathlib import Path
import re
import sqlite3
import unittest


class DatabaseGuardTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.executescript("""
            CREATE TABLE attendance_sessions (
                id TEXT PRIMARY KEY NOT NULL, timeIn INTEGER NOT NULL,
                timeOut INTEGER, breakSeconds INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE personal_profile (
                id INTEGER PRIMARY KEY NOT NULL, requiredHours INTEGER NOT NULL,
                defaultBreakSeconds INTEGER NOT NULL
            );
        """)
        source = (Path(__file__).resolve().parents[1] /
                  "app/src/main/java/com/lazarus/personalojtdtr/data/local/OjtDatabase.kt").read_text()
        triggers = re.findall(r'"""\s*(CREATE TRIGGER.*?)"""', source, re.DOTALL)
        self.assertEqual(2, len(triggers))
        for operation in ("INSERT", "UPDATE"):
            for trigger in triggers:
                self.db.executescript(trigger.replace("${operation.lowercase()}", operation.lower())
                                     .replace("$operation", operation))

    def insert(self, ident="a", start=1000, end=None, break_seconds=0):
        self.db.execute("INSERT INTO attendance_sessions VALUES (?, ?, ?, ?)",
                        (ident, start, end, break_seconds))

    def test_duplicate_active_session_aborts_without_deleting_original(self):
        self.insert()
        with self.assertRaisesRegex(sqlite3.IntegrityError, "Overlapping"):
            self.insert("b", 2000)
        self.assertEqual([("a",)], self.db.execute("SELECT id FROM attendance_sessions").fetchall())

    def test_closed_overlap_aborts(self):
        self.insert(end=10000)
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert("b", 5000, 12000)

    def test_adjacent_sessions_allowed(self):
        self.insert(end=10000)
        self.insert("b", 10000)

    def test_open_session_blocks_future_closed_session(self):
        self.insert()
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert("b", 10000, 20000)

    def test_out_of_order_open_session_blocks_existing_future_session(self):
        self.insert(start=10000, end=20000)
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert("b", 1000)

    def test_update_cannot_create_overlap(self):
        self.insert(end=10000)
        self.insert("b", 10000, 20000)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE attendance_sessions SET timeIn=5000 WHERE id='b'")

    def test_time_out_can_close_itself(self):
        self.insert()
        self.db.execute("UPDATE attendance_sessions SET timeOut=61000, breakSeconds=30 WHERE id='a'")
        self.assertEqual((61000, 30), self.db.execute("SELECT timeOut, breakSeconds FROM attendance_sessions").fetchone())

    def test_invalid_ranges_and_breaks_abort(self):
        for start, end, deduction in [(1000, 1000, 0), (1000, 999, 0), (1000, None, -1), (1000, 2000, 2)]:
            with self.subTest(start=start, end=end, deduction=deduction):
                with self.assertRaises(sqlite3.IntegrityError):
                    self.insert(start=start, end=end, break_seconds=deduction)

    def test_invalid_profile_rejected(self):
        for values in [(2, 500, 0), (1, -1, 0), (1, 100001, 0), (1, 500, -1), (1, 500, 86401)]:
            with self.subTest(values=values):
                with self.assertRaises(sqlite3.IntegrityError):
                    self.db.execute("INSERT INTO personal_profile VALUES (?, ?, ?)", values)

    def test_profile_update_guard(self):
        self.db.execute("INSERT INTO personal_profile VALUES (1, 500, 3600)")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE personal_profile SET requiredHours=-1 WHERE id=1")
        self.assertEqual(500, self.db.execute("SELECT requiredHours FROM personal_profile").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
