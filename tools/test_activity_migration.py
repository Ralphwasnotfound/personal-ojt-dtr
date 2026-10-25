"""Actual v3->v4 SQL migration and activity write/delete constraints, without a device."""
import json
from pathlib import Path
import re
import unittest
import sqlite3
from test_recovery_migration import RecoveryMigrationTest, TRIGGER_NAMES as P3_TRIGGERS

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/java/com/lazarus/personalojtdtr/data/local/ActivitySchema.kt').read_text()
SQL = dict(re.findall(r'const val (\w+) = """(.*?)"""', SOURCE, re.DOTALL))
SQL.update(dict(re.findall(r'const val (\w+) = "([^"\n]+)"', SOURCE)))
DAO = (ROOT / 'app/src/main/java/com/lazarus/personalojtdtr/data/local/dao/OjtDao.kt').read_text()
DELETE = re.search(r'@Query\("(DELETE FROM daily_activities WHERE[^"\n]+)"\)', DAO).group(1)
SORT = re.search(r'@Query\("(SELECT \* FROM daily_activities ORDER BY[^"\n]+)"\)', DAO).group(1)


def migrate(db):
    for name in ('ADD_TITLE', 'ADD_TIME', 'ADD_REVISION', 'LEGACY_TITLES', 'INSERT_GUARD', 'UPDATE_GUARD'):
        db.execute(SQL[name])


class ActivityMigrationTest(unittest.TestCase):
    def setUp(self):
        fixture = RecoveryMigrationTest(); fixture.setUp(); self.addCleanup(fixture.doCleanups)
        self.db = fixture.db
        self.before = self.db.execute('SELECT * FROM daily_activities').fetchall()
        migrate(self.db); self.db.commit()

    def create(self, ident='new', title='Title', description='Detailed work', time=None, date=21000):
        self.db.execute('INSERT INTO daily_activities (id,date,description,photoPaths,createdAt,updatedAt,title,timeMinutes,revision) VALUES (?,?,?,?,?,?,?,?,?)', (ident, date, description, '["first.jpg","second.jpg"]', 1000, 1000, title, time, 0))

    def test_v1_v2_v3_to_v4_preserves_legacy_records_paths_and_timestamps(self):
        after = self.db.execute('SELECT id,date,description,photoPaths,createdAt,updatedAt FROM daily_activities').fetchall()
        self.assertEqual(self.before, after)
        self.assertEqual('Fixture description', self.db.execute('SELECT title FROM daily_activities WHERE id="activity"').fetchone()[0])
        self.assertIsNone(self.db.execute('SELECT timeMinutes FROM daily_activities WHERE id="activity"').fetchone()[0])

    def test_exported_room_four_schema_matches_migration(self):
        schema = json.loads((ROOT / 'app/schemas/com.lazarus.personalojtdtr.data.local.OjtDatabase/4.json').read_text())['database']
        for entity in schema['entities']:
            actual = {row[1]: (row[2], bool(row[3])) for row in self.db.execute('PRAGMA table_info(' + entity['tableName'] + ')')}
            self.assertEqual({f['columnName']: (f['affinity'], f.get('notNull', False)) for f in entity['fields']}, actual)
        self.assertEqual(set(P3_TRIGGERS) | {'activity_insert_guard', 'activity_update_guard'}, {row[0] for row in self.db.execute("SELECT name FROM sqlite_master WHERE type='trigger'")})

    def test_create_edit_preserve_creation_time_and_photo_order(self):
        self.create()
        self.db.execute("UPDATE daily_activities SET title='Edited',description='More detail',photoPaths='[\"second.jpg\",\"first.jpg\"]',timeMinutes=870,updatedAt=2000,revision=1 WHERE id='new'")
        row = self.db.execute("SELECT title,createdAt,updatedAt,photoPaths,timeMinutes,revision FROM daily_activities WHERE id='new'").fetchone()
        self.assertEqual(('Edited',1000,2000,'["second.jpg","first.jpg"]',870,1), row)

    def test_empty_title_description_invalid_time_and_revision_rejected(self):
        for title, description, time in [(' ', 'd', None), ('t', ' ', None), ('t', 'd', -1), ('t', 'd', 1440)]:
            with self.subTest(title=title, time=time), self.assertRaises(sqlite3.IntegrityError): self.create(title=title, description=description, time=time)

    def test_duplicate_create_cannot_overwrite_record(self):
        self.create()
        with self.assertRaises(sqlite3.IntegrityError): self.create(title='Accidental duplicate')
        self.assertEqual('Title', self.db.execute("SELECT title FROM daily_activities WHERE id='new'").fetchone()[0])

    def test_stale_edit_and_accidental_creation_timestamp_change_rejected(self):
        self.create()
        self.db.execute("UPDATE daily_activities SET revision=1,updatedAt=2000 WHERE id='new'")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE daily_activities SET title='duplicate',revision=1 WHERE id='new'")
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE daily_activities SET createdAt=500,revision=2 WHERE id='new'")

    def test_delete_requires_expected_revision_and_leaves_other_records(self):
        self.create('first'); self.create('second')
        self.assertEqual(0, self.db.execute(DELETE, {'id':'first', 'revision':1}).rowcount)
        self.assertEqual(1, self.db.execute(DELETE, {'id':'first', 'revision':0}).rowcount)
        self.assertIsNotNone(self.db.execute("SELECT id FROM daily_activities WHERE id='second'").fetchone())

    def test_failed_edit_transaction_rolls_back_metadata_and_references(self):
        self.create(); self.db.commit()
        before = self.db.execute("SELECT * FROM daily_activities WHERE id='new'").fetchone()
        self.db.execute('BEGIN IMMEDIATE')
        self.db.execute("UPDATE daily_activities SET title='uncommitted',revision=1,photoPaths='[]',updatedAt=2000 WHERE id='new'")
        self.db.rollback()
        self.assertEqual(before, self.db.execute("SELECT * FROM daily_activities WHERE id='new'").fetchone())

    def test_default_sort_is_newest_day_time_then_created(self):
        self.create('early', time=480); self.create('late', time=900); self.create('newday', date=21001)
        self.assertEqual(['newday', 'late', 'early'], [row[0] for row in self.db.execute(SORT).fetchall()][:3])


if __name__ == '__main__':
    result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(ActivityMigrationTest))
    raise SystemExit(0 if result.wasSuccessful() else 1)
