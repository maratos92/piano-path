package org.pianopath.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.pianopath.core.*

@Entity data class SessionEntity(@PrimaryKey(autoGenerate=true) val id: Long = 0, val title: String, val date: Long,
    val pitch: Double, val rhythm: Double?, val overall: Double, val correct: Int, val expected: Int,
    val extra: Int, val missing: Int, val onsetMs: Double?, val durationMs: Double?, val mode: String,
    val bpm: Double, val category: String, val simulated: Boolean, val reviewFile: String? = null)
@Entity data class SectionEntity(@PrimaryKey(autoGenerate=true) val id: Long = 0, val scoreKey: String,
    val name: String, val startBeat: Double, val endBeat: Double, val bpm: Double)
@Entity data class ImportEntity(@PrimaryKey val key: String, val title: String, val composer: String, val fileName: String)
@Dao interface PianoDao {
    @Query("SELECT * FROM SessionEntity ORDER BY date DESC LIMIT 300") fun history(): Flow<List<SessionEntity>>
    @Insert suspend fun saveSession(value: SessionEntity)
    @Query("SELECT * FROM SectionEntity WHERE scoreKey = :key ORDER BY id DESC") suspend fun sections(key: String): List<SectionEntity>
    @Insert suspend fun saveSection(value: SectionEntity)
    @Query("SELECT * FROM ImportEntity ORDER BY title") fun imports(): Flow<List<ImportEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveImport(value: ImportEntity)
}
@Database(entities=[SessionEntity::class,SectionEntity::class,ImportEntity::class],version=2,exportSchema=false)
abstract class PianoDatabase: RoomDatabase() { abstract fun dao(): PianoDao
    companion object { fun create(context: Context) = Room.databaseBuilder(context,PianoDatabase::class.java,"piano-path.db").addMigrations(object: androidx.room.migration.Migration(1,2) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE SessionEntity ADD COLUMN reviewFile TEXT") } }).build() }
}
data class LibraryPiece(val key: String, val title: String, val composer: String, val difficulty: String,
    val asset: String, val license: String, val attribution: String, val source: String)
/** No demo music is bundled. Only a player's private imports populate the Library screen. */
@Suppress("UNUSED_PARAMETER")
fun loadLibrary(context: Context): List<LibraryPiece> = emptyList()
