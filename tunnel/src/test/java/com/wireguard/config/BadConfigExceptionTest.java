/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.config.BadConfigException.Location;
import com.wireguard.config.BadConfigException.Reason;
import com.wireguard.config.BadConfigException.Section;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BadConfigExceptionTest {
    private static final Map<String, InputStream> CONFIG_MAP = new HashMap<>();
    private static final String[] CONFIG_NAMES = {
            "invalid-key",
            "invalid-number",
            "invalid-value",
            "missing-attribute",
            "missing-section",
            "syntax-error",
            "unknown-attribute",
            "unknown-section"
    };

    @AfterClass
    public static void closeStreams() {
        for (final InputStream inputStream : CONFIG_MAP.values()) {
            try {
                inputStream.close();
            } catch (final IOException ignored) {
            }
        }
    }

    @BeforeClass
    public static void readConfigs() {
        for (final String config : CONFIG_NAMES) {
            CONFIG_MAP.put(config, BadConfigExceptionTest.class.getClassLoader().getResourceAsStream(config + ".conf"));
        }
    }

    @Test
    public void throws_correctly_with_INVALID_KEY_reason() {
        try {
            Config.parse(CONFIG_MAP.get("invalid-key"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.INVALID_KEY);
            assertEquals(e.getLocation(), Location.PUBLIC_KEY);
            assertEquals(e.getSection(), Section.PEER);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException thrown during test");
        }
    }

    @Test
    public void throws_correctly_with_INVALID_NUMBER_reason() {
        try {
            Config.parse(CONFIG_MAP.get("invalid-number"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.INVALID_NUMBER);
            assertEquals(e.getLocation(), Location.PERSISTENT_KEEPALIVE);
            assertEquals(e.getSection(), Section.PEER);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException thrown during test");
        }
    }

    @Test
    public void throws_correctly_with_INVALID_VALUE_reason() {
        try {
            Config.parse(CONFIG_MAP.get("invalid-value"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.INVALID_VALUE);
            assertEquals(e.getLocation(), Location.DNS);
            assertEquals(e.getSection(), Section.INTERFACE);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }

    @Test
    public void throws_correctly_with_MISSING_ATTRIBUTE_reason() {
        try {
            Config.parse(CONFIG_MAP.get("missing-attribute"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.MISSING_ATTRIBUTE);
            assertEquals(e.getLocation(), Location.PUBLIC_KEY);
            assertEquals(e.getSection(), Section.PEER);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }

    @Test
    public void throws_correctly_with_MISSING_SECTION_reason() {
        try {
            Config.parse(CONFIG_MAP.get("missing-section"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.MISSING_SECTION);
            assertEquals(e.getLocation(), Location.TOP_LEVEL);
            assertEquals(e.getSection(), Section.CONFIG);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }

    @Test
    public void throws_correctly_with_SYNTAX_ERROR_reason() {
        try {
            Config.parse(CONFIG_MAP.get("syntax-error"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.SYNTAX_ERROR);
            assertEquals(e.getLocation(), Location.TOP_LEVEL);
            assertEquals(e.getSection(), Section.PEER);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }

    @Test
    public void throws_correctly_with_UNKNOWN_ATTRIBUTE_reason() {
        try {
            Config.parse(CONFIG_MAP.get("unknown-attribute"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.UNKNOWN_ATTRIBUTE);
            assertEquals(e.getLocation(), Location.TOP_LEVEL);
            assertEquals(e.getSection(), Section.PEER);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }

    @Test
    public void throws_correctly_with_UNKNOWN_SECTION_reason() {
        try {
            Config.parse(CONFIG_MAP.get("unknown-section"));
            fail("Config parsing must fail in this test");
        } catch (final BadConfigException e) {
            assertEquals(e.getReason(), Reason.UNKNOWN_SECTION);
            assertEquals(e.getLocation(), Location.TOP_LEVEL);
            assertEquals(e.getSection(), Section.CONFIG);
        } catch (final IOException e) {
            e.printStackTrace();
            fail("IOException throwing during test");
        }
    }
}
