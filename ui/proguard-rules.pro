# Squelch all warnings, they're harmless but ProGuard
# escalates them as errors.
-dontwarn sun.misc.Unsafe

# Fragment 1.2.4 allows Fragment classes to be obfuscated but
# databinding references in XML seem to not be rewritten to
# match, so we preserve the names as 1.2.3 did.
-if public class ** extends androidx.fragment.app.Fragment
-keep public class <1> {
    public <init>();
}

# Don't obfuscate
-dontobfuscate
