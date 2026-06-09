package com.example.familybalance.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Meta(
    var totalEntries: Int = 0
)