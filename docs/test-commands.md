# Sanitized test commands

These commands document selected checks used during the OnePlus 13 investigation. They are not an installer or a complete port, and some commands are specific to the tested OxygenOS build.

> [!CAUTION]
> Review every command before running it. Several require root, and the component/foreground-service experiments change transient runtime state. Do not publish unfiltered output: package, Wi-Fi, activity, kernel, and log dumps can contain private or unrelated information.

## Check package identity and registration

```sh
pm path com.google.android.mosey
dumpsys package com.google.android.mosey
```

The stock path after the systemless configuration test was:

```text
package:/system_ext/priv-app/MoseyApp/MoseyApp.apk
```

An updated system app may additionally have a randomized path under `/data/app`. Never publish that path. When sharing `dumpsys package`, retain only the minimum relevant Mosey fields and remove timestamps, installer internals, unrelated users, and identifiers.

## Check stock artifacts

```sh
ls -l /system_ext/priv-app/MoseyApp/MoseyApp.apk
ls -l /system_ext/etc/default-permissions/default-permissions-com.google.android.mosey.xml
ls -l /system_ext/etc/permissions/privapp-permissions-com.google.android.mosey.xml
```

Do not copy or upload the APK, OAT/VDEX files, splits, or extracted native libraries.

## Inspect the OPlus disable rule

```sh
su -c 'grep -n "com.google.android.mosey" /my_stock/etc/config/app_v2.xml'
```

The proof of concept used a bind-mounted patched copy with only the Mosey disable line removed. This repository intentionally does not provide the flashable module or the complete patched OPlus XML.

## Inspect provider components

```sh
cmd package query-services --brief \
  -a com.google.android.nearby.SHARING_PROVIDER

cmd package query-services --brief \
  -a com.google.android.nearby.LITE_SHARING_PROVIDER

dumpsys package com.google.android.mosey \
  | grep -A 4 -B 2 'disabledComponents:'
```

The legacy provider may not resolve while it is disabled. The Lite provider resolving for its action does not establish that a compatible client can bind or use it.

## Check the three backend routes referenced by the updated APK

```sh
pm list libraries | grep -F com.google.android.moseylib

service check \
  com.google.android.moseyservice.IMoseyService/default

service check \
  com.google.pixel.moseyservice.IMoseyService/default
```

The tested build listed no optional library and reported both services as `not found`.

To search only the relevant VINTF directories:

```sh
su -c "grep -R -n -E \
  'com\.google\.(android|pixel)\.moseyservice\.IMoseyService' \
  /vendor/etc/vintf /odm/etc/vintf /system/etc/vintf \
  /system_ext/etc/vintf /product/etc/vintf 2>/dev/null"
```

A negative result must be scoped to these directories and the tested build.

## Check expected executable and init configuration

```sh
su -c 'ls -lZ /vendor/bin/mosey_server /odm/bin/mosey_server'

su -c "grep -R -n -F 'mosey_server' \
  /vendor/etc/init /odm/etc/init /system/etc/init \
  /system_ext/etc/init /product/etc/init 2>/dev/null"

getprop init.svc.mosey_server
```

## Inspect the retained SELinux mappings

```sh
su -c "grep -n -E 'mosey|Mosey' \
  /vendor/etc/selinux/vendor_file_contexts \
  /vendor/etc/selinux/vendor_service_contexts"

su -c "grep -n -E \
  '^\((type|allow|allowx|typetransition|neverallow).*mosey' \
  /vendor/etc/selinux/vendor_sepolicy.cil"
```

Keep only short matching policy expressions. Do not publish the full compiled policy output.

## Historical manual foreground-service experiment

```sh
su -c 'am start-foreground-service \
  -n com.google.android.mosey/.ExternalSharingService \
  -a com.google.android.nearby.SHARING_PROVIDER'
```

In the recorded tests, the process started but never promoted itself to a foreground service, and Android terminated it after approximately 30 seconds with `ForegroundServiceDidNotStartInTimeException`.

This command is **not** equivalent to the GMS provider's normal binding path and should not be treated as an activation method. It may produce a crash dialog or timeout.

## Controlled legacy-component resolution test

This test changes User 0 component state. Record the original state first and do not leave the component forced on.

```sh
dumpsys package com.google.android.mosey \
  | grep -A 4 -B 2 'disabledComponents:'

su -c 'pm enable --user 0 \
  com.google.android.mosey/.ExternalSharingService'

cmd package query-services --brief \
  -a com.google.android.nearby.SHARING_PROVIDER

dumpsys activity services com.google.android.mosey
```

On the tested build, the APK's own initialization receiver restored the backend-gated state:

```sh
su -c 'am broadcast \
  -a android.intent.action.MY_PACKAGE_REPLACED \
  -n com.google.android.mosey/.ServiceInitializationReceiver'
```

Always verify the final component state. Do not assume this receiver will disable the component on a different build where a backend is available.

## Check Wi-Fi Aware feature and service exposure

```sh
pm list features | grep -F android.hardware.wifi.aware
service check wifiaware
getprop wifi.aware.interface

dumpsys wifiaware \
  | grep -E 'mUsageEnabled|mCapabilities|mWifiNanIface|mInterfaces|NDI interface|GET_CAPABILITIES|RESPONSE_TIMEOUT'
```

Do not publish a complete `dumpsys wifi` or `dumpsys wifiaware` without reviewing every line.

## Query Wi-Fi Aware capabilities

First inspect the syntax exposed by the running build:

```sh
su -c 'cmd wifiaware help'
```

On the tested build, the relevant commands were:

```sh
su -c 'cmd wifiaware state_mgr get_capabilities'
su -c 'cmd wifiaware state_mgr get_aware_resources'
su -c 'cmd wifiaware native_cb get_cb_count'
```

One controlled refresh was requested with:

```sh
su -c 'cmd wifiaware state_mgr update_capabilities'
```

This is a transient framework/HAL diagnostic request. A successful capability response does not prove functional NAN discovery, NAN data paths, Quick Share integration, or AWDL support.

## Check for active Aware clients or sessions

```sh
dumpsys wifiaware \
  | grep -E 'mNextClientId|mUidByClientId|mClients|Active NDPs|mInterfaces|mWifiNanIface|COMMAND_TYPE_PUBLISH|COMMAND_TYPE_SUBSCRIBE'
```

Review locally before sharing because client records can identify applications.

## Check the WLAN module for narrowly scoped strings

```sh
lsmod | grep -i -E 'wonder|wlan|cnss'

su -c "grep -a -i -o -E \
  'wondertap|wonder[[:alnum:]_]*|mosey[[:alnum:]_]*|awdl[[:alnum:]_]*' \
  /vendor_dlkm/lib/modules/qca_cld3_peach_v2.ko \
  | sort -u"
```

The module filename is specific to this build. A negative string result is not proof that the hardware or driver cannot provide equivalent behavior.

## Sanitization checklist

Before sharing any output:

1. Keep only lines directly relevant to Mosey, its exact backend routes, SELinux mappings, or tightly scoped NAN/Wi-Fi Aware state.
2. Remove ADB serials, randomized `/data/app` paths, host filesystem paths, device/Quick Share names, timestamps, PIDs/TIDs, SSIDs, BSSIDs, MAC/IP addresses, account data, and unrelated package names.
3. Never upload APKs, splits, firmware, DEX/OAT/VDEX files, native libraries, the KernelSU module, full configuration files, or decompiled source.
4. Never upload full raw logcat, dumpsys, dmesg, KernelSU, Wi-Fi, or macOS AirDrop logs.
5. Remove installed-application lists and all unrelated package names, especially financial-application names.
6. Re-read every excerpt as public and permanent before committing it.
