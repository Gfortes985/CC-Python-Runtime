package dev.gfortes.ccpython.bridge;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DevBridgeAuthStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_CODE_LENGTH = 6;
    private static final long TOKEN_TOUCH_INTERVAL_MILLIS = 60_000L;

    private final Map<String, IssuedToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, PairingCode> pendingCodes = new ConcurrentHashMap<>();

    void load() {
        tokens.clear();
        Path path = CCPythonConfig.bridgeTokenStorePath();
        if (!Files.exists(path)) return;

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 5) continue;

                if (parts.length >= 8) {
                    String token = parts[0];
                    String tokenId = parts[1];
                    String label = sanitizeLabel(parts[2]);
                    TokenKind kind = TokenKind.fromSerialized(parts[3]);
                    UUID playerUuid = parseUuid(parts[4]);
                    String playerName = sanitizeNullable(parts[5]);
                    long createdAt = parseLong(parts[6], System.currentTimeMillis());
                    long lastUsedAt = parseLong(parts[7], createdAt);
                    tokens.put(token, new IssuedToken(token, tokenId, label, kind, playerUuid, playerName, createdAt, lastUsedAt));
                    continue;
                }

                String token = parts[0];
                String tokenId = parts[1];
                String label = sanitizeLabel(parts[2]);
                long createdAt = parseLong(parts[3], System.currentTimeMillis());
                long lastUsedAt = parseLong(parts[4], createdAt);
                tokens.put(token, new IssuedToken(token, tokenId, label, TokenKind.ADMIN, null, null, createdAt, lastUsedAt));
            }
        } catch (IOException exception) {
            CCPythonMod.LOGGER.warn("Failed to load dev bridge token store {}.", path, exception);
        }
    }

    synchronized PairingCode startPairing(String requestedLabel, UUID playerUuid, String playerName, long ttlSeconds) {
        clearExpiredCodes();

        long now = System.currentTimeMillis();
        long expiresAt = now + Math.max(ttlSeconds, 30L) * 1000L;
        String label = sanitizeLabel(requestedLabel == null || requestedLabel.isBlank() ? "VS Code" : requestedLabel);
        String safePlayerName = sanitizeLabel(playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName);

        String code;
        do {
            code = randomCode(DEFAULT_CODE_LENGTH);
        } while (pendingCodes.containsKey(code));

        var pairing = new PairingCode(code, label, playerUuid, safePlayerName, now, expiresAt);
        pendingCodes.put(code, pairing);
        return pairing;
    }

    synchronized IssuedToken completePairing(String code, String requestedLabel) {
        clearExpiredCodes();

        var pairing = pendingCodes.remove(code);
        if (pairing == null || pairing.expiresAt() < System.currentTimeMillis()) {
            return null;
        }

        String label = sanitizeLabel(requestedLabel == null || requestedLabel.isBlank() ? pairing.label() : requestedLabel);
        String token = randomToken();
        String tokenId = "pair-" + Long.toUnsignedString(RANDOM.nextLong(), 36);
        long now = System.currentTimeMillis();
        var issued = new IssuedToken(
            token,
            tokenId,
            label,
            TokenKind.PLAYER,
            pairing.playerUuid(),
            pairing.playerName(),
            now,
            now
        );
        tokens.put(token, issued);
        save();
        return issued;
    }

    IssuedToken validate(String token) {
        if (token == null || token.isBlank()) return null;

        var issued = tokens.get(token);
        if (issued == null) return null;

        long now = System.currentTimeMillis();
        if (now - issued.lastUsedAt() < TOKEN_TOUCH_INTERVAL_MILLIS) {
            return issued;
        }

        var touched = issued.touch(now);
        tokens.put(token, touched);
        save();
        return touched;
    }

    synchronized void clearExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    synchronized void save() {
        Path path = CCPythonConfig.bridgeTokenStorePath();
        var builder = new StringBuilder();
        for (var issued : tokens.values()) {
            builder
                .append(issued.token()).append('\t')
                .append(issued.tokenId()).append('\t')
                .append(issued.label()).append('\t')
                .append(issued.kind().serialized()).append('\t')
                .append(issued.playerUuid() == null ? "" : issued.playerUuid()).append('\t')
                .append(issued.playerName() == null ? "" : issued.playerName()).append('\t')
                .append(issued.createdAt()).append('\t')
                .append(issued.lastUsedAt()).append('\n');
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CCPythonMod.LOGGER.warn("Failed to save dev bridge token store {}.", path, exception);
        }
    }

    Map<String, Object> describeAuthState() {
        var map = new LinkedHashMap<String, Object>();
        map.put("pairing_enabled", CCPythonConfig.devBridgePairingEnabled());
        map.put("paired_clients", tokens.size());
        map.put("pending_pair_codes", pendingCodes.size());
        return map;
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizeLabel(String label) {
        return label.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String sanitizeNullable(String label) {
        if (label == null || label.isBlank()) return null;
        return sanitizeLabel(label);
    }

    private static String randomCode(int length) {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        var builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet[RANDOM.nextInt(alphabet.length)]);
        }
        return builder.toString();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    enum TokenKind {
        ADMIN("admin"),
        PLAYER("player");

        private final String serialized;

        TokenKind(String serialized) {
            this.serialized = serialized;
        }

        String serialized() {
            return serialized;
        }

        static TokenKind fromSerialized(String raw) {
            if ("player".equalsIgnoreCase(raw)) return PLAYER;
            return ADMIN;
        }
    }

    record PairingCode(String code, String label, UUID playerUuid, String playerName, long createdAt, long expiresAt) {
        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("code", code);
            map.put("label", label);
            map.put("player_uuid", playerUuid.toString());
            map.put("player_name", playerName);
            map.put("created_at", createdAt);
            map.put("expires_at", expiresAt);
            map.put("expires_at_iso", Instant.ofEpochMilli(expiresAt).toString());
            return map;
        }
    }

    record IssuedToken(
        String token,
        String tokenId,
        String label,
        TokenKind kind,
        UUID playerUuid,
        String playerName,
        long createdAt,
        long lastUsedAt
    ) {
        IssuedToken touch(long timestamp) {
            return new IssuedToken(token, tokenId, label, kind, playerUuid, playerName, createdAt, timestamp);
        }

        Map<String, Object> toPublicMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("token_id", tokenId);
            map.put("label", label);
            map.put("kind", kind.serialized());
            map.put("player_uuid", playerUuid == null ? null : playerUuid.toString());
            map.put("player_name", playerName);
            map.put("created_at", createdAt);
            map.put("last_used_at", lastUsedAt);
            return map;
        }
    }
}
