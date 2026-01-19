package com.example.autoavp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.autoavp.data.local.dao.InstanceOfficeDao
import com.example.autoavp.data.local.dao.MailItemDao
import com.example.autoavp.data.local.dao.SessionDao
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.data.local.entities.SessionEntity

@Database(
    entities = [SessionEntity::class, MailItemEntity::class, InstanceOfficeEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AutoAvpDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun mailItemDao(): MailItemDao
    abstract fun instanceOfficeDao(): InstanceOfficeDao
}
