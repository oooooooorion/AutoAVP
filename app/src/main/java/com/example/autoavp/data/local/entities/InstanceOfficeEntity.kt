package com.example.autoavp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instance_offices")
data class InstanceOfficeEntity(
    @PrimaryKey(autoGenerate = true)
    val officeId: Long = 0,
    val name: String,
    val address: String,
    val openingHours: String,
    val colorHex: String // Format hexadécimal "#RRGGBB"
)
