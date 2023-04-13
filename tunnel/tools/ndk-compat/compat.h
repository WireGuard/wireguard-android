/* SPDX-License-Identifier: BSD
 *
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 *
 */

#if defined(__ANDROID_MIN_SDK_VERSION__) && __ANDROID_MIN_SDK_VERSION__ < 24
char *strchrnul(const char *s, int c);
#endif

