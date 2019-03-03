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
	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/ipc"
	"golang.zx2c4.com/wireguard/tun"
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
	device *device.Device
	uapi   net.Listener
}

var tunnelHandles map[int32]TunnelHandle

func init() {
	device.RoamingDisabled = true
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
				C.__android_log_write(C.ANDROID_LOG_ERROR, C.CString("WireGuard/GoBackend/Stacktrace"), (*C.char)(unsafe.Pointer(&buf[0])))
			}
		}
	}()
}

//export wgTurnOn
func wgTurnOn(ifnameRef string, tunFd int32, settings string) int32 {
	interfaceName := string([]byte(ifnameRef))

	logger := &device.Logger{
		Debug: log.New(&AndroidLogger{level: C.ANDROID_LOG_DEBUG, interfaceName: interfaceName}, "", 0),
		Info:  log.New(&AndroidLogger{level: C.ANDROID_LOG_INFO, interfaceName: interfaceName}, "", 0),
		Error: log.New(&AndroidLogger{level: C.ANDROID_LOG_ERROR, interfaceName: interfaceName}, "", 0),
	}

	logger.Debug.Println("Debug log enabled")

	tun, name, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))
	if err != nil {
		unix.Close(int(tunFd))
		logger.Error.Println(err)
		return -1
	}

	logger.Info.Println("Attaching to interface", name)
	device := device.NewDevice(tun, logger)

	setError := device.IpcSetOperation(bufio.NewReader(strings.NewReader(settings)))
	if setError != nil {
		unix.Close(int(tunFd))
		logger.Error.Println(setError)
		return -1
	}

	var uapi net.Listener

	uapiFile, err := ipc.UAPIOpen(name)
	if err != nil {
		logger.Error.Println(err)
	} else {
		uapi, err = ipc.UAPIListen(name, uapiFile)
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
					go device.IpcHandle(conn)
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
	fd, err := handle.device.PeekLookAtSocketFd4()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export wgGetSocketV6
func wgGetSocketV6(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	fd, err := handle.device.PeekLookAtSocketFd6()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export wgVersion
func wgVersion() *C.char {
	return C.CString(device.WireGuardGoVersion)
}

func main() {}
