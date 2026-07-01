package islamic.duas.haidh

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CycleEntity::class, SymptomEntity::class, CyclePhaseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CycleDatabase : RoomDatabase() {

    abstract fun cycleDao(): CycleDao

    companion object {
        @Volatile
        private var INSTANCE: CycleDatabase? = null

        fun getInstance(context: Context): CycleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CycleDatabase::class.java,
                    "haidh_cycle_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
