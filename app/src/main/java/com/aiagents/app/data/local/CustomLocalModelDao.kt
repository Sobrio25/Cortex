package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiagents.app.data.model.CustomLocalModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomLocalModelDao {
    @Query("SELECT * FROM custom_local_models ORDER BY addedAt DESC")
    fun getAll(): Flow<List<CustomLocalModelEntity>>

    @Query("SELECT * FROM custom_local_models")
    suspend fun getAllSync(): List<CustomLocalModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: CustomLocalModelEntity)

    @Query("DELETE FROM custom_local_models WHERE id = :id")
    suspend fun delete(id: String)
}
