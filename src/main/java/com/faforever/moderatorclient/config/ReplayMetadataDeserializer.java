package com.faforever.moderatorclient.config;

import com.faforever.commons.replay.ReplayMetadata;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Locale;

public class ReplayMetadataDeserializer extends StdDeserializer<ReplayMetadata> {
    private static final ObjectMapper DELEGATE = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ReplayMetadataDeserializer() {
        super(ReplayMetadata.class);
    }

    @Override
    public ReplayMetadata deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectNode root = parser.getCodec().readTree(parser);
        JsonNode gameTypeNode = root.get("game_type");
        Integer normalizedGameType = normalizeGameType(gameTypeNode);

        if (normalizedGameType != null) {
            root.put("game_type", normalizedGameType);
        }

        return DELEGATE.treeToValue(root, ReplayMetadata.class);
    }

    private Integer normalizeGameType(JsonNode gameTypeNode) {
        if (gameTypeNode == null || gameTypeNode.isNull()) {
            return null;
        }

        if (gameTypeNode.isIntegralNumber()) {
            return gameTypeNode.intValue();
        }

        if (!gameTypeNode.isTextual()) {
            return null;
        }

        String value = gameTypeNode.asText().trim();
        if (value.isEmpty()) {
            return null;
        }

        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }

        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DEMORALIZATION" -> 0;
            case "DOMINATION" -> 1;
            case "ERADICATION" -> 2;
            case "SANDBOX" -> 3;
            default -> null;
        };
    }
}
