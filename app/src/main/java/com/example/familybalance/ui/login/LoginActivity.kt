package com.example.familybalance.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.familybalance.R
import com.example.familybalance.ui.main.MainActivity
import com.example.familybalance.utils.PreferencesManager

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        // If the user already set their identity, skip directly to MainActivity
        if (prefs.getRole() != PreferencesManager.ROLE_UNDECIDED) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        findViewById<Button>(R.id.btn_mom).setOnClickListener {
            prefs.saveRole(PreferencesManager.ROLE_MOM)
            startMainActivity()
        }

        findViewById<Button>(R.id.btn_daughter).setOnClickListener {
            prefs.saveRole(PreferencesManager.ROLE_DAUGHTER)
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}