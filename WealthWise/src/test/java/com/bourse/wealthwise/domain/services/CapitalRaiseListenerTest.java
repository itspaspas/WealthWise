package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CapitalRaiseListenerAdvancedTest {

    @Autowired
    private CapitalRaiseListenerService listener;
    @Autowired
    private ActionRepository actionRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private SecurityRepository securityRepository;

    private Portfolio portfolio1, portfolio2, emptyPortfolio;
    private Security testSecurity, rightSecurity;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        portfolioRepository.deleteAll();
        securityRepository.clear();

        // Setup securities
        testSecurity = Security.builder()
                .name("Test Company")
                .symbol("TEST")
                .isin("TEST001")
                .securityType(SecurityType.STOCK)
                .build();

        rightSecurity = Security.builder()
                .name("Test Company Rights")
                .symbol("HTEST")
                .isin("HTEST001")
                .securityType(SecurityType.STOCK_RIGHT)
                .build();

        securityRepository.addSecurity(testSecurity);
        securityRepository.addSecurity(rightSecurity);

        // Setup portfolios
        User user = User.builder().uuid("user1").build();
        portfolio1 = new Portfolio("port1", user, "Portfolio 1");
        portfolio2 = new Portfolio("port2", user, "Portfolio 2");
        emptyPortfolio = new Portfolio("empty", user, "Empty Portfolio");

        portfolioRepository.save(portfolio1);
        portfolioRepository.save(portfolio2);
        portfolioRepository.save(emptyPortfolio);

        // Add initial holdings
        Buy buy1 = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio1)
                .datetime(LocalDateTime.now().minusDays(1))
                .security(testSecurity)
                .volume(BigInteger.valueOf(1000))
                .price(10)
                .totalValue(BigInteger.valueOf(10000))
                .actionType(ActionType.BUY)
                .build();

        Buy buy2 = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio2)
                .datetime(LocalDateTime.now().minusDays(1))
                .security(testSecurity)
                .volume(BigInteger.valueOf(500))
                .price(10)
                .totalValue(BigInteger.valueOf(5000))
                .actionType(ActionType.BUY)
                .build();

        actionRepository.save(buy1);
        actionRepository.save(buy2);
    }

    @Test
    void givenValidCapitalRaiseMessage_whenProcessing_thenAllPortfoliosGetRights() {
        // When: Capital raise message is processed
        listener.handleCapitalRaiseAnnouncement("CAPITAL_RAISE TEST 0.5");

        // Then: All portfolios with holdings get appropriate rights
        List<BaseAction> portfolio1Actions = actionRepository.findAllActionsOf("port1");
        List<BaseAction> portfolio2Actions = actionRepository.findAllActionsOf("port2");
        List<BaseAction> emptyPortfolioActions = actionRepository.findAllActionsOf("empty");

        // Portfolio 1: 1000 shares * 0.5 = 500 rights
        CapitalRaise capitalRaise1 = (CapitalRaise) portfolio1Actions.stream()
                .filter(a -> a instanceof CapitalRaise)
                .findFirst()
                .orElse(null);
        assertNotNull(capitalRaise1);
        assertEquals(BigInteger.valueOf(500), capitalRaise1.getRightVolume());

        // Portfolio 2: 500 shares * 0.5 = 250 rights
        CapitalRaise capitalRaise2 = (CapitalRaise) portfolio2Actions.stream()
                .filter(a -> a instanceof CapitalRaise)
                .findFirst()
                .orElse(null);
        assertNotNull(capitalRaise2);
        assertEquals(BigInteger.valueOf(250), capitalRaise2.getRightVolume());

        // Empty portfolio: no capital raise action
        boolean hasCapitalRaise = emptyPortfolioActions.stream()
                .anyMatch(a -> a instanceof CapitalRaise);
        assertFalse(hasCapitalRaise);
    }

    @Test
    void givenFractionalRights_whenProcessing_thenRightsAreFloored() {
        // Given: Portfolio with 7 shares
        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(emptyPortfolio)
                .datetime(LocalDateTime.now().minusDays(1))
                .security(testSecurity)
                .volume(BigInteger.valueOf(7))
                .price(10)
                .totalValue(BigInteger.valueOf(70))
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buy);

        // When: Capital raise with 0.9 ratio (7 * 0.9 = 6.3)
        listener.handleCapitalRaiseAnnouncement("CAPITAL_RAISE TEST 0.9");

        // Then: Rights are floored to 6
        List<BaseAction> actions = actionRepository.findAllActionsOf("empty");
        CapitalRaise capitalRaise = (CapitalRaise) actions.stream()
                .filter(a -> a instanceof CapitalRaise)
                .findFirst()
                .orElse(null);
        assertNotNull(capitalRaise);
        assertEquals(BigInteger.valueOf(6), capitalRaise.getRightVolume());
    }

    @Test
    void givenInvalidMessageFormat_whenProcessing_thenNoActionsCreated() {
        // Given: Initial action count
        int initialActionCount = actionRepository.findAllActionsOf("port1").size();

        // When: Invalid message format is processed
        listener.handleCapitalRaiseAnnouncement("INVALID_MESSAGE TEST 0.5");

        // Then: No new actions are created
        int finalActionCount = actionRepository.findAllActionsOf("port1").size();
        assertEquals(initialActionCount, finalActionCount);
    }

    @Test
    void givenNonExistentSecurity_whenProcessing_thenNoActionsCreated() {
        // Given: Initial action count
        int initialActionCount = actionRepository.findAllActionsOf("port1").size();

        // When: Message for non-existent security is processed
        listener.handleCapitalRaiseAnnouncement("CAPITAL_RAISE NONEXISTENT 0.5");

        // Then: No new actions are created
        int finalActionCount = actionRepository.findAllActionsOf("port1").size();
        assertEquals(initialActionCount, finalActionCount);
    }
}
