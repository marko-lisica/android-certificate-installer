package com.lisica.certinstaller

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * A helper object to encapsulate the actual certificate installation logic.
 */
object CertificateInstaller {

    private const val TAG = "CertificateInstaller"

    // The alias you want the certificate to have in the Android KeyStore
    const val CERT_ALIAS = "my-corporate-alias" // Changed to 'const val' and public for MainActivity

    /**
     * Installs the key pair. Returns a Result object indicating success or failure.
     */
    fun install(context: Context): Result<Unit> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        return runCatching {
            // --- TODO: Implement your certificate loading logic here ---
            // This example assumes you have a PKCS#12 file (.p12 or .pfx)

            // WARNING: In a production app, the password MUST NOT be hardcoded!
            val p12Password = "test12345".toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")

            // Load the keystore from the raw resource file
            // Replace `R.raw.my_keystore` with your actual file
            context.resources.openRawResource(R.raw.certificate).use { inputStream ->
                keyStore.load(inputStream, p12Password)
            }

            // Find the alias within the P12 file.
            val p12Alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) }

            // Use require to throw an exception if the keystore is invalid, which runCatching will catch.
            require(p12Alias != null) { "Could not find a key entry in the provided keystore. Check alias and file contents." }

            // Extract the private key and certificate
            val privateKey = keyStore.getKey(p12Alias, p12Password) as PrivateKey
            val certificate = keyStore.getCertificate(p12Alias)

            // --- End of loading logic ---

            // Now, attempt to install the KeyPair using the DPM API
            val success = dpm.installKeyPair(
                null, // Admin component - null when called by a delegated app
                privateKey,
                certificate,
                CERT_ALIAS
            )

            // Use require to throw an exception if DPM returns false
            require(success) { "DPM installKeyPair returned false. This may be due to key/cert format or permission issues." }

            Log.i(TAG, "Successfully installed key pair with alias: $CERT_ALIAS")
        }
    }
    /**
     * Retrieves details (CN and Serial Number) for all installed CA certificates.
     *
     * @return A Result containing a List of formatted strings (CN + Serial) on success,
     * or an exception on failure (e.g., SecurityException if delegation is missing).
     */
    fun getInstalledCaCertDetails(context: Context): Result<List<String>> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val certFactory = CertificateFactory.getInstance("X.509")

        return runCatching {
            // The admin argument must be null for a delegated app.
            val caCertsBytes = dpm.getInstalledCaCerts(null)

            if (caCertsBytes.isEmpty()) {
                return@runCatching emptyList()
            }

            // Map the byte arrays to X509Certificate objects and extract details
            caCertsBytes.mapNotNull { certBytes ->
                try {
                    val cert = certFactory.generateCertificate(
                        ByteArrayInputStream(certBytes)
                    ) as X509Certificate

                    // Extract the Common Name (CN) from the Subject Distinguished Name
                    val subjectDn = cert.subjectX500Principal.getName(X500Principal.RFC2253)
                    val cnMatch = "CN=([^,]+)".toRegex().find(subjectDn)
                    val commonName = cnMatch?.groupValues?.getOrNull(1) ?: "Unknown CN"

                    // Format the serial number for readability (hex string)
                    val serialNumber = cert.serialNumber.toString(16).uppercase()

                    "CN: $commonName, Serial: $serialNumber"
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing CA certificate: ${e.message}", e)
                    "Error parsing certificate."
                }
            }
        }
    }
}
