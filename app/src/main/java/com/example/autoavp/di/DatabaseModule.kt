package com.example.autoavp.di

import android.content.Context
import androidx.room.Room
import com.example.autoavp.data.local.AutoAvpDatabase
import com.example.autoavp.data.local.dao.InstanceOfficeDao
import com.example.autoavp.data.local.dao.MailItemDao
import com.example.autoavp.data.local.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoAvpDatabase {
        return Room.databaseBuilder(
            context,
            AutoAvpDatabase::class.java,
            "auto_avp_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideSessionDao(database: AutoAvpDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideMailItemDao(database: AutoAvpDatabase): MailItemDao {
        return database.mailItemDao()
    }

    @Provides
    fun provideInstanceOfficeDao(database: AutoAvpDatabase): InstanceOfficeDao {
        return database.instanceOfficeDao()
    }
}
