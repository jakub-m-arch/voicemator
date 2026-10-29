package io.github.jakub_m_arch.voicemator.text

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A dictionary entry as stored on the device. */
@Entity(tableName = "dictionary")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heard: String,
    val written: String,
) {
    fun toReplacement() = Replacement(heard, written)
}

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionary ORDER BY heard COLLATE NOCASE")
    fun observeAll(): Flow<List<DictionaryEntry>>

    @Query("SELECT * FROM dictionary")
    suspend fun getAll(): List<DictionaryEntry>

    @Insert
    suspend fun insert(entry: DictionaryEntry)

    @Query("DELETE FROM dictionary WHERE id = :id")
    suspend fun delete(id: Long)
}
