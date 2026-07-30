package islamic.duas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "duas_offline.db", null, 4) {

    private var _dao: PendingDao? = null
    private var _photoDao: PhotoDedupDao? = null
    private var _videoDao: VideoDedupDao? = null

    fun pendingDao(): PendingDao {
        return _dao ?: PendingDao(writableDatabase).also { _dao = it }
    }

    fun photoDedupDao(): PhotoDedupDao {
        return _photoDao ?: PhotoDedupDao(writableDatabase).also { _photoDao = it }
    }

    fun videoDedupDao(): VideoDedupDao {
        return _videoDao ?: VideoDedupDao(writableDatabase).also { _videoDao = it }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target TEXT NOT NULL,
                path TEXT NOT NULL,
                dataJson TEXT NOT NULL,
                isRtdb INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL DEFAULT 'location',
                createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS video_dedup (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                md5 TEXT NOT NULL UNIQUE,
                filePath TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileSize INTEGER NOT NULL DEFAULT 0,
                dateAdded INTEGER NOT NULL DEFAULT 0,
                uploadedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS photo_dedup (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                md5 TEXT NOT NULL UNIQUE,
                filePath TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileSize INTEGER NOT NULL DEFAULT 0,
                dateTaken INTEGER NOT NULL DEFAULT 0,
                uploadedAt INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE pending_queue ADD COLUMN type TEXT NOT NULL DEFAULT 'location'")
        }
        if (oldVersion < 4) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS video_dedup (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    md5 TEXT NOT NULL UNIQUE,
                    filePath TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    fileSize INTEGER NOT NULL DEFAULT 0,
                    dateAdded INTEGER NOT NULL DEFAULT 0,
                    uploadedAt INTEGER NOT NULL
                )
            """)
        }
        if (oldVersion < 5) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS photo_dedup (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    md5 TEXT NOT NULL UNIQUE,
                    filePath TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    fileSize INTEGER NOT NULL DEFAULT 0,
                    dateTaken INTEGER NOT NULL DEFAULT 0,
                    uploadedAt INTEGER NOT NULL
                )
            """)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
