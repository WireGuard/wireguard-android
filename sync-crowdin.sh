#!/bin/bash
if [[ $# -ne 1 ]]; then
	echo "Download https://crowdin.com/backend/download/project/wireguard.zip and provide path as argument"
	exit 1
fi

set -ex

bsdtar -C ui/src/main/res -x -f "$1" --strip-components 5 wireguard-android
find ui/src/main/res -name strings.xml -exec bash -c '[[ $(xmllint --xpath "count(//resources/*)" {}) -ne 0 ]] || rm -rf "$(dirname {})"' \;
