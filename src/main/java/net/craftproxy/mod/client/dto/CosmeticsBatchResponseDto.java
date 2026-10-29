package net.craftproxy.mod.client.dto;

import java.util.List;

public record CosmeticsBatchResponseDto(
        List<CosmeticEntryDto> players
) {}
