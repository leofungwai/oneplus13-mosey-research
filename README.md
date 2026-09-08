# OnePlus 13 Mosey Research

Research notes on Google Mosey / Quick Share ↔ AirDrop support in OnePlus 13 OxygenOS 16.

> [!IMPORTANT]
> This repository documents a limited, device- and build-specific investigation. It does **not** claim that AirDrop was implemented or enabled on the OnePlus 13, and it is not a ready-to-install port.

## Scope

Testing was performed on a rooted Qualcomm-based OnePlus 13 running Android 16 / OxygenOS 16 with KernelSU Next. The tested build was `CPH2653_16.0.10.501(EX01)`. The investigation has two distinct phases:

- **2026-09-07:** stock Mosey package and a narrow systemless configuration proof of concept;
- **2026-09-08:** a user-installed Quick Share Extension update, static component analysis, backend availability checks, GMS binding tests, Wi-Fi Aware diagnostics, and stock policy inspection.

The proof of concept used bind-mounted files. It did not physically modify `/my_stock`, replace the stock APK, replace Wi-Fi firmware or drivers, disable SELinux, or patch the kernel. No proprietary binary or flashable module is included here.

## 2026-09-08 follow-up summary

- The newer Quick Share Extension APK is directly related, but installing it was not sufficient on this OnePlus 13 build.
- The updated APK checks an optional `com.google.android.moseylib` library and two exact native Binder service names; none was available at runtime.
- Its controlled backend check left the legacy provider disabled, explaining why repeatedly starting only the frontend cannot provide the missing backend.
- Stock SELinux policy contains detailed Mosey server/service mappings and network permissions, but the expected executable, init rule, and registered endpoint were not found.
- A manually requested Wi-Fi Aware capability refresh succeeded after the feature declaration was exposed systemlessly. This is useful evidence of a responding NAN HAL path, but it is not proof of AWDL or working AirDrop.
- No AirDrop discovery or GMS-to-Mosey service binding was achieved.

## Current status

| Check | Result |
| --- | --- |
| OEM Mosey disable gate bypassed | **SUCCESS** |
| Mosey registered as a privileged system app | **SUCCESS** |
| User-installed extension update inspected | **SUCCESS** |
| `ExternalSharingService` process starts through a manual FGS command | **SUCCESS** |
| Optional `com.google.android.moseylib` available | **NO** on the tested build |
| Referenced Mosey Binder services available | **NO** on the tested build |
| Updated APK backend availability check | **FAILED** |
| Legacy `ExternalSharingService` after the check | **DISABLED** |
| Lite provider present in the updated manifest | **YES; integration not verified** |
| Wi-Fi Aware capability refresh | **SUCCESS** in one controlled test |
| GMS → Mosey service binding observed | **NO** |
| AirDrop discovery | **NOT WORKING** |
| Native backend executable or registered service found | **NO** in the searched locations/runtime services |
| Mosey-related SELinux policy/configuration present | **YES** |
| Full OnePlus 13 port | **NOT IMPLEMENTED** |

## Verified observations

### Phase 1: stock Mosey package

The tested stock OxygenOS 16 build contained:

```text
/system_ext/priv-app/MoseyApp/MoseyApp.apk
/system_ext/etc/default-permissions/default-permissions-com.google.android.mosey.xml
/system_ext/etc/permissions/privapp-permissions-com.google.android.mosey.xml
```

Observed stock package metadata:

| Field | Value |
| --- | --- |
| Package | `com.google.android.mosey` |
| Version name | `1.0.864840262` |
| Version code | `13120` |
| Minimum SDK | `36` |
| Target SDK | `36` |

### OPlus disable gate and systemless proof of concept

`/my_stock/etc/config/app_v2.xml` contains an explicit package-disable rule:

```xml
<disable pkg="com.google.android.mosey" priority="10"/>
```

A small KernelSU proof of concept bind-mounted a patched copy of this configuration with only that rule removed. After reboot:

```text
$ pm path com.google.android.mosey
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

`dumpsys package` identified Mosey as `SYSTEM`, `PRIVILEGED`, and `SYSTEM_EXT`. Important install permissions became granted, including `MANAGE_WIFI_INTERFACES`, `BLUETOOTH_PRIVILEGED`, `LOCAL_MAC_ADDRESS`, `CONNECTIVITY_USE_RESTRICTED_NETWORKS`, and `LOCATION_HARDWARE`. Requested Nearby, Bluetooth, and location runtime permissions were also successfully granted for User 0 during the stock-package tests.

### Phase 2: user-installed Quick Share Extension update

A user-installed update of the same package was active over the stock system APK during the second phase. PackageManager identified it as an updated privileged system app. It was treated as a sideloaded test update, **not** as evidence of an OEM or Google Play staged rollout.

| Field | Active update |
| --- | --- |
| Package | `com.google.android.mosey` |
| Version name | `1.0.962636193` |
| Version code | `39073` |
| Minimum SDK | `36` |
| Target SDK | `37` |

This metadata matches the public [Quick Share Extension 1.0.962636193 listing](https://www.apkmirror.com/apk/google-inc/quick-share-extension/quick-share-extension-1-0-962636193-release/quick-share-extension-1-0-962636193-android-apk-download/).

Static manifest inspection found the following additions compared with the stock APK:

- an optional `com.google.android.moseylib` shared-library dependency;
- `ServiceInitializationReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`;
- `MoseyPermissionActivity`;
- `ExternalSharingServiceLite`, using action `com.google.android.nearby.LITE_SHARING_PROVIDER` and permission `com.google.location.nearby.permission.ACCESS_NEARBY_SHARE_API`.

The original `ExternalSharingService`, legacy action `com.google.android.nearby.SHARING_PROVIDER`, and GMS permission remained present. The inspected arm64 split contained `liblivephoto_puller_jni.so`; no separately packaged Mosey/AWDL transport library was identified in that split.

### Backend selection and self-disable behavior

Static inspection of the updated APK found code paths that first attempt `com.google.android.moseylib.MoseyManager`. When that optional library is unavailable, the inspected code attempts these declared Binder services:

```text
com.google.android.moseyservice.IMoseyService/default
com.google.pixel.moseyservice.IMoseyService/default
```

On the tested running build:

- `pm list libraries` did not list `com.google.android.moseylib`;
- `service check` reported both exact Binder service names as not found;
- no matching declaration was found in the searched VINTF directories.

In a controlled explicit `MY_PACKAGE_REPLACED` receiver test, the updated APK reported that the optional library was unavailable, neither Binder service could be obtained, and the Mosey service was unavailable. `ExternalSharingService` remained disabled afterward. This demonstrates that this APK build can disable the legacy component based on its backend availability check; it does not establish when the component was first disabled.

### Policy scaffold without a running backend

The stock SELinux configuration contains specific Mosey references:

```text
/(vendor|odm)/bin/mosey_server -> mosey_server_exec
com.google.android.moseyservice.IMoseyService/default -> mosey_service
com.google.pixel.moseyservice.IMoseyService/default -> mosey_service
```

The compiled vendor policy also defines `mosey_server`, `mosey_server_exec`, and `mosey_service` types, a transition from `init` to the server domain, Binder/service-manager access, and narrowly relevant network capabilities such as `NET_ADMIN`, `NET_RAW`, packet sockets, routing/generic netlink, and TUN access.

However, on this tested build:

```text
/vendor/bin/mosey_server   not present
/odm/bin/mosey_server      not present
```

No Mosey init rule was found in the searched init directories, and neither referenced Binder service was registered. Searches of the mounted stock and OPlus `my_*` partitions found no runnable Mosey backend endpoint. This is evidence of retained policy/configuration scaffolding, not evidence that the backend binary is present.

### Service lifecycle and the 30-second FGS timeout

The legacy service can be invoked manually with:

```sh
am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER
```

In controlled tests, the command created the Mosey process, but `startForegroundCount` remained zero and Android terminated the service after approximately 30 seconds with `ForegroundServiceDidNotStartInTimeException`.

A DEX symbol search found no direct `startForeground` reference in the active APK. Static inspection of the active GMS provider registry instead found a discovery-and-`bindService()` path for enabled/exported services implementing the legacy provider action. Therefore the manual foreground-service command is not equivalent to the normal GMS binding path. The symbol search does not rule out reflective or external shared-library behavior.

No AirDrop discovery was achieved through either the stock or updated package tests.

### GMS provider observations

The inspected active GMS base APK contained references to the legacy `com.google.android.nearby.SHARING_PROVIDER` action and code that queries and binds eligible external providers. A raw-string search of that base package did not find the exact Lite action or its newer permission.

Temporarily enabling the legacy component made it resolvable through PackageManager. Nevertheless, no active Mosey service binding was observed while opening the tested Quick Share settings or receive UI. The component was restored to its backend-gated disabled state after each test. These observations do not exclude every dynamic-module, account, rollout, or future-client path.

### Wi-Fi Aware / NAN findings

Before the systemless feature-declaration test, stock framework checks did not expose `android.hardware.wifi.aware`, and `dumpsys wifiaware` could not find the service. Qualcomm/vendor diagnostics nevertheless contained NAN indications such as `wifi-aware0`, a NAN-capable interface mode, and `wifi.aware.interface=wifi-aware0`.

The later KernelSU proof of concept systemlessly exposed the Wi-Fi Aware feature declaration; it did not replace or patch the Wi-Fi driver or firmware. In the resulting test state:

- the framework service existed and reported `mUsageEnabled: true`;
- the initial snapshot had null capabilities, no active NAN interface, and a recorded `GET_CAPABILITIES` response timeout;
- `cmd wifiaware state_mgr update_capabilities` subsequently completed with `RESPONSE_TYPE_ON_CAPABILITIES_UPDATED`;
- the returned structure reported one concurrent cluster, eight publish sessions, eight subscribe sessions, one NDI, and eight NDP sessions;
- the available-resource query reported eight NDP, eight publish, and eight subscribe slots;
- no active Wi-Fi Aware clients, publish/subscribe sessions, NDPs, or persistent NAN interface were observed after opening Quick Share.

This proves only that one explicit framework-to-HAL capability query completed in that test state. It does **not** prove that NAN discovery/data paths work, that Quick Share used them, or that the device supports AWDL.

### WLAN driver search

No standalone `wonder` kernel module was found loaded or stored in the searched module locations. The running Qualcomm WLAN module was `qca_cld3_peach_v2`; a raw string/symbol search of that module did not find `wonder`, `wondertap`, `mosey`, or `awdl`. A negative string search does not prove that equivalent hardware or driver functionality is impossible.

## Hypotheses (not verified)

- The newer extension appears necessary for newer integration paths but was not sufficient on this OnePlus 13 build.
- The absence of all three backend routes referenced by the active APK is a strong candidate explanation for its availability check failing, but it is not proven to be the only blocker.
- The retained SELinux policy may come from a shared OPlus/Google integration baseline while this product build omits the corresponding binary, init configuration, library, or enablement. The reason is unknown.
- A functional port would probably require additional system/vendor integration and compatible native or driver components; bypassing the XML disable rule alone is insufficient.

Nothing in these results establishes that OnePlus 13 hardware is incapable of AirDrop interoperability. Conversely, Wi-Fi Aware capability reporting is not proof of AWDL compatibility.

## Documentation

- [Detailed findings](docs/findings.md)
- [Sanitized test commands](docs/test-commands.md)

## Related research and acknowledgements

Thanks to the authors and researchers behind these independent projects:

- [`thelok1s/mosey-extended`](https://github.com/thelok1s/mosey-extended) — research into the Mosey native service, SELinux integration, and `wonder` networking path.
- [`thelok1s/mosey-extended` Issue #4](https://github.com/thelok1s/mosey-extended/issues/4) — discussion of a related Qualcomm/OnePlus implementation and discovery work.
- [`FeelLiao/mosey-bada`](https://github.com/FeelLiao/mosey-bada) — experimental Qualcomm/OnePlus bridge, shim, BLE, mDNS, and `mosey0` work; its documentation states that proprietary backend files are not bundled.
- [`DanielNappa/ring-around-the-mosey`](https://github.com/DanielNappa/ring-around-the-mosey) — Pixel 9-series staged-rollout/service research. Its Pixel method assumes device-side native support and did not solve the missing backend routes observed here.

How those projects relate to this follow-up:

| Project | Author-reported focus | Relevance to this OnePlus 13 result |
| --- | --- | --- |
| `mosey-extended` | Experimental native service, SELinux, and `wonder`-path transplantation | Supports investigating the native transport layer rather than treating the APK as the whole feature |
| Issue #4 / `mosey-bada` | Qualcomm/OnePlus 15 discovery, bridge, BLE, mDNS, and `mosey0` experiments | Provides a comparison target; its OnePlus 15 discovery result was not reproduced on this OnePlus 13 |
| `mosey-bada` | Requires externally obtained `mosey_server` and related proprietary library files, which it does not bundle | Consistent with this build retaining policy references while lacking a runnable endpoint |
| `ring-around-the-mosey` | Keeps the Pixel 9 Mosey frontend service alive during a staged rollout | Useful on a device that already has its native support; repeatedly starting the frontend cannot supply the absent backend routes observed here |

The “author-reported focus” column summarizes those projects' own documentation. This repository does not independently validate all of their implementation or compatibility claims.

At the time of the second test phase, Google's public [Quick Share with iPhone compatibility page](https://www.android.com/quick-share/with-iphone/) listed the OnePlus 15, not the OnePlus 13. Compatibility lists and rollouts can change.

This repository is independent and is not affiliated with or endorsed by those projects, Google, OPlus/OnePlus, Qualcomm, or Apple.

## Privacy and redistribution

Only sanitized technical observations are included. This repository does not contain or redistribute:

- `MoseyApp.apk`, GMS APKs/splits, DEX/OAT/VDEX files, native libraries, firmware images, or other proprietary binaries;
- the KernelSU module used during testing;
- raw full logcat, dumpsys, dmesg, KernelSU, Wi-Fi, or macOS logs;
- installed-application lists or financial-application names;
- ADB serials, randomized `/data/app` paths, device IDs, MAC/IP addresses, SSIDs, account information, or personal filesystem paths.

Google, OPlus/OnePlus, Qualcomm, Apple, Android, Quick Share, AirDrop, Mosey, and other third-party names and binaries remain the property of their respective owners. References are descriptive only.

## Further testing

I am happy to provide researchers with additional sanitized OnePlus 13 test results or run requested read-only/root diagnostic commands. I am not currently implementing the full port myself, but I am happy to test.

## License

Original documentation and any original scripts in this repository are available under the [MIT License](LICENSE). The license does not apply to third-party trademarks or proprietary binaries, none of which are included here.
