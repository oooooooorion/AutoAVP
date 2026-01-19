package com.example.autoavp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceOfficeDao {
    @Insert
    suspend fun insertOffice(office: InstanceOfficeEntity): Long

    @Update
    suspend fun updateOffice(office: InstanceOfficeEntity)

    @Delete
    suspend fun deleteOffice(office: InstanceOfficeEntity)

    @Query("SELECT * FROM instance_offices ORDER BY name ASC")
    fun getAllOffices(): Flow<List<InstanceOfficeEntity>>

    @Query("SELECT * FROM instance_offices WHERE officeId = :id")
    suspend fun getOfficeById(id: Long): InstanceOfficeEntity?
}
