## Android Certificate Installer

A sample Android app demonstrating certificate installation and management in managed (Work Profile) environments using delegated scopes assigned to the app by MDM (EMM) server through Android Management API.

## Features

1. **Certificate Installation** - Downloads and installs certificates from remote server using `DevicePolicyManager.installKeyPair()`
   - Client certificates (PKCS#12) with private keys
   - CA certificates (PEM/X.509) for trusting internal servers

2. **Certificate Removal** - Remove installed certificates by alias using `DevicePolicyManager.removeKeyPair()`

3. **Certificate Tracking** - View all certificates installed by this app with detailed information (alias, CN, validity, etc.)

4. **Managed Configuration** - Configure certificate download URLs and credentials via MDM (AMAPI).

## Required delegation

The app requires the `DELEGATION_CERT_INSTALL` delegation to be granted by MDM.

### `DELEGATION_CERT_INSTALL` ([Android docs](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#DELEGATION_CERT_INSTALL))
Allows the app to manage certificates using:
- `DevicePolicyManager.installKeyPair()` - Install client certificates
- `DevicePolicyManager.installCaCert()` - Install CA certificates
- `DevicePolicyManager.removeKeyPair()` - Remove installed certificates
- `DevicePolicyManager.getInstalledCaCerts()` - List installed CA certificates

## How It Works

### Certificate installation
1. App reads managed configuration for certificate URLs and password
2. Downloads certificate from configured remote server (HTTPS)
3. Parses PKCS#12 format with configured password
4. Calls `DevicePolicyManager.installKeyPair()` or `installCaCert()`
5. Certificate installed with unique alias (e.g., `cert1`, `cert2`, `cert3`)
6. Certificate metadata saved to SharedPreferences for tracking

### Certificate removal
1. User enters certificate alias in the UI
2. App calls `DevicePolicyManager.removeKeyPair(alias)`
3. Certificate removed from system keystore
4. Certificate metadata removed from SharedPreferences
5. Certificate list refreshed

## Managed configuration (known as app restrictions in Android development)

The app **requires** managed configuration to function. All certificate download settings must be configured via EMM/MDM - there are no default values.

### Required configuration fields

The app will not install certificates until all three fields are configured:

1. **`cert_download_url`** (string, REQUIRED)
   - URL to download the PKCS#12 certificate file
   - Example: `https://example.com/certificates/client.p12`

2. **`ca_cert_download_url`** (string, REQUIRED)
   - URL to download the CA certificate file
   - Example: `https://example.com/certificates/ca.pem`

3. **`p12_password`** (string, REQUIRED)
   - Password for the PKCS#12 certificate file
   - This value will be masked in the UI (shown as `********`)

### Configuration status

The app displays configuration status in the UI:
- ✓ Shows green checkmark when all fields are configured
- ❌ Shows red "NOT SET" for missing fields
- Install buttons are disabled until all configuration is present


### Configuration schema example

```json
{
  "cert_download_url": "https://example.com/api/certificates/client.p12",
  "ca_cert_download_url": "https://example.com/api/certificates/ca.pem",
  "p12_password": "SecurePassword123"
}
```

## Setup via Android Management API

To configure via Google's Android Management API:

```json
{
  "applications": [
    {
      "packageName": "com.lisica.certinstaller",
      "delegatedScopes": ["CERT_INSTALL"],
      "managedConfiguration": {
        "cert_download_url": "https://example.com/api/certificates/client.p12",
        "ca_cert_download_url": "https://example.com/api/certificates/ca.pem",
        "p12_password": "SecurePassword123"
      }
    }
  ]
}
```

## References

- [DevicePolicyManager.installKeyPair](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#installKeyPair(android.content.ComponentName,%20java.security.PrivateKey,%20java.security.cert.Certificate[],%20java.lang.String,%20int))
- [DevicePolicyManager.installCaCert](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#installCaCert(android.content.ComponentName,%20byte[]))
- [DevicePolicyManager.removeKeyPair](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#removeKeyPair(android.content.ComponentName,%20java.lang.String))
- [DELEGATION_CERT_INSTALL](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#DELEGATION_CERT_INSTALL)
- [Android Management API](https://developers.google.com/android/management/reference/rest/v1/enterprises.policies)
- [Managed Configurations](https://developer.android.com/work/managed-configurations)
