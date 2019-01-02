/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (C) 2017-2019 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

package tun

import (
	"git.zx2c4.com/wireguard-go/rwcancel"
	"os"
)

func CreateTUNFromFD(tunFd int) (TUNDevice, string, error) {
	file := os.NewFile(uintptr(tunFd), "/dev/tun")
	tun := &nativeTun{
		tunFile: file,
		fd:      file.Fd(),
		events:  make(chan TUNEvent, 5),
		errors:  make(chan error, 5),
		nopi:    true,
	}
	var err error
	tun.fdCancel, err = rwcancel.NewRWCancel(tunFd)
	if err != nil {
		return nil, "", err
	}
	name, err := tun.Name()
	if err != nil {
		tun.fdCancel.Cancel()
		return nil, "", err
	}
	return tun, name, nil
}
