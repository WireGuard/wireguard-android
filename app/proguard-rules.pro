# Squelch all warnings, they're harmless but ProGuard
# escalates them as errors.
-dontwarn sun.misc.Unsafe
# We're OSS anyway and who doesn't love a readable log
-dontobfuscate
