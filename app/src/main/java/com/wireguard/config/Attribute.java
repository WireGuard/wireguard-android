package com.wireguard.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The set of valid attributes for an interface or peer in a WireGuard configuration file.
 */

enum Attribute {
    ADDRESS("Address"),
    ALLOWED_IPS("AllowedIPs"),
    DNS("DNS"),
    ENDPOINT("Endpoint"),
    LISTEN_PORT("ListenPort"),
    MTU("MTU"),
    PERSISTENT_KEEPALIVE("PersistentKeepalive"),
    PRE_SHARED_KEY("PresharedKey"),
    PRIVATE_KEY("PrivateKey"),
    PUBLIC_KEY("PublicKey");

    private static final Map<String, Attribute> map;

    static {
        map = new HashMap<>(Attribute.values().length);
        for (final Attribute key : Attribute.values())
            map.put(key.getToken(), key);
    }

    public static Attribute match(final String line) {
        return map.get(line.split("\\s|=")[0]);
    }

    private final String token;
    private final Pattern pattern;

    Attribute(final String token) {
        pattern = Pattern.compile(token + "\\s*=\\s*(\\S.*)");
        this.token = token;
    }

    public String composeWith(final String value) {
        return token + " = " + value + "\n";
    }

    public String getToken() {
        return token;
    }

    public String parseFrom(final String line) {
        final Matcher matcher = pattern.matcher(line);
        if (matcher.matches())
            return matcher.group(1);
        return null;
    }
}
