package dev.gfortes.ccpython.bridge;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DevBridgeAccessStore {
    private final Map<Integer, ComputerAcl> acls = new ConcurrentHashMap<>();

    void load() {
        acls.clear();
        Path path = CCPythonConfig.bridgeAclStorePath();
        if (!Files.exists(path)) return;

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", 4);
                if (parts.length < 3) continue;

                int computerId = parseInt(parts[0], -1);
                UUID ownerUuid = parseUuid(parts[1]);
                String ownerName = parts[2].trim();
                if (computerId < 0 || ownerUuid == null || ownerName.isBlank()) continue;

                var whitelist = new LinkedHashMap<UUID, String>();
                if (parts.length >= 4 && !parts[3].isBlank()) {
                    for (String entry : parts[3].split(";")) {
                        if (entry.isBlank()) continue;
                        int separator = entry.indexOf(':');
                        if (separator <= 0) continue;
                        UUID uuid = parseUuid(entry.substring(0, separator));
                        String name = entry.substring(separator + 1).trim();
                        if (uuid != null && !name.isBlank()) {
                            whitelist.put(uuid, sanitizeName(name));
                        }
                    }
                }

                acls.put(computerId, new ComputerAcl(computerId, ownerUuid, sanitizeName(ownerName), whitelist));
            }
        } catch (IOException exception) {
            CCPythonMod.LOGGER.warn("Failed to load dev bridge ACL store {}.", path, exception);
        }
    }

    synchronized void save() {
        Path path = CCPythonConfig.bridgeAclStorePath();
        var builder = new StringBuilder();
        for (var acl : acls.values().stream().sorted((left, right) -> Integer.compare(left.computerId(), right.computerId())).toList()) {
            builder
                .append(acl.computerId()).append('\t')
                .append(acl.ownerUuid()).append('\t')
                .append(acl.ownerName()).append('\t');

            boolean first = true;
            for (var entry : acl.whitelist().entrySet()) {
                if (!first) builder.append(';');
                first = false;
                builder.append(entry.getKey()).append(':').append(sanitizeName(entry.getValue()));
            }
            builder.append('\n');
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CCPythonMod.LOGGER.warn("Failed to save dev bridge ACL store {}.", path, exception);
        }
    }

    synchronized ComputerAcl get(int computerId) {
        return acls.get(computerId);
    }

    synchronized ComputerAcl setOwner(int computerId, UUID ownerUuid, String ownerName) {
        var current = acls.get(computerId);
        var whitelist = current == null ? new LinkedHashMap<UUID, String>() : new LinkedHashMap<>(current.whitelist());
        whitelist.remove(ownerUuid);

        var acl = new ComputerAcl(computerId, ownerUuid, sanitizeName(ownerName), whitelist);
        acls.put(computerId, acl);
        save();
        return acl;
    }

    synchronized ComputerAcl assignOwnerIfAbsent(int computerId, UUID ownerUuid, String ownerName) {
        var current = acls.get(computerId);
        if (current != null && current.ownerUuid() != null) return current;
        return setOwner(computerId, ownerUuid, ownerName);
    }

    synchronized ComputerAcl claim(int computerId, UUID ownerUuid, String ownerName) {
        var current = acls.get(computerId);
        if (current != null && current.ownerUuid() != null && !current.ownerUuid().equals(ownerUuid)) {
            return current;
        }
        return setOwner(computerId, ownerUuid, ownerName);
    }

    synchronized ComputerAcl grant(int computerId, UUID playerUuid, String playerName) {
        var current = requireOwnerRecord(computerId);
        if (current.ownerUuid().equals(playerUuid)) return current;

        var whitelist = new LinkedHashMap<>(current.whitelist());
        whitelist.put(playerUuid, sanitizeName(playerName));

        var updated = new ComputerAcl(computerId, current.ownerUuid(), current.ownerName(), whitelist);
        acls.put(computerId, updated);
        save();
        return updated;
    }

    synchronized ComputerAcl revoke(int computerId, UUID playerUuid) {
        var current = requireOwnerRecord(computerId);
        if (!current.whitelist().containsKey(playerUuid)) return current;

        var whitelist = new LinkedHashMap<>(current.whitelist());
        whitelist.remove(playerUuid);

        var updated = new ComputerAcl(computerId, current.ownerUuid(), current.ownerName(), whitelist);
        acls.put(computerId, updated);
        save();
        return updated;
    }

    boolean canAccess(int computerId, UUID playerUuid) {
        if (playerUuid == null) return false;
        var acl = acls.get(computerId);
        if (acl == null || acl.ownerUuid() == null) return false;
        return acl.ownerUuid().equals(playerUuid) || acl.whitelist().containsKey(playerUuid);
    }

    private ComputerAcl requireOwnerRecord(int computerId) {
        var acl = acls.get(computerId);
        if (acl == null || acl.ownerUuid() == null) {
            throw new IllegalStateException("Computer " + computerId + " has no owner yet.");
        }
        return acl;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String sanitizeName(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').replace(';', ' ').replace(':', ' ').trim();
    }

    record ComputerAcl(int computerId, UUID ownerUuid, String ownerName, Map<UUID, String> whitelist) {
        ComputerAcl {
            whitelist = Map.copyOf(whitelist);
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("computer_id", computerId);
            map.put("owner", ownerUuid == null ? null : Map.of(
                "uuid", ownerUuid.toString(),
                "name", ownerName
            ));

            var players = whitelist.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                    "uuid", entry.getKey().toString(),
                    "name", entry.getValue()
                ))
                .toList();
            map.put("whitelist", players);
            return map;
        }
    }
}
