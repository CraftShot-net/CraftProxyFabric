package net.craftproxy.mod.client.dto;

import java.util.List;

public record MyCosmeticsResponseDto(List<PlayerCosmeticEntryDto> cosmetics) {
    public record PlayerCosmeticEntryDto(
            long id,
            String cosmeticId,
            String type,
            boolean equipped,
            String textureUrl
    ) {}
}
