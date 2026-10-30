package io.github.jakub_m_arch.voicemator.profiles

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.jakub_m_arch.voicemator.text.TextStyle
import kotlinx.coroutines.flow.Flow

/**
 * How dictation behaves in a group of apps, such as messengers or email, like Power Mode in
 * VoiceInk. Dictionary entries can belong to a profile too; they apply on top of the general ones.
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "capitalize_first") val capitalizeFirst: Boolean = true,
    @ColumnInfo(name = "end_with_period") val endWithPeriod: Boolean = true,
    /** Whether the whole recording is transcribed again at the end; null follows the app setting. */
    @ColumnInfo(name = "final_pass") val finalPass: Boolean? = null,
) {
    val textStyle get() = TextStyle(capitalizeFirst = capitalizeFirst, endWithPeriod = endWithPeriod)
}

/** An app, by package name, that uses a profile. An app belongs to at most one profile. */
@Entity(tableName = "profile_apps", indices = [Index("profile_id")])
data class ProfileApp(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
)

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<Profile?>

    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<Profile>

    @Query("SELECT * FROM profile_apps")
    fun observeApps(): Flow<List<ProfileApp>>

    @Query("SELECT * FROM profile_apps")
    suspend fun getApps(): List<ProfileApp>

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    /** Adds the app to a profile, moving it out of any other. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assign(app: ProfileApp)

    @Query("DELETE FROM profile_apps WHERE package_name = :packageName")
    suspend fun unassign(packageName: String)

    /** Deletes the profile with its app list and its dictionary entries. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteApps(id)
        deleteDictionary(id)
        deleteProfile(id)
    }

    @Query("DELETE FROM profile_apps WHERE profile_id = :id")
    suspend fun deleteApps(id: Long)

    @Query("DELETE FROM dictionary WHERE profile_id = :id")
    suspend fun deleteDictionary(id: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)
}
