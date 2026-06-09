package com.example.familybalance.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROLE = "user_role"
        const val ROLE_MOM = 0
        const val ROLE_DAUGHTER = 1
        const val ROLE_UNDECIDED = -1
    }

    fun saveRole(role: Int) {
        prefs.edit { putInt(KEY_ROLE, role) }
    }

    fun getRole(): Int {
        return prefs.getInt(KEY_ROLE, ROLE_UNDECIDED)
    }
}