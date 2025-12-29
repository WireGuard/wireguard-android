// SPDX-License-Identifier: GPL-2.0 OR MIT
/*
 * Copyright (C) 2015-2020 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "containers.h"
#include "config.h"
#include "ipc.h"
#include "subcommands.h"

struct peer_origin {
	struct wgpeer *peer;
	bool from_file;
};

static int peer_cmp(const void *first, const void *second)
{
	const struct peer_origin *a = first, *b = second;
	int ret = memcmp(a->peer->public_key, b->peer->public_key, WG_KEY_LEN);
	if (ret)
		return ret;
	return a->from_file - b->from_file;
}

static bool sync_conf(struct wgdevice *file)
{
	struct wgdevice *runtime;
	struct wgpeer *peer;
	struct peer_origin *peers;
	size_t peer_count = 0, i = 0;

	if (!file->first_peer)
		return true;

	for_each_wgpeer(file, peer)
		++peer_count;

	if (ipc_get_device(&runtime, file->name) != 0) {
		perror("Unable to retrieve current interface configuration");
		return false;
	}

	if (!runtime->first_peer) {
		free_wgdevice(runtime);
		return true;
	}

	file->flags &= ~WGDEVICE_REPLACE_PEERS;

	for_each_wgpeer(runtime, peer)
		++peer_count;

	peers = calloc(peer_count, sizeof(*peers));
	if (!peers) {
		free_wgdevice(runtime);
		perror("Peer list allocation");
		return false;
	}

	for_each_wgpeer(file, peer) {
		peers[i].peer = peer;
		peers[i].from_file = true;
		++i;
	}
	for_each_wgpeer(runtime, peer) {
		peers[i].peer = peer;
		peers[i].from_file = false;
		++i;
	}
	qsort(peers, peer_count, sizeof(*peers), peer_cmp);

	for (i = 0; i < peer_count; ++i) {
		if (peers[i].from_file)
			continue;
		if (i == peer_count - 1 || !peers[i + 1].from_file || memcmp(peers[i].peer->public_key, peers[i + 1].peer->public_key, WG_KEY_LEN)) {
			peer = calloc(1, sizeof(struct wgpeer));
			if (!peer) {
				free_wgdevice(runtime);
				free(peers);
				perror("Peer allocation");
				return false;
			}
			peer->flags = WGPEER_REMOVE_ME;
			memcpy(peer->public_key, peers[i].peer->public_key, WG_KEY_LEN);
			peer->next_peer = file->first_peer;
			file->first_peer = peer;
			if (!file->last_peer)
				file->last_peer = peer;
		} else if (i < peer_count - 1 && peers[i + 1].from_file &&
		           (peers[i].peer->flags & WGPEER_HAS_PRESHARED_KEY) && !(peers[i + 1].peer->flags & WGPEER_HAS_PRESHARED_KEY) &&
			   !memcmp(peers[i].peer->public_key, peers[i + 1].peer->public_key, WG_KEY_LEN)) {
			memset(peers[i + 1].peer->preshared_key, 0, WG_KEY_LEN);
			peers[i + 1].peer->flags |= WGPEER_HAS_PRESHARED_KEY;
		}
	}
	free_wgdevice(runtime);
	free(peers);
	return true;
}

int setconf_main(int argc, const char *argv[])
{
	struct wgdevice *device = NULL;
	struct config_ctx ctx;
	FILE *config_input = NULL;
	char *config_buffer = NULL;
	size_t config_buffer_len = 0;
	int ret = 1;

	if (argc != 3) {
		fprintf(stderr, "Usage: %s %s <interface> <configuration filename>\n", PROG_NAME, argv[0]);
		return 1;
	}

	config_input = fopen(argv[2], "r");
	if (!config_input) {
		perror("fopen");
		return 1;
	}
	if (!config_read_init(&ctx, !strcmp(argv[0], "addconf"))) {
		fclose(config_input);
		return 1;
	}
	while (getline(&config_buffer, &config_buffer_len, config_input) >= 0) {
		if (!config_read_line(&ctx, config_buffer)) {
			fprintf(stderr, "Configuration parsing error\n");
			goto cleanup;
		}
	}
	device = config_read_finish(&ctx);
	if (!device) {
		fprintf(stderr, "Invalid configuration\n");
		goto cleanup;
	}
	strncpy(device->name, argv[1], IFNAMSIZ - 1);
	device->name[IFNAMSIZ - 1] = '\0';

	if (!strcmp(argv[0], "syncconf")) {
		if (!sync_conf(device))
			goto cleanup;
	}

	if (ipc_set_device(device) != 0) {
		perror("Unable to modify interface");
		goto cleanup;
	}

	ret = 0;

cleanup:
	if (config_input)
		fclose(config_input);
	free(config_buffer);
	free_wgdevice(device);
	return ret;
}
