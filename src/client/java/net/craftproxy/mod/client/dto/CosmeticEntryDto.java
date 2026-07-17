package net.craftproxy.mod.client.dto;

import java.util.UUID;

public record CosmeticEntryDto(
        UUID uuid,
        String cosmeticId,
        String textureUrl
) {}
