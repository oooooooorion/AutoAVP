package com.example.autoavp.data.repository

import com.example.autoavp.data.local.dao.MailItemDao
import com.example.autoavp.data.local.dao.SessionDao
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.data.local.entities.SessionEntity
import com.example.autoavp.domain.model.ScannedData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val mailItemDao: MailItemDao
) {

    // Créer une nouvelle session et retourner son ID
    suspend fun createSession(): Long {
        return sessionDao.insertSession(SessionEntity())
    }

    // Sauvegarder un courrier scanné
    suspend fun saveScannedItem(sessionId: Long, data: ScannedData) {
        val entity = MailItemEntity(
            sessionId = sessionId,
            trackingNumber = data.trackingNumber,
            recipientName = data.recipientName,
            recipientAddress = data.rawText, // On stocke le brut pour l'instant
            rawOcrText = data.rawText,
            isPrinted = false,
            validationStatus = data.confidenceStatus.name,
            luhnKey = data.luhnKey,
            isoKey = data.isoKey,
            ocrKey = data.ocrKey
        )
        mailItemDao.insertMailItem(entity)
    }
    
    // Récupérer la session la plus récente
    fun getLatestSession(): Flow<SessionEntity?> {
        return sessionDao.getLatestSession()
    }

    // Récupérer les courriers d'une session (pour la liste en temps réel)
    fun getItemsForSession(sessionId: Long): Flow<List<MailItemEntity>> {
        return mailItemDao.getMailsForSession(sessionId)
    }

    suspend fun updateMailItem(item: MailItemEntity) {
        mailItemDao.updateMailItem(item)
    }

    suspend fun deleteMailItem(item: MailItemEntity) {
        mailItemDao.deleteMailItem(item)
    }
}
