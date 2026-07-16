package islamic.duas.haidh

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CycleDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "haidh_cycle_db", null, 1) {

    private var _dao: CycleDao? = null

    fun cycleDao(): CycleDao {
        return _dao ?: CycleDao(writableDatabase).also { _dao = it }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cycles (
                date TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                symptoms TEXT NOT NULL DEFAULT '',
                flowIntensity INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                isHabitDay INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cycle_phases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                startDate TEXT NOT NULL,
                endDate TEXT NOT NULL,
                status TEXT NOT NULL,
                cycleDay INTEGER NOT NULL DEFAULT 1
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cycles")
        db.execSQL("DROP TABLE IF EXISTS cycle_phases")
        onCreate(db)
    }

    companion object {
        @Volatile
        private var INSTANCE: CycleDatabase? = null

        fun getInstance(context: Context): CycleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CycleDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
