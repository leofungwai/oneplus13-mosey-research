# Sanitized test commands

These commands document the checks used during the OnePlus 13 investigation. They are not an installer or a complete port.

> [!CAUTION]
> Review commands before running them. Several require root. The foreground-service command changes transient process state and may produce an Android crash dialog or timeout; the other examples are diagnostic. Do not publish unfiltered command output because system dumps and logs can contain private or unrelated data.

## Check package registration

```sh
pm path com.google.android.mosey
dumpsys package com.google.android.mosey
```

Relevant observed path after the systemless configuration test:

```text
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

When sharing `dumpsys package` output, retain only the Mosey package section and review it for identifiers or unrelated package data.

## Check stock artifacts

```sh
ls -l /system_ext/priv-app/MoseyApp/MoseyApp.apk
ls -l /system_ext/etc/default-permissions/default-permissions-com.google.android.mosey.xml
ls -l /system_ext/etc/permissions/privapp-permissions-com.google.android.mosey.xml
```

## Inspect the OPlus disable rule

```sh
su -c 'grep -n "com.google.android.mosey" /my_stock/etc/config/app_v2.xml'
```

The proof of concept used a bind-mounted patched copy with only the Mosey disable line removed. This repository intentionally does not provide a flashable module or alter instructions.

## Start the stock service

```sh
su -c 'am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER'
```

In the recorded test, Android started and bound the process, then terminated it after approximately 30 seconds with `ForegroundServiceDidNotStartInTimeException` because the service did not call `Service.startForeground()` in time.

## Check for the native service

```sh
su -c 'ls -l /vendor/bin/mosey_server /vendor/etc/init/mosey.rc'
getprop init.svc.mosey_server
service list | grep -Ei 'mosey|com\.google\.pixel\.service|wonder'
```

## Search selected stock partitions

```sh
su -c "find /vendor /system /system_ext /product /odm \
  \( -iname '*mosey*' -o -iname '*wonder*' \)"
```

This search is intentionally limited to the listed partitions. A negative result must be reported as "not found in the searched stock partitions," not as proof that no backend exists anywhere.

## Check Wi-Fi Aware / NAN exposure

```sh
ip link show wifi-aware0
getprop wifi.aware.interface
dumpsys wifiaware
pm list features | grep -F android.hardware.wifi.aware
```

Driver/vendor-side NAN indications do not, by themselves, prove AWDL compatibility.

## Sanitization checklist

Before sharing any output:

1. Keep only lines directly relevant to Mosey, `ExternalSharingService`, NAN/Wi-Fi Aware, or the searched paths.
2. Remove device identifiers, serial numbers, MAC addresses, IP addresses where unnecessary, account data, and personal paths.
3. Remove installed-application lists and all unrelated package names, especially financial-application names.
4. Do not upload full raw logcat. Quote only the minimal relevant event or exception.
5. Re-read the final excerpt as if it were public and permanent.
