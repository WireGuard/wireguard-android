/*
 * Copyright © 2018-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import com.wireguard.util.NonNullForAll;

import android.content.res.Resources;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.BackendException;
import com.wireguard.android.util.RootShell.RootShellException;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.BadConfigException.Location;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.ParseException;
import com.wireguard.crypto.Key.Format;
import com.wireguard.crypto.KeyFormatException;
import com.wireguard.crypto.KeyFormatException.Type;

import java.net.InetAddress;
import java.util.EnumMap;
import java.util.Map;

import java9.util.Maps;

@NonNullForAll
public final class ErrorMessages {
    private static final Map<BadConfigException.Reason, Integer> BCE_REASON_MAP = new EnumMap<>(Maps.of(
            BadConfigException.Reason.INVALID_KEY, R.string.bad_config_reason_invalid_key,
            BadConfigException.Reason.INVALID_NUMBER, R.string.bad_config_reason_invalid_number,
            BadConfigException.Reason.INVALID_VALUE, R.string.bad_config_reason_invalid_value,
            BadConfigException.Reason.MISSING_ATTRIBUTE, R.string.bad_config_reason_missing_attribute,
            BadConfigException.Reason.MISSING_SECTION, R.string.bad_config_reason_missing_section,
            BadConfigException.Reason.MISSING_VALUE, R.string.bad_config_reason_missing_value,
            BadConfigException.Reason.SYNTAX_ERROR, R.string.bad_config_reason_syntax_error,
            BadConfigException.Reason.UNKNOWN_ATTRIBUTE, R.string.bad_config_reason_unknown_attribute,
            BadConfigException.Reason.UNKNOWN_SECTION, R.string.bad_config_reason_unknown_section
    ));
    private static final Map<BackendException.Reason, Integer> BE_REASON_MAP = new EnumMap<>(Maps.of(
            BackendException.Reason.UNKNOWN_KERNEL_MODULE_NAME, R.string.module_version_error,
            BackendException.Reason.WG_QUICK_CONFIG_ERROR_CODE, R.string.tunnel_config_error,
            BackendException.Reason.TUNNEL_MISSING_CONFIG, R.string.no_config_error,
            BackendException.Reason.VPN_NOT_AUTHORIZED, R.string.vpn_not_authorized_error,
            BackendException.Reason.UNABLE_TO_START_VPN, R.string.vpn_start_error,
            BackendException.Reason.TUN_CREATION_ERROR, R.string.tun_create_error,
            BackendException.Reason.GO_ACTIVATION_ERROR_CODE, R.string.tunnel_on_error
    ));
    private static final Map<RootShellException.Reason, Integer> RSE_REASON_MAP = new EnumMap<>(Maps.of(
            RootShellException.Reason.NO_ROOT_ACCESS, R.string.error_root,
            RootShellException.Reason.SHELL_MARKER_COUNT_ERROR, R.string.shell_marker_count_error,
            RootShellException.Reason.SHELL_EXIT_STATUS_READ_ERROR, R.string.shell_exit_status_read_error,
            RootShellException.Reason.SHELL_START_ERROR, R.string.shell_start_error,
            RootShellException.Reason.CREATE_BIN_DIR_ERROR, R.string.create_bin_dir_error,
            RootShellException.Reason.CREATE_TEMP_DIR_ERROR, R.string.create_temp_dir_error
    ));
    private static final Map<Format, Integer> KFE_FORMAT_MAP = new EnumMap<>(Maps.of(
            Format.BASE64, R.string.key_length_explanation_base64,
            Format.BINARY, R.string.key_length_explanation_binary,
            Format.HEX, R.string.key_length_explanation_hex
    ));
    private static final Map<Type, Integer> KFE_TYPE_MAP = new EnumMap<>(Maps.of(
            Type.CONTENTS, R.string.key_contents_error,
            Type.LENGTH, R.string.key_length_error
    ));
    private static final Map<Class, Integer> PE_CLASS_MAP = Maps.of(
            InetAddress.class, R.string.parse_error_inet_address,
            InetEndpoint.class, R.string.parse_error_inet_endpoint,
            InetNetwork.class, R.string.parse_error_inet_network,
            Integer.class, R.string.parse_error_integer
    );

    private ErrorMessages() {
        // Prevent instantiation
    }

    public static String get(@Nullable final Throwable throwable) {
        final Resources resources = Application.get().getResources();
        if (throwable == null)
            return resources.getString(R.string.unknown_error);
        final Throwable rootCause = rootCause(throwable);
        final String message;
        if (rootCause instanceof BadConfigException) {
            final BadConfigException bce = (BadConfigException) rootCause;
            final String reason = getBadConfigExceptionReason(resources, bce);
            final String context = bce.getLocation() == Location.TOP_LEVEL ?
                    resources.getString(R.string.bad_config_context_top_level,
                            bce.getSection().getName()) :
                    resources.getString(R.string.bad_config_context,
                            bce.getSection().getName(),
                            bce.getLocation().getName());
            final String explanation = getBadConfigExceptionExplanation(resources, bce);
            message = resources.getString(R.string.bad_config_error, reason, context) + explanation;
        } else if (rootCause instanceof BackendException) {
            final BackendException be = (BackendException) rootCause;
            message = resources.getString(BE_REASON_MAP.get(be.getReason()), be.getFormat());
        } else if (rootCause instanceof RootShellException) {
            final RootShellException rse = (RootShellException) rootCause;
            message = resources.getString(RSE_REASON_MAP.get(rse.getReason()), rse.getFormat());
        } else if (rootCause.getMessage() != null) {
            message = rootCause.getMessage();
        } else {
            final String errorType = rootCause.getClass().getSimpleName();
            message = resources.getString(R.string.generic_error, errorType);
        }
        return message;
    }

    private static String getBadConfigExceptionExplanation(final Resources resources,
                                                           final BadConfigException bce) {
        if (bce.getCause() instanceof KeyFormatException) {
            final KeyFormatException kfe = (KeyFormatException) bce.getCause();
            if (kfe.getType() == Type.LENGTH)
                return resources.getString(KFE_FORMAT_MAP.get(kfe.getFormat()));
        } else if (bce.getCause() instanceof ParseException) {
            final ParseException pe = (ParseException) bce.getCause();
            if (pe.getMessage() != null)
                return ": " + pe.getMessage();
        } else if (bce.getLocation() == Location.LISTEN_PORT) {
            return resources.getString(R.string.bad_config_explanation_udp_port);
        } else if (bce.getLocation() == Location.MTU) {
            return resources.getString(R.string.bad_config_explanation_positive_number);
        } else if (bce.getLocation() == Location.PERSISTENT_KEEPALIVE) {
            return resources.getString(R.string.bad_config_explanation_pka);
        }
        return "";
    }

    private static String getBadConfigExceptionReason(final Resources resources,
                                                      final BadConfigException bce) {
        if (bce.getCause() instanceof KeyFormatException) {
            final KeyFormatException kfe = (KeyFormatException) bce.getCause();
            return resources.getString(KFE_TYPE_MAP.get(kfe.getType()));
        } else if (bce.getCause() instanceof ParseException) {
            final ParseException pe = (ParseException) bce.getCause();
            final String type = resources.getString(PE_CLASS_MAP.containsKey(pe.getParsingClass()) ?
                    PE_CLASS_MAP.get(pe.getParsingClass()) : R.string.parse_error_generic);
            return resources.getString(R.string.parse_error_reason, type, pe.getText());
        }
        return resources.getString(BCE_REASON_MAP.get(bce.getReason()), bce.getText());
    }

    private static Throwable rootCause(final Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            if (cause instanceof BadConfigException || cause instanceof BackendException ||
                    cause instanceof RootShellException)
                break;
            final Throwable nextCause = cause.getCause();
            if (nextCause instanceof RemoteException)
                break;
            cause = nextCause;
        }
        return cause;
    }
}
