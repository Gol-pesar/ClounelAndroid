package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.SocksProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SocksProfileDao {
    @Query("SELECT * FROM socks_profiles ORDER BY isSelected DESC, createdAt DESC")
    fun observeAllProfiles(): Flow<List<SocksProfile>>

    @Query("SELECT * FROM socks_profiles ORDER BY isSelected DESC, createdAt DESC")
    suspend fun getAllProfiles(): List<SocksProfile>

    @Query("SELECT * FROM socks_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): SocksProfile?

    @Query("SELECT * FROM socks_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): SocksProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SocksProfile): Long

    @Update
    suspend fun updateProfile(profile: SocksProfile)

    @Query("DELETE FROM socks_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE socks_profiles SET isSelected = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setOnlySelected(id: Long)

    @Query("UPDATE socks_profiles SET lastLatencyMs = :latencyMs, lastExitIp = :exitIp, lastExitCountry = :exitCountry WHERE id = :id")
    suspend fun updateProbeStats(id: Long, latencyMs: Int?, exitIp: String?, exitCountry: String?)

    @Transaction
    suspend fun selectProfile(id: Long) {
        setOnlySelected(id)
    }
}
