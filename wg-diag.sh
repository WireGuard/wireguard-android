#!/bin/bash
# WireGuard Diagnostics Dumper
adb shell am broadcast -a com.wireguard.android.action.DUMP_DIAGNOSTICS -n com.wireguard.android.debug/com.wireguard.android.model.TunnelManager\$IntentReceiver > /dev/null 2>&1
sleep 2
adb logcat -d | sed -n '/=== WIREGUARD DIAGNOSTICS DUMP ===/,/=== END DIAGNOSTICS DUMP ===/p'
