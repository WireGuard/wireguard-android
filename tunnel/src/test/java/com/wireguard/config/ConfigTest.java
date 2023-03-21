/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigTest {

    @Test(expected = BadConfigException.class)
    public void invalid_config_throws() throws IOException, BadConfigException {
        try (final InputStream is = Objects.requireNonNull(getClass().getClassLoader()).getResourceAsStream("broken.conf")) {
            Config.parse(is);
        }
    }

    @Test
    public void valid_config_parses_correctly() throws IOException, ParseException {
        Config config = null;
        final Collection<InetNetwork> expectedAllowedIps = new HashSet<>(Arrays.asList(InetNetwork.parse("0.0.0.0/0"), InetNetwork.parse("::0/0")));
        try (final InputStream is = Objects.requireNonNull(getClass().getClassLoader()).getResourceAsStream("working.conf")) {
            config = Config.parse(is);
        } catch (final BadConfigException e) {
            fail("'working.conf' should never fail to parse");
        }
        assertNotNull("config cannot be null after parsing", config);
        assertTrue(
                "No applications should be excluded by default",
                config.getInterface().getExcludedApplications().isEmpty()
        );
        assertEquals("Test config has exactly one peer", 1, config.getPeers().size());
        assertEquals("Test config's allowed IPs are 0.0.0.0/0 and ::0/0", config.getPeers().get(0).getAllowedIps(), expectedAllowedIps);
        assertEquals("Test config has one DNS server", 1, config.getInterface().getDnsServers().size());
    }
}
