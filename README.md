# OnePlus 13 Mosey Research

Research notes on Google Mosey / Quick Share ↔ AirDrop support in OnePlus 13 OxygenOS 16.

> [!IMPORTANT]
> This repository documents a limited, device-specific investigation. It does **not** claim that AirDrop was implemented or enabled on the OnePlus 13, and it is not a ready-to-install port.

## Scope

The testing recorded here was performed on a rooted Qualcomm-based OnePlus 13 running Android 16 / OxygenOS 16 with KernelSU Next. The goal was to identify the Mosey components already present in stock firmware, test the effect of the OPlus package-disable rule, and record the next observable failure.

The proof of concept used a systemless bind mount. It did not physically modify `/my_stock`, replace `MoseyApp.apk`, modify Wi-Fi, disable SELinux, or patch the kernel.

## Current status

| Check | Result |
| --- | --- |
| OEM Mosey disable gate bypassed | **SUCCESS** |
| Mosey registered as privileged system app | **SUCCESS** |
| `ExternalSharingService` process starts | **SUCCESS** |
| AirDrop discovery | **NOT WORKING** |
| Native backend found in searched partitions | **NO** |
| Full OnePlus 13 port | **NOT IMPLEMENTED** |

## Verified observations

### Stock Mosey package

The tested stock OxygenOS 16 build contained:

```text
/system_ext/priv-app/MoseyApp/MoseyApp.apk
/system_ext/etc/default-permissions/default-permissions-com.google.android.mosey.xml
/system_ext/etc/permissions/privapp-permissions-com.google.android.mosey.xml
```

Observed package metadata:

| Field | Value |
| --- | --- |
| Package | `com.google.android.mosey` |
| Version name | `1.0.864840262` |
| Version code | `13120` |
| Minimum SDK | `36` |
| Target SDK | `36` |

### OPlus disable gate

`/my_stock/etc/config/app_v2.xml` contains an explicit package-disable rule:

```xml
<disable pkg="com.google.android.mosey" priority="10"/>
```

A small KernelSU proof of concept bind-mounted a patched copy of this configuration with only that rule removed. After reboot:

```text
$ pm path com.google.android.mosey
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

`dumpsys package` then identified Mosey as `SYSTEM`, `PRIVILEGED`, and `SYSTEM_EXT`. Important install permissions were granted, including `MANAGE_WIFI_INTERFACES`, `BLUETOOTH_PRIVILEGED`, `LOCAL_MAC_ADDRESS`, `CONNECTIVITY_USE_RESTRICTED_NETWORKS`, and `LOCATION_HARDWARE`. The requested Nearby, Bluetooth, and location runtime permissions were also successfully granted for User 0.

### Foreground-service failure

The stock service can be invoked with:

```sh
am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER
```

In repeated tests, the process started and bound, but Android terminated it after approximately 30 seconds with `ForegroundServiceDidNotStartInTimeException`, reporting that `ExternalSharingService` had not called `Service.startForeground()` in time. No Mac-to-OnePlus or OnePlus-to-Mac AirDrop discovery was achieved through this path.

### Native backend search

The following expected files were not present in the searched stock partitions:

```text
/vendor/bin/mosey_server
/vendor/etc/init/mosey.rc
```

`getprop init.svc.mosey_server` returned no value. A filename search of `/vendor`, `/system`, `/system_ext`, `/product`, and `/odm` for `*mosey*` and `*wonder*` found only the Mosey Android APK/OAT files and its permission XMLs.

This result is deliberately scoped: the native backend was **not found in the searched stock partitions**. It does not prove that no related backend exists anywhere or in every firmware variant.

### NAN below the Android framework

Qualcomm Wi-Fi diagnostics indicated driver/vendor-side NAN capability, including:

- a `wifi-aware0` interface;
- NAN among the supported interface modes; and
- `wifi.aware.interface=wifi-aware0`.

The Android framework did not expose the normal Wi-Fi Aware service or feature:

```text
$ dumpsys wifiaware
Can't find service: wifiaware
```

PackageManager did not expose `android.hardware.wifi.aware` either. These observations do **not** prove AWDL compatibility.

## Hypotheses (not verified)

A reasonable working hypothesis is that this OnePlus 13 firmware contains part of the Mosey integration but lacks or disables additional native-backend and/or Android-framework components required for full operation. The foreground-service timeout is consistent with an unmet dependency, but the testing so far does not identify a root cause.

Nothing in these results establishes that the OnePlus 13 hardware is incapable of AirDrop interoperability.

## Documentation

- [Detailed findings](docs/findings.md)
- [Sanitized test commands](docs/test-commands.md)

## Related research and acknowledgements

Thanks to the authors and researchers behind these independent projects:

- [`thelok1s/mosey-extended`](https://github.com/thelok1s/mosey-extended) — research into the Mosey native service, SELinux integration, and `wonder` networking path.
- [`thelok1s/mosey-extended` Issue #4](https://github.com/thelok1s/mosey-extended/issues/4) — discussion of a related Qualcomm/OnePlus implementation and discovery work.
- [`FeelLiao/mosey-bada`](https://github.com/FeelLiao/mosey-bada) — a Qualcomm/OnePlus implementation using a native bridge, privileged shim, BLE handling, mDNS, and a `mosey0` networking path.
- [`DanielNappa/ring-around-the-mosey`](https://github.com/DanielNappa/ring-around-the-mosey) — Pixel 9-series staged-rollout/service enablement research.

This repository is independent and is not affiliated with or endorsed by those projects, Google, OPlus/OnePlus, or Apple.

## Privacy and redistribution

Only sanitized technical observations are included. This repository does not contain or redistribute:

- `MoseyApp.apk` or any other Google/OPlus binary;
- raw full logcat output;
- installed-application lists or financial-application names;
- device identifiers, MAC addresses, or serial numbers; or
- account or personal information.

Google, OPlus/OnePlus, Apple, Android, Quick Share, AirDrop, Mosey, and other third-party names and binaries remain the property of their respective owners. References are descriptive only.

## Further testing

I am happy to provide researchers with additional sanitized OnePlus 13 test results or run requested read-only/root diagnostic commands. I am not currently implementing the port myself, but I am happy to test.

## License

Original documentation and any original scripts in this repository are available under the [MIT License](LICENSE). The license does not apply to third-party trademarks or proprietary binaries, none of which are included here.
