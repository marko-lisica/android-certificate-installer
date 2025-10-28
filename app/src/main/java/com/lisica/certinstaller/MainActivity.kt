package com.lisica.certinstaller

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Activity for testing. Provides a UI to manually trigger the certificate installation
 * and display the status and any errors.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var caListTextView: TextView
    private lateinit var installButton: Button
    private lateinit var dpm: DevicePolicyManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views and DPM
        statusTextView = findViewById(R.id.statusTextView)
        installButton = findViewById(R.id.installButton)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        installButton.setOnClickListener {
            startInstallation()
        }

        // Initial check when the activity is created
        updateDelegationStatus()
    }

    /**
     * Checks if the app has been delegated the DELEGATION_CERT_INSTALL scope.
     * Updates the UI accordingly.
     */
    private fun updateDelegationStatus() {
        val packageName = packageName
        val scopes = dpm.getDelegatedScopes(null, packageName)

        if (scopes.contains(DevicePolicyManager.DELEGATION_CERT_INSTALL)) {
            setStatus("Delegation Granted! Ready to install.", R.color.green)
            installButton.isEnabled = true
        } else {
            setStatus("Delegation NOT Granted. Please configure EMM.", R.color.red)
            installButton.isEnabled = false
        }
    }

    /**
     * Triggers the certificate installation process on a background thread.
     */
    private fun startInstallation() {
        installButton.isEnabled = false
        setStatus("Installation in progress...", R.color.blue)

        // Use lifecycleScope to run coroutine safely tied to the Activity's lifecycle
        lifecycleScope.launch(Dispatchers.IO) {
            val result = CertificateInstaller.install(this@MainActivity)

            // Switch back to the main thread to update the UI
            withContext(Dispatchers.Main) {
                installButton.isEnabled = true
                if (result.isSuccess) {
                    setStatus("SUCCESS: Key Pair installed as alias '${CertificateInstaller.CERT_ALIAS}'!", R.color.green)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("FAILURE: $message", R.color.red)
                }
            }
        }
    }

    /**
     * Fetches and displays the list of installed CA certificates.
     */
    private fun updateCaCertificateList() {
        caListTextView.text = "Fetching CA list..."
        caListTextView.setTextColor(getColor(R.color.blue))

        lifecycleScope.launch(Dispatchers.IO) {
            val result = CertificateInstaller.getInstalledCaCertDetails(this@MainActivity)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val details = result.getOrThrow()
                    val count = details.size
                    val listText = if (count == 0) {
                        "0 CA Certificates installed."
                    } else {
                        "Found $count CA Certificate(s):\n" + details.joinToString("\n")
                    }
                    caListTextView.text = listText
                    caListTextView.setTextColor(getColor(R.color.gray))
                } else {
                    val message = result.exceptionOrNull()?.message
                    caListTextView.text = "CA List Error: ${message ?: "Security Exception (Delegation Check Failed)"}"
                    caListTextView.setTextColor(getColor(R.color.red))
                }
            }
        }
    }

    /**
     * Helper to set text and color on the status TextView.
     */
    private fun setStatus(message: String, colorResId: Int) {
        statusTextView.text = message
        statusTextView.setTextColor(getColor(colorResId))
    }
}
