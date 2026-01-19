package com.example.autoavp.data.repository

import com.example.autoavp.data.local.dao.InstanceOfficeDao
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfficeRepository @Inject constructor(
    private val officeDao: InstanceOfficeDao
) {
    fun getAllOffices(): Flow<List<InstanceOfficeEntity>> = officeDao.getAllOffices()

    suspend fun getOfficeById(id: Long): InstanceOfficeEntity? = officeDao.getOfficeById(id)

    suspend fun saveOffice(office: InstanceOfficeEntity) {
        if (office.officeId == 0L) {
            officeDao.insertOffice(office)
        } else {
            officeDao.updateOffice(office)
        }
    }

    suspend fun deleteOffice(office: InstanceOfficeEntity) {
        officeDao.deleteOffice(office)
    }
}
