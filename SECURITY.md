# Security

Meguri has no server and no account, so most security problems would be in how the app stores or shares your data on the phone (backups, shared photos, exported logs).

## Reporting a problem

Please don't open a public issue. Report it privately through GitHub: **Security › [Report a vulnerability](https://github.com/thisisartman/Meguri/security/advisories/new)**. Say which version, what you found, and how to reproduce it. You will get a reply within a week.

## Supported versions

Only the latest release gets fixes. Updates install over the top and keep your data.

## Checking a download

Every release is signed with the same key. Before installing an APK from anywhere other than this repository's releases, check its signing certificate's SHA-256:

```
06:7F:32:50:E8:46:AA:0B:E0:B6:A4:8B:89:3B:21:B7:56:8E:1C:5E:9C:FC:17:D9:30:A8:63:F9:40:38:79:13
```

With the Android SDK: `apksigner verify --print-certs Meguri.apk`.
