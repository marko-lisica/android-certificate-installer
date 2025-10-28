## Research: Install certificates on Android

I made this app to learn how certificate installation works on Android (specifically in Work profile).

Summary:
1. App has UI that has button to trigger certificate install (certificate is in the repo in `/res/raw` directory).
2. Android app needs [DELEGATION_CERT_INSTALL](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#DELEGATION_CERT_INSTALL) expect to get to have access to [DevicePolicyManager.installKeyPair](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#installKeyPair(android.content.ComponentName,%20java.security.PrivateKey,%20java.security.cert.Certificate[],%20java.lang.String,%20boolean))
3. `DELEGATION_CERT_INSTALL` is assigned to the app via [Android Policy](https://developers.google.com/android/management/reference/rest/v1/enterprises.policies)
  a. Each item in `applications` has `delegatedScopes` field that has `CERT_INSTALL` option.
  b. Once the EMM assigns DELEGATION_CERT_INSTALL to the app, application can listen for the broadcast intent `ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED`. Upon receiving this, app can use the `installKeyPair` API to install the certificate. If you attempt the installation before receiving this broadcast, the operation will fail.