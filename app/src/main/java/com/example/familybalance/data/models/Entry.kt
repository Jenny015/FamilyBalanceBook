package com.example.familybalance.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Entry(
    var id: Int = 0,
    var role: Int = 0, // 0 = Mom (Credit), 1 = Daughter (Debit)
    var date: String = "",
    var title: String = "",
    var detail: String? = "",
    var amount: Double = 0.0
)