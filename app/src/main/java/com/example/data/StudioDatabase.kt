package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_characters")
data class CustomCharacterEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameBn: String,
    val categoryName: String,
    val rigTypeName: String,
    val imagePath: String,
    val hasTransparency: Boolean,
    val puppetConfigSerialized: String,
    val meshPointsSerialized: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "studio_captures")
data class StudioCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val mediaStoreUri: String?,
    val isVideo: Boolean,
    val characterName: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM custom_characters ORDER BY createdAt DESC")
    fun observeCustomCharacters(): Flow<List<CustomCharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomCharacter(entity: CustomCharacterEntity)

    @Query("DELETE FROM custom_characters WHERE id = :id")
    suspend fun deleteCustomCharacter(id: String)

    @Query("SELECT * FROM studio_captures ORDER BY createdAt DESC")
    fun observeStudioCaptures(): Flow<List<StudioCaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudioCapture(entity: StudioCaptureEntity): Long

    @Query("DELETE FROM studio_captures WHERE id = :id")
    suspend fun deleteStudioCapture(id: Long)
}

@Database(
    entities = [CustomCharacterEntity::class, StudioCaptureEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "faceswap_studio.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
