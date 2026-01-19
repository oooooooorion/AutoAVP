package com.example.autoavp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.autoavp.data.local.entities.MailItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MailItemDao {
    @Insert
    suspend fun insertMailItem(mailItem: MailItemEntity): Long

    @Update
    suspend fun updateMailItem(mailItem: MailItemEntity)

    @Delete
    suspend fun deleteMailItem(mailItem: MailItemEntity)

    @Query("SELECT * FROM mail_items WHERE sessionId = :sessionId ORDER BY scanTimestamp DESC")
    fun getMailsForSession(sessionId: Long): Flow<List<MailItemEntity>>

    @Query("SELECT * FROM mail_items WHERE mailId = :mailId")
    suspend fun getMailItemById(mailId: Long): MailItemEntity?
}
