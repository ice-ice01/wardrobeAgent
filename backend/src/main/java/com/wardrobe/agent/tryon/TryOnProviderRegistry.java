package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TryOnProviderRegistry {
    private final Map<String, TryOnProvider> providers;

    public TryOnProviderRegistry(java.util.List<TryOnProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.code().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public TryOnProvider require(String code) {
        TryOnProvider provider = providers.get(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
        if (provider == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_UNSUPPORTED", "试穿 Provider 不受支持");
        return provider;
    }

    public Optional<TryOnProvider> find(String code) {
        return Optional.ofNullable(providers.get(code == null ? "" : code.trim().toUpperCase(Locale.ROOT)));
    }
}
