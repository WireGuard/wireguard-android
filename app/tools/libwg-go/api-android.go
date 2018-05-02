/* SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Copyright (C) 2017-2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

package main

// #cgo LDFLAGS: -llog
// #include <android/log.h>
import "C"

import (
	"bufio"
	"io/ioutil"
	"log"
	"math"
	"os"
	"strings"
	"syscall"
)

type AndroidLogger struct {
	level         C.int
	interfaceName string
}

func (l AndroidLogger) Write(p []byte) (int, error) {
	C.__android_log_write(l.level, C.CString("WireGuard/GoBackend/"+l.interfaceName), C.CString(string(p)))
	return len(p), nil
}

var tunnelHandles map[int32]*Device

func init() {
	tunnelHandles = make(map[int32]*Device)
}

//export wgTurnOn
func wgTurnOn(ifnameRef string, tun_fd int32, settings string) int32 {
	interfaceName := string([]byte(ifnameRef))

	logger := &Logger{
		Debug: log.New(&AndroidLogger{level: C.ANDROID_LOG_DEBUG, interfaceName: interfaceName}, "", 0),
		Info:  log.New(&AndroidLogger{level: C.ANDROID_LOG_INFO, interfaceName: interfaceName}, "", 0),
		Error: log.New(&AndroidLogger{level: C.ANDROID_LOG_ERROR, interfaceName: interfaceName}, "", 0),
	}

	logger.Debug.Println("Debug log enabled")

	tun := &NativeTun{
		fd:     os.NewFile(uintptr(tun_fd), "/dev/tun"),
		events: make(chan TUNEvent, 5),
		errors: make(chan error, 5),
		nopi:   true,
	}
	var err error

	err = syscall.SetNonblock(int(tun_fd), true)
	if err != nil {
		logger.Error.Println(err)
		return -1
	}
	tun.closingReader, tun.closingWriter, err = os.Pipe()
	if err != nil {
		logger.Error.Println(err)
		return -1
	}
	name, err := tun.Name()
	if err != nil {
		logger.Error.Println(err)
		return -1
	}
	logger.Info.Println("Attaching to interface", name)
	device := NewDevice(tun, logger)
	logger.Debug.Println("Interface has MTU", device.tun.mtu)

	bufferedSettings := bufio.NewReadWriter(bufio.NewReader(strings.NewReader(settings)), bufio.NewWriter(ioutil.Discard))
	setError := ipcSetOperation(device, bufferedSettings)
	if setError != nil {
		logger.Error.Println(setError)
		return -1
	}

	device.Up()
	logger.Info.Println("Device started")

	var i int32
	for i = 0; i < math.MaxInt32; i++ {
		if _, exists := tunnelHandles[i]; !exists {
			break
		}
	}
	if i == math.MaxInt32 {
		return -1
	}
	tunnelHandles[i] = device
	return i
}

//export wgTurnOff
func wgTurnOff(tunnelHandle int32) {
	device, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return
	}
	delete(tunnelHandles, tunnelHandle)
	device.Close()
}

//export wgGetSocketV4
func wgGetSocketV4(tunnelHandle int32) int32 {
	device, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	native, ok := device.net.bind.(*NativeBind)
	if !ok {
		return -1
	}
	return int32(native.sock4)
}

//export wgGetSocketV6
func wgGetSocketV6(tunnelHandle int32) int32 {
	device, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	native, ok := device.net.bind.(*NativeBind)
	if !ok {
		return -1
	}
	return int32(native.sock6)
}

func main() {}
