"""Version 1/2 -> 3 migration, recovery guards, and explicit REPLACE transaction rollback.
Uses real exported Room schemas and SQL from the application. No Android runtime/device.
"""
import json
from pathlib import Path
import re
import sqlite3
import unittest
from test_event_migration import EventMigrationTest, SOURCE as EVENT_SOURCE, SQL as EVENT_SQL, DAY, insert, millis

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/java/com/lazarus/personalojtdtr/data/local/RecoverySchema.kt').read_text()
SQL = dict(re.findall(r'const val (\w+) = """(.*?)"""', SOURCE, re.DOTALL))
INDEX = re.search(r'const val CREATE_INDEX = "(.*?)"', SOURCE).group(1)
TRIGGER_NAMES = re.findall(r'"([a-z_]+)"', SOURCE[SOURCE.index('val triggerNames'):SOURCE.index('fun install')])


def install_recovery(db):
    db.execute('DROP TRIGGER IF EXISTS event_insert_guard')
    for name in ('EVENT_GUARD', 'RESOLUTION_GUARD', 'RESOLUTION_UPDATE_GUARD', 'RESOLUTION_DELETE_GUARD'):
        db.execute(SQL[name])
    for operation in ('insert', 'update'):
        db.execute('DROP TRIGGER IF EXISTS legacy_event_' + operation + '_guard')
        original = EVENT_SQL['LEGACY_' + operation.upper() + '_GUARD']
        db.execute(original.replace('AND (NEW.timeOut IS NULL', 'AND NOT EXISTS (SELECT 1 FROM session_resolutions r WHERE r.date = start.date AND r.session = start.type / 2) AND (NEW.timeOut IS NULL'))


def migrate_v3(db):
    db.execute(SQL['CREATE_RESOLUTIONS'])
    db.execute(INDEX)
    db.execute(SQL['CREATE_MARKER'])
    install_recovery(db)


def resolve(db, session=0, kind=0, day=DAY, acted_day=None, hour='06:00:00', ident=None):
    from datetime import datetime, timedelta
    action_day = datetime(1970, 1, 1).date() + timedelta(days=day + (1 if kind == 0 else 0) if acted_day is None else acted_day)
    db.execute('INSERT INTO session_resolutions VALUES (?, ?, ?, ?, ?)', (ident or f'{day}-{session}-{kind}', day, session, kind, millis(hour, action_day.isoformat())))


class RecoveryMigrationTest(unittest.TestCase):
    def setUp(self):
        fixture = EventMigrationTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.db = fixture.db
        self.fixture = fixture
        migrate_v3(self.db)
        self.db.commit()

    def test_v1_to_v2_to_v3_preserves_profile_settings_activities_and_photos(self):
        for table, records in self.fixture.before.items():
            self.assertEqual(records, self.db.execute('SELECT * FROM ' + table).fetchall())
        self.assertEqual([], self.db.execute('SELECT * FROM session_resolutions').fetchall())
        self.assertEqual([], self.db.execute('SELECT * FROM restore_marker').fetchall())

    def test_existing_v2_captured_event_preserved_when_upgrading(self):
        # v3 migration can be tested against a populated v2 instance separately.
        other = EventMigrationTest(); other.setUp()
        self.addCleanup(other.doCleanups)
        insert(other.db)
        before = other.db.execute('SELECT * FROM attendance_events').fetchall()
        migrate_v3(other.db)
        self.assertEqual(before, other.db.execute('SELECT * FROM attendance_events').fetchall())

    def test_schema_columns_primary_keys_and_indexes_match_room_v3(self):
        schema = json.loads((ROOT / 'app/schemas/com.lazarus.personalojtdtr.data.local.OjtDatabase/3.json').read_text())['database']
        for entity in schema['entities']:
            table = entity['tableName']
            actual = {row[1]: (row[2], bool(row[3])) for row in self.db.execute('PRAGMA table_info(' + table + ')')}
            expected = {field['columnName']: (field['affinity'], field.get('notNull', False)) for field in entity['fields']}
            self.assertEqual(expected, actual)
            self.assertEqual(entity['primaryKey']['columnNames'], [row[1] for row in self.db.execute('PRAGMA table_info(' + table + ')') if row[5]])
            for index in entity.get('indices', []):
                self.assertEqual(index['columnNames'], [row[2] for row in self.db.execute('PRAGMA index_info(' + index['name'] + ')')])
        self.assertEqual(set(TRIGGER_NAMES), {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='trigger'")})

    def test_morning_recovery_preserves_time_in_and_allows_next_day(self):
        insert(self.db)
        before = self.db.execute('SELECT * FROM attendance_events').fetchall()
        resolve(self.db)
        insert(self.db, day=DAY + 1)
        self.assertEqual(before[0], self.db.execute('SELECT * FROM attendance_events WHERE date=?', (DAY,)).fetchone())
        self.assertEqual(1, self.db.execute('SELECT count(*) FROM attendance_events WHERE date=?', (DAY,)).fetchone()[0])

    def test_afternoon_recovery_preserves_completed_morning(self):
        for code, time in enumerate(('07:45:00', '12:00:00', '12:45:00')):
            insert(self.db, code, time)
        resolve(self.db, session=1)
        insert(self.db, day=DAY + 1)
        self.assertEqual(3, self.db.execute('SELECT count(*) FROM attendance_events WHERE date=?', (DAY,)).fetchone()[0])

    def test_duplicate_recovery_and_audit_updates_deletes_rejected(self):
        insert(self.db); resolve(self.db)
        for operation in (lambda: resolve(self.db, ident='duplicate'), lambda: self.db.execute('DELETE FROM session_resolutions'), lambda: self.db.execute('UPDATE session_resolutions SET kind=1')):
            with self.assertRaises(sqlite3.IntegrityError): operation()

    def test_completed_or_unstarted_session_cannot_be_recovered(self):
        with self.assertRaises(sqlite3.IntegrityError): resolve(self.db)
        insert(self.db); insert(self.db, 1, '12:00:00')
        with self.assertRaises(sqlite3.IntegrityError): resolve(self.db)

    def test_afternoon_only_day_requires_explicit_morning_skip(self):
        resolve(self.db, kind=1)
        insert(self.db, 2, '10:00:00'); insert(self.db, 3, '14:00:00')
        self.assertEqual([2, 3], [r[0] for r in self.db.execute('SELECT type FROM attendance_events ORDER BY type')])

    def test_morning_only_day_can_skip_unstarted_afternoon(self):
        insert(self.db); insert(self.db, 1, '11:00:00')
        resolve(self.db, session=1, kind=1, hour='12:00:00')
        with self.assertRaises(sqlite3.IntegrityError): insert(self.db, 2, '13:00:00')

    def test_skip_cannot_overwrite_genuine_started_session(self):
        insert(self.db)
        with self.assertRaises(sqlite3.IntegrityError): resolve(self.db, kind=1, hour='09:00:00')

    def test_capture_must_follow_exception_timestamp(self):
        resolve(self.db, kind=1, hour='09:00:00')
        with self.assertRaises(sqlite3.IntegrityError): insert(self.db, 2, '08:30:00')

    def test_failed_replace_rolls_back_records_marker_and_trigger_ddl(self):
        insert(self.db); resolve(self.db)
        self.db.execute("INSERT INTO restore_marker VALUES (1, 'old-generation', 1)")
        self.db.commit()
        tables = ('personal_profile', 'attendance_sessions', 'daily_activities', 'attendance_events', 'session_resolutions', 'restore_marker')
        before = {table: self.db.execute('SELECT * FROM ' + table).fetchall() for table in tables}
        guards = self.db.execute("SELECT name, sql FROM sqlite_master WHERE type='trigger' ORDER BY name").fetchall()
        try:
            self.db.execute('BEGIN IMMEDIATE')
            for name in TRIGGER_NAMES: self.db.execute('DROP TRIGGER IF EXISTS ' + name)
            for table in tables: self.db.execute('DELETE FROM ' + table)
            self.db.execute("INSERT INTO restore_marker VALUES (1, 'new-generation', 2)")
            raise RuntimeError('Injected final apply failure')
        except RuntimeError:
            self.db.rollback()
        for table in tables: self.assertEqual(before[table], self.db.execute('SELECT * FROM ' + table).fetchall())
        self.assertEqual(guards, self.db.execute("SELECT name, sql FROM sqlite_master WHERE type='trigger' ORDER BY name").fetchall())
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('DELETE FROM attendance_events')

    def test_immutable_records_are_deletable_only_inside_explicit_replace_transaction(self):
        insert(self.db); resolve(self.db); self.db.commit()
        self.db.execute('BEGIN IMMEDIATE')
        for name in TRIGGER_NAMES: self.db.execute('DROP TRIGGER IF EXISTS ' + name)
        for table in ('attendance_events', 'session_resolutions', 'attendance_sessions', 'daily_activities', 'personal_profile'): self.db.execute('DELETE FROM ' + table)
        # Restore guard definitions from application source, transactionally with the data switch.
        old_source = (ROOT / 'app/src/main/java/com/lazarus/personalojtdtr/data/local/OjtDatabase.kt').read_text()
        for operation in ('INSERT', 'UPDATE'):
            for sql in re.findall(r'"""\s*(CREATE TRIGGER.*?)"""', old_source, re.DOTALL):
                self.db.execute(sql.replace('${operation.lowercase()}', operation.lower()).replace('$operation', operation))
        for name in ('INSERT_GUARD', 'UPDATE_GUARD', 'DELETE_GUARD', 'LEGACY_INSERT_GUARD', 'LEGACY_UPDATE_GUARD'): self.db.execute(EVENT_SQL[name])
        install_recovery(self.db)
        self.db.execute("INSERT INTO restore_marker VALUES (1, 'restored-generation', 1)")
        self.db.commit()
        self.assertEqual('restored-generation', self.db.execute('SELECT generation FROM restore_marker').fetchone()[0])
        self.assertEqual(0, self.db.execute('SELECT count(*) FROM attendance_events').fetchone()[0])
        self.assertEqual(set(TRIGGER_NAMES), {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='trigger'")})


if __name__ == '__main__':
    # Only this module's new tests; P1/P2 suites retain their own separate entry points.
    result = unittest.TextTestRunner(verbosity=1).run(unittest.defaultTestLoader.loadTestsFromTestCase(RecoveryMigrationTest))
    raise SystemExit(0 if result.wasSuccessful() else 1)
