package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityChange;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioSharesService {

    private final ActionRepository actionRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityPriceRepository securityPriceRepository;

    /**
     * Represents a security holding in a portfolio
     */
    @Getter
    @Builder
    public static class SecurityHolding {
        private final Security security;
        private final BigInteger volume;
        private final Double value; // Day value based on price
    }

    /**
     * Calculate portfolio shares at a specific date and time
     * @param portfolioId The portfolio ID
     * @param dateTime The date and time to calculate holdings for
     * @return List of SecurityHolding sorted by security name
     */
    public List<SecurityHolding> getPortfolioSharesAtDateTime(String portfolioId, LocalDateTime dateTime) {
        // Validate portfolio exists
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        // Get all security changes before the specified datetime
        Map<Security, BigInteger> securityVolumeMap = new HashMap<>();

        actionRepository.findAllActionsOf(portfolioId).stream()
                .filter(action -> action.getDatetime() != null && !action.getDatetime().isAfter(dateTime))
                .flatMap(action -> action.getSecurityChanges().stream())
                .forEach(securityChange -> {
                    Security security = securityChange.getSecurity();
                    BigInteger currentVolume = securityVolumeMap.getOrDefault(security, BigInteger.ZERO);
                    BigInteger newVolume = currentVolume.add(securityChange.getVolumeChange());

                    if (newVolume.compareTo(BigInteger.ZERO) > 0) {
                        securityVolumeMap.put(security, newVolume);
                    } else {
                        // Remove security if volume becomes zero or negative
                        securityVolumeMap.remove(security);
                    }
                });

        // Convert to SecurityHolding list with day values
        LocalDate date = dateTime.toLocalDate();
        List<SecurityHolding> holdings = new ArrayList<>();

        for (Map.Entry<Security, BigInteger> entry : securityVolumeMap.entrySet()) {
            Security security = entry.getKey();
            BigInteger volume = entry.getValue();

            // Get day value from SecurityPriceRepository
            Double dayValue = securityPriceRepository.getPrice(security.getIsin(), date)
                    .map(price -> price * volume.doubleValue())
                    .orElse(null);

            holdings.add(SecurityHolding.builder()
                    .security(security)
                    .volume(volume)
                    .value(dayValue)
                    .build());
        }

        // Sort by security name
        holdings.sort(Comparator.comparing(h -> h.getSecurity().getName() != null ?
                h.getSecurity().getName() : h.getSecurity().getSymbol()));

        return holdings;
    }

    /**
     * Get all securities held in a portfolio at a specific datetime
     * @param portfolioId The portfolio ID
     * @param dateTime The date and time
     * @return Map of Security to Volume
     */
    public Map<Security, BigInteger> getSecurityVolumes(String portfolioId, LocalDateTime dateTime) {
        Map<Security, BigInteger> securityVolumeMap = new HashMap<>();

        actionRepository.findAllActionsOf(portfolioId).stream()
                .filter(action -> action.getDatetime() != null && !action.getDatetime().isAfter(dateTime))
                .flatMap(action -> action.getSecurityChanges().stream())
                .forEach(securityChange -> {
                    Security security = securityChange.getSecurity();
                    BigInteger currentVolume = securityVolumeMap.getOrDefault(security, BigInteger.ZERO);
                    BigInteger newVolume = currentVolume.add(securityChange.getVolumeChange());

                    if (newVolume.compareTo(BigInteger.ZERO) > 0) {
                        securityVolumeMap.put(security, newVolume);
                    } else {
                        securityVolumeMap.remove(security);
                    }
                });

        return securityVolumeMap;
    }
}