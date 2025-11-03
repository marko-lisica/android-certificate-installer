package com.lisica.certinstaller

import android.app.admin.DevicePolicyManager
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/**
 * A helper object to encapsulate the actual certificate installation logic.
 */
object CertificateInstaller {

    private const val TAG = "CertificateInstaller"

    // SharedPreferences key for tracking certificate counter
    private const val PREFS_NAME = "CertInstallerPrefs"
    private const val KEY_CERT_COUNTER = "cert_counter"
    private const val KEY_INSTALLED_CERTS = "installed_certs"  // JSON array of installed cert info

    // Managed configuration keys
    private const val KEY_CERT_DOWNLOAD_URL = "cert_download_url"
    private const val KEY_CA_CERT_DOWNLOAD_URL = "ca_cert_download_url"
    private const val KEY_P12_PASSWORD = "p12_password"

    /**
     * Retrieves configuration from managed configuration (app restrictions).
     * Returns null if any required configuration is missing.
     */
    private fun getConfig(context: Context): ConfigData? {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as android.content.RestrictionsManager
        val appRestrictions = restrictionsManager.applicationRestrictions

        val certUrl = appRestrictions.getString(KEY_CERT_DOWNLOAD_URL)
        val caUrl = appRestrictions.getString(KEY_CA_CERT_DOWNLOAD_URL)
        val password = appRestrictions.getString(KEY_P12_PASSWORD)

        // Return null if any required field is missing or empty
        if (certUrl.isNullOrEmpty() || caUrl.isNullOrEmpty() || password.isNullOrEmpty()) {
            return null
        }

        return ConfigData(
            certDownloadUrl = certUrl,
            caCertDownloadUrl = caUrl,
            p12Password = password
        )
    }

    /**
     * Validates that all required managed configuration is present.
     * Returns a Result containing the validation error or success.
     */
    fun validateConfiguration(context: Context): Result<ConfigData> {
        return runCatching {
            val config = getConfig(context)
            require(config != null) {
                "Managed configuration not set. Please configure cert_download_url, ca_cert_download_url, and p12_password via MDM."
            }
            config
        }
    }

    /**
     * Data class to hold configuration values.
     */
    data class ConfigData(
        val certDownloadUrl: String,
        val caCertDownloadUrl: String,
        val p12Password: String
    )

    /**
     * Data class to hold installed certificate metadata.
     */
    data class CertificateInfo(
        val alias: String,
        val commonName: String,
        val subjectDN: String,
        val issuerDN: String,
        val serialNumber: String,
        val validFrom: Long,
        val validTo: Long,
        val installedAt: Long
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("alias", alias)
                put("commonName", commonName)
                put("subjectDN", subjectDN)
                put("issuerDN", issuerDN)
                put("serialNumber", serialNumber)
                put("validFrom", validFrom)
                put("validTo", validTo)
                put("installedAt", installedAt)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): CertificateInfo {
                return CertificateInfo(
                    alias = json.getString("alias"),
                    commonName = json.getString("commonName"),
                    subjectDN = json.getString("subjectDN"),
                    issuerDN = json.getString("issuerDN"),
                    serialNumber = json.getString("serialNumber"),
                    validFrom = json.getLong("validFrom"),
                    validTo = json.getLong("validTo"),
                    installedAt = json.getLong("installedAt")
                )
            }
        }
    }

    /**
     * Saves a certificate info to the persistent list.
     */
    private fun saveCertificateInfo(context: Context, certInfo: CertificateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_INSTALLED_CERTS, "[]")
        val certArray = JSONArray(existingJson)

        certArray.put(certInfo.toJson())

        prefs.edit().putString(KEY_INSTALLED_CERTS, certArray.toString()).apply()
    }

    /**
     * Retrieves all saved certificate information.
     */
    private fun getSavedCertificates(context: Context): List<CertificateInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_INSTALLED_CERTS, "[]") ?: "[]"
        val certArray = JSONArray(json)

        val result = mutableListOf<CertificateInfo>()
        for (i in 0 until certArray.length()) {
            try {
                result.add(CertificateInfo.fromJson(certArray.getJSONObject(i)))
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return result
    }

    /**
     * Generates the next unique certificate alias and increments the counter.
     * Returns aliases like: cert1, cert2, cert3, etc.
     */
    private fun getNextCertAlias(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCounter = prefs.getInt(KEY_CERT_COUNTER, 0) + 1

        // Save the incremented counter
        prefs.edit().putInt(KEY_CERT_COUNTER, currentCounter).apply()

        return "cert$currentCounter"
    }

    /**
     * Downloads the certificate file from the remote server.
     *
     * @param url The URL to download from
     * @return ByteArray containing the certificate data
     * @throws Exception if download fails
     */
    private fun downloadCertificate(url: String): ByteArray {

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download certificate: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            return body.bytes()
        }
    }

    /**
     * Installs the key pair. Returns a Result object indicating success or failure.
     * Downloads the certificate from the remote server on each call.
     * Each installation gets a unique alias (cert1, cert2, cert3, etc.)
     *
     * @return Result containing the alias used for the installed certificate
     */
    fun install(context: Context): Result<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        return runCatching {
            // Get configuration from managed configuration
            val config = getConfig(context)
            require(config != null) {
                "Managed configuration not set. Please configure via MDM."
            }

            // Generate a unique alias for this certificate
            val certAlias = getNextCertAlias(context)

            // Download the certificate from the remote server
            val certBytes = downloadCertificate(config.certDownloadUrl)

            // Load the PKCS#12 keystore from the downloaded bytes
            val p12Password = config.p12Password.toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")

            ByteArrayInputStream(certBytes).use { inputStream ->
                keyStore.load(inputStream, p12Password)
            }

            // Find the alias within the P12 file
            val p12Alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) }

            // Use require to throw an exception if the keystore is invalid
            require(p12Alias != null) {
                "Could not find a key entry in the provided keystore. Check alias and file contents."
            }

            // Extract the private key and certificate
            val privateKey = keyStore.getKey(p12Alias, p12Password) as PrivateKey
            val certificate = keyStore.getCertificate(p12Alias) as X509Certificate

            // Extract certificate details for tracking
            val subjectDn = certificate.subjectX500Principal.getName(X500Principal.RFC2253)
            val issuerDn = certificate.issuerX500Principal.getName(X500Principal.RFC2253)
            val cnMatch = "CN=([^,]+)".toRegex().find(subjectDn)
            val commonName = cnMatch?.groupValues?.getOrNull(1) ?: "Unknown CN"
            val serialNumber = certificate.serialNumber.toString(16).uppercase()

            // Attempt to install the KeyPair using the DPM API with the unique alias
            // INSTALLKEY_SET_USER_SELECTABLE makes the certificate available for user selection
            // in apps that use certificate-based authentication (e.g., browsers, VPN clients)
            val flags = DevicePolicyManager.INSTALLKEY_REQUEST_CREDENTIALS_ACCESS or
                       DevicePolicyManager.INSTALLKEY_SET_USER_SELECTABLE

            val success = dpm.installKeyPair(
                null, // Admin component - null when called by a delegated app
                privateKey,
                arrayOf(certificate), // Certificate chain (single certificate in this case)
                certAlias,
                flags
            )

            // Use require to throw an exception if DPM returns false
            require(success) {
                "DPM installKeyPair returned false. This may be due to key/cert format or permission issues."
            }

            // Save certificate metadata for later retrieval
            val certInfo = CertificateInfo(
                alias = certAlias,
                commonName = commonName,
                subjectDN = subjectDn,
                issuerDN = issuerDn,
                serialNumber = serialNumber,
                validFrom = certificate.notBefore.time,
                validTo = certificate.notAfter.time,
                installedAt = System.currentTimeMillis()
            )
            saveCertificateInfo(context, certInfo)

            // Return the alias that was used
            certAlias
        }
    }

    /**
     * Downloads a CA certificate from the remote server.
     *
     * @param url The URL to download from
     * @return ByteArray containing the CA certificate data
     * @throws Exception if download fails
     */
    private fun downloadCaCertificate(url: String): ByteArray {

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download CA certificate: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            return body.bytes()
        }
    }

    /**
     * Installs a CA certificate. Returns a Result object indicating success or failure.
     * Downloads the CA certificate from the remote server on each call.
     *
     * The CA certificate will be used to trust servers that present certificates signed by this CA.
     * This is useful for trusting internal corporate CAs or self-signed certificates.
     *
     * @return Result containing certificate details (CN) on success
     */
    fun installCaCert(context: Context): Result<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        return runCatching {
            // Get configuration from managed configuration
            val config = getConfig(context)
            require(config != null) {
                "Managed configuration not set. Please configure via MDM."
            }

            // Download the CA certificate from the remote server
            val caCertBytes = downloadCaCertificate(config.caCertDownloadUrl)

            // Parse the certificate to extract details before installation
            val certFactory = CertificateFactory.getInstance("X.509")
            val certificate = certFactory.generateCertificate(
                ByteArrayInputStream(caCertBytes)
            ) as X509Certificate

            // Extract Common Name for display
            val subjectDn = certificate.subjectX500Principal.getName(X500Principal.RFC2253)
            val cnMatch = "CN=([^,]+)".toRegex().find(subjectDn)
            val commonName = cnMatch?.groupValues?.getOrNull(1) ?: "Unknown CN"

            // Install the CA certificate using DPM
            // Note: installCaCert takes the raw certificate bytes (DER or PEM encoded)
            val success = dpm.installCaCert(
                null, // Admin component - null when called by a delegated app
                caCertBytes
            )

            // Use require to throw an exception if DPM returns false
            require(success) {
                "DPM installCaCert returned false. This may be due to cert format or permission issues."
            }

            // Return the certificate CN for display
            commonName
        }
    }

    /**
     * Retrieves details of all key pairs that were installed by this app.
     * These are certificates installed via installKeyPair() and tracked in SharedPreferences.
     *
     * Note: AndroidKeyStore doesn't provide a reliable API to list aliases in work profiles,
     * so we track installations ourselves.
     *
     * @return Result containing a list of certificate details (alias, CN, validity, etc.)
     */
    fun getInstalledKeyPairDetails(context: Context): Result<List<String>> {
        return runCatching {
            val certificates = getSavedCertificates(context)

            if (certificates.isEmpty()) {
                return@runCatching listOf("No key pairs found (none installed via this app)")
            }

            certificates.map { cert ->
                val validFromDate = Date(cert.validFrom)
                val validToDate = Date(cert.validTo)
                val installedDate = Date(cert.installedAt)

                buildString {
                    append("Alias: ${cert.alias}\n")
                    append("CN: ${cert.commonName}\n")
                    append("Subject: ${cert.subjectDN}\n")
                    append("Issuer: ${cert.issuerDN}\n")
                    append("Serial: ${cert.serialNumber}\n")
                    append("Valid: $validFromDate to $validToDate\n")
                    append("Installed: $installedDate")
                }
            }
        }
    }

    /**
     * Retrieves information about a specific certificate alias.
     *
     * @param alias The certificate alias to retrieve information for
     * @return Result containing certificate details or null if not found
     */
    fun getCertificateInfo(context: Context, alias: String): Result<CertificateInfo?> {
        return runCatching {
            val certificates = getSavedCertificates(context)
            certificates.find { it.alias == alias }
        }
    }

    /**
     * Removes a key pair (certificate) by alias.
     *
     * @param context The application context
     * @param alias The alias of the certificate to remove
     * @return Result containing success message or error
     */
    fun removeKeyPair(context: Context, alias: String): Result<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        return runCatching {
            require(alias.isNotBlank()) {
                "Certificate alias cannot be empty"
            }

            // Remove the key pair using DPM
            val success = dpm.removeKeyPair(
                null, // Admin component - null when called by a delegated app
                alias
            )

            require(success) {
                "Failed to remove key pair '$alias'. Certificate may not exist or you don't have permission."
            }

            // Remove from our saved certificates list
            removeSavedCertificate(context, alias)

            "Successfully removed certificate '$alias'"
        }
    }

    /**
     * Removes a certificate from the saved certificates list.
     */
    private fun removeSavedCertificate(context: Context, alias: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val certificates = getSavedCertificates(context)

        // Filter out the certificate with the given alias
        val updatedCertificates = certificates.filter { it.alias != alias }

        // Save the updated list
        val certArray = JSONArray()
        updatedCertificates.forEach { certArray.put(it.toJson()) }

        prefs.edit().putString(KEY_INSTALLED_CERTS, certArray.toString()).apply()
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
                    "Error parsing certificate."
                }
            }
        }
    }
}
