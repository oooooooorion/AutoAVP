package com.example.autoavp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mail_items",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE // Si on supprime une session, on supprime ses courriers
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MailItemEntity(
    @PrimaryKey(autoGenerate = true)
    val mailId: Long = 0,
    val sessionId: Long,
    val trackingNumber: String?, // Peut être vide si illisible
    val recipientName: String?,
    val recipientAddress: String?,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val rawOcrText: String? = null, // Pour debug ou correction manuelle
    val isPrinted: Boolean = false,
    val validationStatus: String = "CALCULATED", // VERIFIED, WARNING, CALCULATED
    val luhnKey: String? = null,
    val isoKey: String? = null,
    val ocrKey: String? = null
)
