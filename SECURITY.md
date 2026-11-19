# Security

Meguri has no servers and no accounts, so any security problem is most likely in how the app stores or shares your data on the phone: backups, shared photos or exported logs.

## Reporting a problem

Please don't open a public issue. Instead, report it privately through GitHub: go to **Security › [Report a vulnerability](https://github.com/thisisartman/Meguri/security/advisories/new)**. Tell us which version you're using, what you found and how to reproduce it, and you'll hear back within a week.

## Which versions get fixes

Only the latest release. Updates install over the old version and keep your data, so staying current is easy.

## Is my APK genuine?

Every release is signed with the same key. If you got the APK from anywhere other than this repository's releases, check that its signing certificate's SHA-256 is:

```
06:7F:32:50:E8:46:AA:0B:E0:B6:A4:8B:89:3B:21:B7:56:8E:1C:5E:9C:FC:17:D9:30:A8:63:F9:40:38:79:13
```

With the Android SDK, run `apksigner verify --print-certs Meguri.apk`.
