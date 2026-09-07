# Detailed findings

## Test environment and limits

- Device: OnePlus 13
- Platform: Qualcomm
- Operating system: Android 16 / OxygenOS 16
- Root: KernelSU Next
- Test date: 2026-09-07

These are observations from one device and firmware build. They should not be generalized to every OnePlus 13 firmware variant.

The proof of concept was deliberately narrow. It used a KernelSU bind mount to present a patched configuration containing one fewer package-disable rule. It did not physically modify `/my_stock`, replace the Mosey APK, modify Wi-Fi, disable SELinux, patch the kernel, or add a service watchdog.

## Observations

### 1. Stock firmware artifacts

The following files were present:

```text
/system_ext/priv-app/MoseyApp/MoseyApp.apk
/system_ext/etc/default-permissions/default-permissions-com.google.android.mosey.xml
/system_ext/etc/permissions/privapp-permissions-com.google.android.mosey.xml
```

Observed APK metadata:

```text
package:     com.google.android.mosey
versionName: 1.0.864840262
versionCode: 13120
minSdk:      36
targetSdk:   36
```

The APK exposes `com.google.android.mosey.ExternalSharingService` and supports the `com.google.android.nearby.SHARING_PROVIDER` action.

Strings and references observed in the APK included `AirDrop/1.0`, `_airdrop._tcp`, `android.net.wifi.aware.WifiAwareManager`, `com.apple.notes.airdrop.document`, and `com.google.android.gms.nearby.sharing.START_SERVICE`. These references show relevant implementation material in the shipped APK; they do not establish that the complete feature works.

### 2. OPlus package-disable rule

`/my_stock/etc/config/app_v2.xml` contained:

```xml
<disable pkg="com.google.android.mosey" priority="10"/>
```

Removing only that rule in the systemlessly bind-mounted copy was sufficient for Android PackageManager to register the stock APK after reboot:

```text
$ pm path com.google.android.mosey
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

`dumpsys package` showed package flags including `SYSTEM`, `HAS_CODE`, `PRIVILEGED`, and `SYSTEM_EXT`.

### 3. Permission state

Granted install permissions included:

```text
android.permission.MANAGE_WIFI_INTERFACES
android.permission.CHANGE_NETWORK_STATE
android.permission.CHANGE_WIFI_MULTICAST_STATE
android.permission.INTERNET
android.permission.GET_PACKAGE_SIZE
android.permission.BLUETOOTH_PRIVILEGED
android.permission.CHANGE_WIFI_STATE
android.permission.ACCESS_NETWORK_STATE
android.permission.LOCAL_MAC_ADDRESS
android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS
android.permission.ACCESS_WIFI_STATE
android.permission.LOCATION_HARDWARE
android.permission.WAKE_LOCK
```

The following requested runtime permissions were manually granted for User 0 and subsequently reported `granted=true`:

```text
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION
android.permission.NEARBY_WIFI_DEVICES
android.permission.BLUETOOTH_SCAN
android.permission.BLUETOOTH_ADVERTISE
android.permission.ACCESS_BACKGROUND_LOCATION
```

`android.permission.CREATE_APP_SPECIFIC_NETWORK` and `android.permission.NETWORK_FACTORY` were in the requested-permission list but not in the granted install-permission list. This is an observation, not an identified cause of the later failure.

### 4. ExternalSharingService behavior

The test conditions were:

- Quick Share visibility set to Everyone;
- Wi-Fi and Bluetooth enabled;
- macOS Finder AirDrop open; and
- macOS AirDrop discovery set to Everyone.

The service-start command was:

```sh
am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER
```

Android started and bound the Mosey process. On repeated tests, it was killed approximately 30 seconds later with:

```text
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException
```

The observed interval in one sanitized example was approximately `18:15:40.254` to `18:16:10.280`. The exception indicates that `ExternalSharingService` did not call `Service.startForeground()` within Android's deadline.

No successful AirDrop discovery occurred in either direction during this test.

### 5. Native-backend search

The following paths returned `No such file or directory`:

```text
/vendor/bin/mosey_server
/vendor/etc/init/mosey.rc
```

`getprop init.svc.mosey_server` returned no value. Binder/service searches for `mosey`, `com.google.pixel.service`, and `wonder` returned nothing relevant.

A filename search across `/vendor`, `/system`, `/system_ext`, `/product`, and `/odm` for `*mosey*` and `*wonder*` found only:

- the Mosey APK directory and APK;
- Mosey OAT artifacts; and
- the default/privileged permission XML files.

No `mosey_server` or `wonder` binary/module was found in those searched stock partitions. This wording is intentional: the search does not prove that no related backend exists anywhere, in an unsearched location, or in another firmware variant.

Other firmware integration references associated with Mosey were present in SELinux and sysconfig data, including `system_ext_seapp_contexts`, `hidden-api-whitelist-ext.xml`, `vendor_service_contexts`, and `precompiled_service_contexts`. The firmware therefore appears to contain more than an isolated APK.

### 6. NAN capability and framework exposure

Driver/vendor-side diagnostics indicated:

- a `wifi-aware0` interface;
- NAN in supported interface modes;
- `wifi.aware.interface=wifi-aware0`; and
- Qualcomm/nl80211 NAN capability indications.

The normal Android framework surface was unavailable:

```text
$ dumpsys wifiaware
Can't find service: wifiaware
```

PackageManager did not expose `android.hardware.wifi.aware`.

Supported conclusion: driver/vendor-side NAN capability appears to exist, while the Android framework Wi-Fi Aware service/feature is not exposed on this firmware. This does not prove AWDL compatibility.

## Hypotheses

The collected evidence supports a working hypothesis that this firmware contains part of Google's Mosey integration but lacks or disables additional backend/framework components required for full operation.

The approximately 30-second foreground-service timeout may be a symptom of one or more unmet dependencies. The current evidence does not establish which dependency is responsible. In particular, it does not prove that the two ungranted install permissions or the missing framework Wi-Fi Aware surface is the root cause.

The investigation does not establish that the OnePlus 13 hardware is incapable of AirDrop interoperability.

## Current status

| Check | Result |
| --- | --- |
| OEM Mosey disable gate bypassed | **SUCCESS** |
| Mosey registered as privileged system app | **SUCCESS** |
| `ExternalSharingService` process starts | **SUCCESS** |
| AirDrop discovery | **NOT WORKING** |
| Native backend found in searched partitions | **NO** |
| Full OnePlus 13 port | **NOT IMPLEMENTED** |
