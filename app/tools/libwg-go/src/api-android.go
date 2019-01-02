/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (C) 2017-2019 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

package main

// #cgo LDFLAGS: -llog
// #include <android/log.h>
import "C"

import (
	"bufio"
	"git.zx2c4.com/wireguard-go/tun"
	"golang.org/x/sys/unix"
	"io/ioutil"
	"log"
	"math"
	"net"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"unsafe"
)

type AndroidLogger struct {
	level         C.int
	interfaceName string
}

func (l AndroidLogger) Write(p []byte) (int, error) {
	C.__android_log_write(l.level, C.CString("WireGuard/GoBackend/"+l.interfaceName), C.CString(string(p)))
	return len(p), nil
}

type TunnelHandle struct {
	device *Device
	uapi   net.Listener
}

var tunnelHandles map[int32]TunnelHandle

func init() {
	roamingDisabled = true
	tunnelHandles = make(map[int32]TunnelHandle)
	signals := make(chan os.Signal)
	signal.Notify(signals, unix.SIGUSR2)
	go func() {
		buf := make([]byte, os.Getpagesize())
		for {
			select {
			case <-signals:
				n := runtime.Stack(buf, true)
				buf[n] = 0
				C.__android_log_write(C.ANDROID_LOG_ERROR, C.CString("WireGuard/GoBackend/Stacktrace"), (*_Ctype_char)(unsafe.Pointer(&buf[0])))
			}
		}
	}()
}

//export wgTurnOn
func wgTurnOn(ifnameRef string, tunFd int32, settings string) int32 {
	interfaceName := string([]byte(ifnameRef))

	logger := &Logger{
		Debug: log.New(&AndroidLogger{level: C.ANDROID_LOG_DEBUG, interfaceName: interfaceName}, "", 0),
		Info:  log.New(&AndroidLogger{level: C.ANDROID_LOG_INFO, interfaceName: interfaceName}, "", 0),
		Error: log.New(&AndroidLogger{level: C.ANDROID_LOG_ERROR, interfaceName: interfaceName}, "", 0),
	}

	logger.Debug.Println("Debug log enabled")

	tun, name, err := tun.CreateTUNFromFD(int(tunFd))
	if err != nil {
		unix.Close(int(tunFd))
		logger.Error.Println(err)
		return -1
	}

	logger.Info.Println("Attaching to interface", name)
	device := NewDevice(tun, logger)

	logger.Debug.Println("Interface has MTU", device.tun.mtu)

	bufferedSettings := bufio.NewReadWriter(bufio.NewReader(strings.NewReader(settings)), bufio.NewWriter(ioutil.Discard))
	setError := ipcSetOperation(device, bufferedSettings)
	if setError != nil {
		unix.Close(int(tunFd))
		logger.Error.Println(setError)
		return -1
	}

	var uapi net.Listener;

	uapiFile, err := UAPIOpen(name)
	if err != nil {
		logger.Error.Println(err)
	} else {
		uapi, err = UAPIListen(name, uapiFile)
		if err != nil {
			uapiFile.Close()
			logger.Error.Println(err)
		} else {
			go func() {
				for {
					conn, err := uapi.Accept()
					if err != nil {
						return
					}
					go ipcHandle(device, conn)
				}
			}()
		}
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
		unix.Close(int(tunFd))
		return -1
	}
	tunnelHandles[i] = TunnelHandle{device: device, uapi: uapi}
	return i
}

//export wgTurnOff
func wgTurnOff(tunnelHandle int32) {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return
	}
	delete(tunnelHandles, tunnelHandle)
	if handle.uapi != nil {
		handle.uapi.Close()
	}
	handle.device.Close()
}

//export wgGetSocketV4
func wgGetSocketV4(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	native, ok := handle.device.net.bind.(*NativeBind)
	if !ok {
		return -1
	}
	fd := int32(-1)
	conn, err := native.ipv4.SyscallConn()
	if err != nil {
		return -1
	}
	err = conn.Control(func(f uintptr) {
		fd = int32(f)
	})
	if err != nil {
		return -1
	}
	return fd
}

//export wgGetSocketV6
func wgGetSocketV6(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	native, ok := handle.device.net.bind.(*NativeBind)
	if !ok {
		return -1
	}
	fd := int32(-1)
	conn, err := native.ipv6.SyscallConn()
	if err != nil {
		return -1
	}
	err = conn.Control(func(f uintptr) {
		fd = int32(f)
	})
	if err != nil {
		return -1
	}
	return fd
}

//export wgVersion
func wgVersion() *C.char {
	return C.CString(WireGuardGoVersion)
}

func main() {}
