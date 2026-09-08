# Detailed findings

## Test environment and limits

- Device: OnePlus 13
- Platform: Qualcomm
- Operating system: Android 16 / OxygenOS 16
- Tested build: `CPH2653_16.0.10.501(EX01)`
- Root: KernelSU Next
- Test dates: 2026-09-07 and 2026-09-08

These are observations from one device and firmware build. They should not be generalized to every OnePlus 13, OPlus product, region, or firmware variant.

The tests did not modify the physical contents of `/my_stock`, replace stock Google/OPlus binaries, patch the kernel, replace Wi-Fi firmware, or disable SELinux. Temporary per-user component changes were restored after each controlled test. Static analysis used temporary local copies that were deleted afterward and are not included in this repository.

## Phase 1 — stock package (2026-09-07)

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

The stock APK exposes `com.google.android.mosey.ExternalSharingService` for the `com.google.android.nearby.SHARING_PROVIDER` action. Relevant strings/references observed in the APK included `AirDrop/1.0`, `_airdrop._tcp`, `android.net.wifi.aware.WifiAwareManager`, and `com.apple.notes.airdrop.document`. Their presence shows related implementation material in the APK; it does not establish that the complete feature works.

### 2. OPlus package-disable rule

`/my_stock/etc/config/app_v2.xml` contained:

```xml
<disable pkg="com.google.android.mosey" priority="10"/>
```

Removing only that rule from a systemlessly bind-mounted copy was sufficient for PackageManager to register the stock APK after reboot:

```text
$ pm path com.google.android.mosey
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

`dumpsys package` showed flags including `SYSTEM`, `HAS_CODE`, `PRIVILEGED`, and `SYSTEM_EXT`.

### 3. Stock permission state

Granted install permissions included:

```text
android.permission.MANAGE_WIFI_INTERFACES
android.permission.CHANGE_NETWORK_STATE
android.permission.CHANGE_WIFI_MULTICAST_STATE
android.permission.INTERNET
android.permission.BLUETOOTH_PRIVILEGED
android.permission.CHANGE_WIFI_STATE
android.permission.ACCESS_NETWORK_STATE
android.permission.LOCAL_MAC_ADDRESS
android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS
android.permission.ACCESS_WIFI_STATE
android.permission.LOCATION_HARDWARE
android.permission.WAKE_LOCK
```

The following requested runtime permissions were manually granted for User 0 and subsequently reported `granted=true` during the stock-package test:

```text
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION
android.permission.NEARBY_WIFI_DEVICES
android.permission.BLUETOOTH_SCAN
android.permission.BLUETOOTH_ADVERTISE
android.permission.ACCESS_BACKGROUND_LOCATION
```

This records a test state, not a recommendation to grant every permission permanently.

### 4. Stock foreground-service experiment

With Quick Share visibility set to Everyone and an Apple device placed in its corresponding discoverable mode, the following command created the Mosey process:

```sh
am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER
```

Android terminated it after approximately 30 seconds with `ForegroundServiceDidNotStartInTimeException`. No AirDrop discovery occurred in either direction.

This was initially useful as a process-lifecycle experiment. Phase 2 static analysis later showed that GMS has a provider-binding path, so this manual FGS invocation should not be treated as equivalent to the normal integration path.

## Phase 2 — user-installed extension update (2026-09-08)

### 5. Active update identity and provenance

A user-installed update of `com.google.android.mosey` was active over the stock system package. PackageManager reported it as `SYSTEM`, `UPDATED_SYSTEM_APP`, `PRIVILEGED`, and `SYSTEM_EXT`. APKMirror Installer was the observed installer, so the update was not treated as evidence of an OEM or Google Play staged rollout.

```text
package:     com.google.android.mosey
versionName: 1.0.962636193
versionCode: 39073
minSdk:      36
targetSdk:   37
```

The metadata matches the public [APKMirror listing](https://www.apkmirror.com/apk/google-inc/quick-share-extension/quick-share-extension-1-0-962636193-release/quick-share-extension-1-0-962636193-android-apk-download/). No APK or split is redistributed here.

### 6. Manifest and packaged-content differences

Static manifest inspection found these additions in the active update:

- optional library `com.google.android.moseylib` (`required=false`);
- `ServiceInitializationReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`;
- exported `MoseyPermissionActivity` for the permission-request flow;
- exported `ExternalSharingServiceLite` for `com.google.android.nearby.LITE_SHARING_PROVIDER`, protected by `com.google.location.nearby.permission.ACCESS_NEARBY_SHARE_API`.

The original `ExternalSharingService` remained exported for `com.google.android.nearby.SHARING_PROVIDER`, protected by `com.google.android.gms.permission.ACCESS_NEARBY_SHARE_API`.

The inspected update contained three DEX files. Its arm64 split contained `liblivephoto_puller_jni.so`; no separately packaged Mosey/AWDL transport library was identified in that split. This does not exclude every external/shared-library path.

### 7. Backend-selection paths in the updated APK

Static inspection found code paths that attempt the following backends:

1. instantiate `com.google.android.moseylib.MoseyManager` when the optional shared library is available;
2. otherwise obtain `com.google.android.moseyservice.IMoseyService/default` from ServiceManager;
3. or obtain `com.google.pixel.moseyservice.IMoseyService/default` from ServiceManager.

The library-backed path references operations corresponding to hardware-capability query, version, start, stop, and update. This is a high-level summary of observed code paths, not redistributed decompiled source.

### 8. Runtime backend availability

On the tested running build:

```text
com.google.android.moseylib                                      not listed
com.google.android.moseyservice.IMoseyService/default            not found
com.google.pixel.moseyservice.IMoseyService/default              not found
```

No matching declaration was found in the searched VINTF directories under `/vendor`, `/odm`, `/system`, `/system_ext`, or `/product`. No matching registered service appeared in the service list.

The updated APK's initialization receiver performs a backend availability check. During a controlled explicit `MY_PACKAGE_REPLACED` test, its sanitized result was:

```text
optional Mosey library unavailable
neither referenced Binder service obtainable
Mosey service unavailable; legacy component disabled
```

`ExternalSharingService` remained in the User 0 disabled-components set afterward. This proves that the receiver can enforce a disabled state when its availability test fails; it does not establish what first disabled the component before the controlled test.

### 9. Foreground-service lifecycle in the active update

A repeated controlled FGS invocation created the process, but `startForegroundCount` remained zero. Android again terminated it after roughly 30 seconds with `ForegroundServiceDidNotStartInTimeException`.

A DEX symbol search across the active APK found no direct `startForeground` reference. The inspected service hierarchy provided binding/lifecycle behavior but no observed direct foreground promotion. This explains the controlled command result without labeling it an APK bug; reflection or external shared-library behavior was not exhaustively excluded.

### 10. GMS provider discovery and binding

The active GMS build reported version `26.32.34`. Static inspection of its base DEX files found:

- the legacy `com.google.android.nearby.SHARING_PROVIDER` action;
- provider discovery through `queryIntentServices`;
- filtering for enabled/exported providers plus provider validation;
- an explicit `bindService` path for eligible provider components.

A raw-string search of the inspected GMS base package did not find the exact Lite action or its newer permission. This is scoped to that package and search method and does not exclude every dynamically delivered or future client path.

Runtime tests then temporarily enabled the legacy component. PackageManager could resolve it, but no active Mosey service binding was observed while opening the tested Quick Share settings or receive UI. Wi-Fi Aware also showed no active client/session attributable to that UI test. The component was restored through its initialization receiver after each experiment.

The separate Lite service resolved for its action, but a normal functional client integration was not verified. A direct shell `startService` failure would not prove that a protected or bind-oriented provider is unusable, so it is not used as a conclusion.

### 11. Stock policy references and missing executable

The stock SELinux context files contain exact mappings for the same backend names used by the updated APK:

```text
/(vendor|odm)/bin/mosey_server
  -> u:object_r:mosey_server_exec:s0

com.google.android.moseyservice.IMoseyService/default
  -> u:object_r:mosey_service:s0

com.google.pixel.moseyservice.IMoseyService/default
  -> u:object_r:mosey_service:s0
```

The compiled vendor policy defines `mosey_server`, `mosey_server_exec`, and `mosey_service`. Observed policy rules include:

- an `init` → `mosey_server` domain transition through `mosey_server_exec`;
- permission for the server to add/find the Mosey service and for the Mosey app to find/call it;
- `NET_ADMIN` and `NET_RAW` capabilities;
- access to network sysfs, packet sockets, routing/generic netlink, and TUN resources.

Despite that configuration scaffold:

```text
/vendor/bin/mosey_server   not present
/odm/bin/mosey_server      not present
```

No Mosey init rule was found in the searched init directories. A filename search over `/vendor`, `/odm`, `/system`, `/system_ext`, `/product`, and mounted APEX content found only the Android package/OAT artifacts and permission XMLs. Searches of the mounted OPlus `my_*` partitions found no additional Mosey/Wonder file or exact backend interface/library string.

Supported conclusion: this tested build retains specific Mosey policy/configuration references, but no runnable backend endpoint was found in the searched locations or runtime services. It does not prove that no backend exists in an unsearched image, inactive slot, different regional build, or other firmware release.

### 12. Wi-Fi Aware / NAN timeline

#### Before the systemless feature declaration

Stock framework checks did not expose `android.hardware.wifi.aware`, and:

```text
$ dumpsys wifiaware
Can't find service: wifiaware
```

Qualcomm/vendor diagnostics nevertheless indicated `wifi-aware0`, a NAN-supported interface mode, and `wifi.aware.interface=wifi-aware0`. Those observations did not prove Android framework integration or AWDL compatibility.

#### After the systemless feature declaration

The later proof of concept exposed the Wi-Fi Aware feature XML systemlessly without replacing the driver or firmware. The framework service then existed and initially showed:

```text
mUsageEnabled: true
mCapabilities: null
mWifiNanIface: null
mInterfaces: empty
history: GET_CAPABILITIES response timeout
```

The build's shell command places the refresh under the `state_mgr` namespace:

```sh
cmd wifiaware state_mgr update_capabilities
```

After one controlled refresh, history recorded `RESPONSE_TYPE_ON_CAPABILITIES_UPDATED` and the framework reported:

```text
maxConcurrentAwareClusters: 1
maxPublishes:                8
maxSubscribes:               8
maxNdiInterfaces:            1
maxNdpSessions:              8
available NDPs:              8
available publish sessions:  8
available subscribe sessions: 8
```

The framework released the temporary Aware interface after the query; no active clients, discovery sessions, or NDPs remained. This establishes only that one explicit capability query completed in that state. It does not establish operational NAN discovery/data paths, Quick Share use of Aware, or AWDL support.

### 13. WLAN module search

No standalone `wonder` module was found in the loaded-module list or searched module directories. The running Qualcomm WLAN module was `qca_cld3_peach_v2`. A raw string/symbol search of that module found no `wonder`, `wondertap`, `mosey`, or `awdl` match.

This is a narrowly scoped negative string search. It must not be interpreted as proof that the hardware or driver cannot provide equivalent functionality.

## Observations versus hypotheses

### Supported observations

- Both stock and updated Mosey Android packages can be registered as privileged system-app code after bypassing the OPlus XML disable gate.
- The updated package references three backend routes, and all three were unavailable on the tested running build.
- Its controlled backend availability check left the legacy provider disabled.
- Specific SELinux policy/configuration for a native Mosey server is present, while the expected executable, init rule, and registered service were not found.
- One explicit Wi-Fi Aware capability query completed after the framework feature was exposed systemlessly.
- No AirDrop discovery was achieved.

### Working hypotheses

- The user-installed update is relevant to newer integration paths but is insufficient by itself.
- The failed backend availability check is a strong candidate explanation for the disabled legacy provider, but may not be the only blocker.
- The policy references may come from shared firmware integration work while this product build omits or disables other required components.
- A functional port likely requires additional compatible native, framework, GMS, and/or WLAN integration.

### Not established

- that no backend exists anywhere or in every OnePlus firmware;
- that Wi-Fi Aware is equivalent to AWDL;
- that the OnePlus 13 hardware can or cannot support AirDrop interoperability;
- that the absence of raw `wonder` strings proves missing driver functionality;
- that the Pixel watchdog method can compensate for a missing OnePlus backend;
- that a full OnePlus 13 port has been implemented.

## Relationship to parallel community research

This follow-up was compared with several independent projects:

- [`thelok1s/mosey-extended`](https://github.com/thelok1s/mosey-extended) investigates transplantation of native Mosey, SELinux, and `wonder`-path components.
- [`mosey-extended` Issue #4](https://github.com/thelok1s/mosey-extended/issues/4) links related OnePlus 15/Qualcomm work and reports partial discovery progress.
- [`FeelLiao/mosey-bada`](https://github.com/FeelLiao/mosey-bada) documents an experimental OnePlus 15 bridge/shim approach and states that required proprietary `mosey_server`/library files are not bundled.
- [`DanielNappa/ring-around-the-mosey`](https://github.com/DanielNappa/ring-around-the-mosey) targets a Pixel 9 staged-rollout/service-lifecycle condition on devices that already have native support.

Those descriptions summarize the projects' own documentation. Their device results were not reproduced or adopted as observations about this OnePlus 13. The comparison instead helped distinguish a frontend/service-lifecycle workaround from the missing backend routes observed on the tested build.

## Current status

| Check | Result |
| --- | --- |
| OEM Mosey disable gate bypassed | **SUCCESS** |
| Mosey registered as privileged system app | **SUCCESS** |
| Updated extension inspected | **SUCCESS** |
| Updated APK backend availability check | **FAILED** |
| Optional library / referenced Binder services | **UNAVAILABLE** on the tested build |
| SELinux backend policy references | **PRESENT** |
| Native backend executable / registered endpoint | **NOT FOUND** in the searched scope |
| Explicit Wi-Fi Aware capability refresh | **SUCCESS** once in the tested state |
| GMS → Mosey bind observed | **NO** |
| AirDrop discovery | **NOT WORKING** |
| Full OnePlus 13 port | **NOT IMPLEMENTED** |
