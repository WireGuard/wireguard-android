#!/bin/bash
set -ex
curl -Lo - https://crowdin.com/backend/download/project/wireguard.zip | bsdtar -C ui/src/main/res -x -f - --strip-components 5 wireguard-android
find ui/src/main/res -name strings.xml -exec bash -c '[[ $(xmllint --xpath "count(//resources/*)" {}) -ne 0 ]] || rm -rf "$(dirname {})"' \;
