# Squelch all warnings, they're harmless but ProGuard
# escalates them as errors.
-dontwarn sun.misc.Unsafe

# Retain some information to keep stacktraces usable
-keepattributes SourceFile,LineNumberTable
