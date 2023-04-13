/* SPDX-License-Identifier: BSD
 *
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 *
 */

#define FILE_IS_EMPTY

#if defined(__ANDROID_MIN_SDK_VERSION__) && __ANDROID_MIN_SDK_VERSION__ < 24
#undef FILE_IS_EMPTY
#include <string.h>

char *strchrnul(const char *s, int c)
{
        char *x = strchr(s, c);
        if (!x)
                return (char *)s + strlen(s);
        return x;
}
#endif

#ifdef FILE_IS_EMPTY
#undef FILE_IS_EMPTY
static char ____x __attribute__((unused));
#endif
