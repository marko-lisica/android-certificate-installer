package com.lisica.certinstaller

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
    private lateinit var configTextView: TextView
    private lateinit var installButton: Button
    private lateinit var installCaButton: Button
    private lateinit var aliasEditText: EditText
    private lateinit var removeButton: Button
    private lateinit var viewCertificatesButton: Button
    private lateinit var dpm: DevicePolicyManager

    private var keyPairCount = 0
    private var caCertCount = 0

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views and DPM
        statusTextView = findViewById(R.id.statusTextView)
        configTextView = findViewById(R.id.configTextView)
        installButton = findViewById(R.id.installButton)
        installCaButton = findViewById(R.id.installCaButton)
        aliasEditText = findViewById(R.id.aliasEditText)
        removeButton = findViewById(R.id.removeButton)
        viewCertificatesButton = findViewById(R.id.viewCertificatesButton)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        installButton.setOnClickListener {
            startInstallation()
        }

        installCaButton.setOnClickListener {
            startCaInstallation()
        }

        removeButton.setOnClickListener {
            startRemoval()
        }

        viewCertificatesButton.setOnClickListener {
            val intent = Intent(this, CertificateListActivity::class.java)
            startActivity(intent)
        }

        // Initial check when the activity is created
        updateDelegationStatus()

        // Load certificate counts
        updateCertificateCounts()

        // Display current configuration
        updateConfiguration()
    }

    override fun onResume() {
        super.onResume()
        // Refresh counts when returning to this activity
        updateCertificateCounts()
    }

    /**
     * Checks if the app has been delegated the DELEGATION_CERT_INSTALL scope
     * and if the required configuration is present.
     * Updates the UI accordingly.
     */
    private fun updateDelegationStatus() {
        val packageName = packageName
        val scopes = dpm.getDelegatedScopes(null, packageName)

        val hasCertInstall = scopes.contains(DevicePolicyManager.DELEGATION_CERT_INSTALL)

        // Validate configuration
        val configResult = CertificateInstaller.validateConfiguration(this)
        val hasValidConfig = configResult.isSuccess

        val statusMessage = buildString {
            if (hasCertInstall) {
                append("Delegation: CERT_INSTALL ✓")
            } else {
                append("Delegation: CERT_INSTALL ✗")
            }

            append("\n")

            if (hasValidConfig) {
                append("Configuration: ✓")
            } else {
                append("Configuration: ✗ (Not set)")
            }
        }

        if (hasCertInstall && hasValidConfig) {
            setStatus(statusMessage, R.color.green)
        } else {
            setStatus(statusMessage, R.color.red)
        }

        // Enable/disable buttons based on delegation AND configuration
        val enableButtons = hasCertInstall && hasValidConfig
        installButton.isEnabled = enableButtons
        installCaButton.isEnabled = enableButtons

        // Remove button only needs delegation (doesn't need config)
        removeButton.isEnabled = hasCertInstall
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
                    val installedAlias = result.getOrNull()
                    setStatus("SUCCESS: Key Pair installed as alias '$installedAlias'!", R.color.green)
                    // Refresh the certificate counts after successful installation
                    updateCertificateCounts()
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("FAILURE: $message", R.color.red)
                }
            }
        }
    }

    /**
     * Triggers the CA certificate installation process on a background thread.
     */
    private fun startCaInstallation() {
        installCaButton.isEnabled = false
        setStatus("Installing CA certificate...", R.color.blue)

        // Use lifecycleScope to run coroutine safely tied to the Activity's lifecycle
        lifecycleScope.launch(Dispatchers.IO) {
            val result = CertificateInstaller.installCaCert(this@MainActivity)

            // Switch back to the main thread to update the UI
            withContext(Dispatchers.Main) {
                installCaButton.isEnabled = true
                if (result.isSuccess) {
                    val certCN = result.getOrNull()
                    setStatus("SUCCESS: CA Certificate installed! CN: '$certCN'", R.color.green)
                    // Refresh the certificate counts after successful installation
                    updateCertificateCounts()
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("FAILURE: $message", R.color.red)
                }
            }
        }
    }

    /**
     * Triggers the certificate removal process on a background thread.
     */
    private fun startRemoval() {
        val alias = aliasEditText.text.toString().trim()

        if (alias.isEmpty()) {
            setStatus("ERROR: Please enter a certificate alias", R.color.red)
            return
        }

        removeButton.isEnabled = false
        setStatus("Removing certificate '$alias'...", R.color.blue)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = CertificateInstaller.removeKeyPair(this@MainActivity, alias)

            withContext(Dispatchers.Main) {
                removeButton.isEnabled = true
                if (result.isSuccess) {
                    val message = result.getOrNull()
                    setStatus("SUCCESS: $message", R.color.green)
                    aliasEditText.text.clear()
                    // Refresh the certificate counts after successful removal
                    updateCertificateCounts()
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("FAILURE: $message", R.color.red)
                }
            }
        }
    }

    /**
     * Fetches certificate counts and updates the button text.
     */
    private fun updateCertificateCounts() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Fetch key pair count
            val keyPairResult = CertificateInstaller.getInstalledKeyPairDetails(this@MainActivity)
            val keyPairs = if (keyPairResult.isSuccess) {
                val details = keyPairResult.getOrThrow()
                if (details.isEmpty() || (details.size == 1 && details[0].contains("No key pairs found"))) {
                    0
                } else {
                    details.size
                }
            } else {
                0
            }

            // Fetch CA cert count
            val caCertResult = CertificateInstaller.getInstalledCaCertDetails(this@MainActivity)
            val caCerts = if (caCertResult.isSuccess) {
                caCertResult.getOrThrow().size
            } else {
                0
            }

            withContext(Dispatchers.Main) {
                keyPairCount = keyPairs
                caCertCount = caCerts
                updateButtonText()
            }
        }
    }

    /**
     * Updates the view certificates button text with counts.
     */
    private fun updateButtonText() {
        val buttonText = buildString {
            append("View Certificates")
            if (keyPairCount > 0 || caCertCount > 0) {
                append("  ")
                if (keyPairCount > 0) {
                    append("🔑 $keyPairCount")
                }
                if (caCertCount > 0) {
                    if (keyPairCount > 0) append("  ")
                    append("🏛️ $caCertCount")
                }
            }
        }
        viewCertificatesButton.text = buttonText
    }

    /**
     * Helper to set text and color on the status TextView.
     */
    private fun setStatus(message: String, colorResId: Int) {
        statusTextView.text = message
        statusTextView.setTextColor(getColor(colorResId))
    }

    /**
     * Displays the current managed configuration settings.
     */
    private fun updateConfiguration() {
        val configResult = CertificateInstaller.validateConfiguration(this)

        if (configResult.isSuccess) {
            val config = configResult.getOrThrow()
            val configText = buildString {
                append("Certificate URL:\n")
                append("${config.certDownloadUrl}\n\n")
                append("CA Certificate URL:\n")
                append("${config.caCertDownloadUrl}\n\n")
                append("PKCS#12 Password:\n")
                append("********")
            }
            configTextView.text = configText
            configTextView.setTextColor(getColor(R.color.gray))
        } else {
            // Show error if configuration is missing
            val restrictionsManager = getSystemService(Context.RESTRICTIONS_SERVICE) as android.content.RestrictionsManager
            val appRestrictions = restrictionsManager.applicationRestrictions

            val certUrl = appRestrictions.getString("cert_download_url")
            val caUrl = appRestrictions.getString("ca_cert_download_url")
            val password = appRestrictions.getString("p12_password")

            val configText = buildString {
                append("⚠️ CONFIGURATION REQUIRED ⚠️\n\n")
                append("Certificate URL: ")
                append(if (certUrl.isNullOrEmpty()) "❌ NOT SET" else "✓ Set")
                append("\n\n")
                append("CA Certificate URL: ")
                append(if (caUrl.isNullOrEmpty()) "❌ NOT SET" else "✓ Set")
                append("\n\n")
                append("PKCS#12 Password: ")
                append(if (password.isNullOrEmpty()) "❌ NOT SET" else "✓ Set")
                append("\n\n")
                append("Please configure via MDM/EMM.")
            }
            configTextView.text = configText
            configTextView.setTextColor(getColor(R.color.red))
        }
    }
}
