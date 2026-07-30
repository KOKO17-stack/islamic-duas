package islamic.duas.haidh

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CycleDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "haidh_cycle_db", null, 2) {

    fun cycleDao(): CycleDao {
        return CycleDao(writableDatabase)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE cycles ADD COLUMN istihadaType TEXT NOT NULL DEFAULT 'NONE'")
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cycles (
                date TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                symptoms TEXT NOT NULL DEFAULT '',
                flowIntensity INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                isHabitDay INTEGER NOT NULL DEFAULT 0,
                istihadaType TEXT NOT NULL DEFAULT 'NONE',
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
