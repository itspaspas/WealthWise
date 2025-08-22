package com.bourse.wealthwise.messaging;

import com.bourse.wealthwise.domain.entity.action.Actor;
import com.bourse.wealthwise.domain.entity.action.CapitalRaise;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.domain.services.PortfolioSharesService;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CapitalRaiseListenerService {

    private final SecurityRepository securityRepository;
    private final PortfolioRepository portfolioRepository;
    private final ActionRepository actionRepository;
    private final PortfolioSharesService portfolioSharesService;

    @JmsListener(destination = "capital.raise.queue")
    public void handleCapitalRaiseAnnouncement(String message) {
        log.info("Received capital raise announcement: {}", message);

        try {
            String[] parts = message.split(" ");
            if (parts.length != 3 || !parts[0].equals("CAPITAL_RAISE")) {
                log.error("Invalid message format: {}", message);
                return;
            }

            String securitySymbol = parts[1];
            double rightPerShare = Double.parseDouble(parts[2]);

            // Find the security by symbol
            Security originalSecurity = findSecurityBySymbol(securitySymbol);
            if (originalSecurity == null) {
                log.error("Security not found for symbol: {}", securitySymbol);
                return;
            }

            String rightSymbol = "H" + securitySymbol;
            Security rightSecurity = findSecurityBySymbol(rightSymbol);
            if (rightSecurity == null) {
                log.error("Stock right security not found for symbol: {}", rightSymbol);
                return;
            }

            // Process all portfolios
            LocalDateTime now = LocalDateTime.now();
            for (Portfolio portfolio : portfolioRepository.findAll()) {
                processPortfolioCapitalRaise(portfolio, originalSecurity, rightSecurity, rightPerShare, now);
            }

            log.info("Capital raise processing completed for security: {}", securitySymbol);

        } catch (Exception e) {
            log.error("Error processing capital raise announcement: " + message, e);
        }
    }

    private void processPortfolioCapitalRaise(Portfolio portfolio, Security originalSecurity,
                                              Security rightSecurity, double rightPerShare,
                                              LocalDateTime dateTime) {
        Map<Security, BigInteger> holdings = portfolioSharesService.getSecurityVolumes(
                portfolio.getUuid(), dateTime);

        BigInteger originalVolume = holdings.get(originalSecurity);
        if (originalVolume == null || originalVolume.compareTo(BigInteger.ZERO) <= 0) {
            // Portfolio doesn't hold this security
            return;
        }

        BigInteger rightVolume = CapitalRaise.calculateRightVolume(originalVolume, rightPerShare);

        if (rightVolume.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(dateTime)
                .tracing_number("CR-" + UUID.randomUUID().toString())
                .actor(Actor.PUBLISHER)
                .originalSecurity(originalSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(rightVolume)
                .rightPerShare(rightPerShare)
                .build();

        actionRepository.save(capitalRaise);

        log.info("Allocated {} rights of {} to portfolio {} (original holding: {})",
                rightVolume, rightSecurity.getSymbol(), portfolio.getUuid(), originalVolume);
    }

}