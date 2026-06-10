package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amerganim.lockapp.databinding.ActivitySetPinBinding

/** First-run screen: enter a PIN, then confirm it. */
class SetPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetPinBinding
    private lateinit var pinManager: PinManager
    private lateinit var pinPad: PinPad

    private var firstEntry: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinManager = PinManager(this)

        pinPad = PinPad(binding.keypad, binding.dots) { pin -> onPinEntered(pin) }
    }

    private fun onPinEntered(pin: String) {
        val first = firstEntry
        if (first == null) {
            // First entry — ask for confirmation.
            firstEntry = pin
            binding.title.setText(R.string.confirm_pin_title)
            pinPad.reset()
        } else if (pin == first) {
            pinManager.setPin(pin)
            LockState.settingsAuthed = true // they just proved who they are
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            // Mismatch — start over.
            firstEntry = null
            binding.title.setText(R.string.set_pin_title)
            pinPad.reset()
            Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
        }
    }
}
