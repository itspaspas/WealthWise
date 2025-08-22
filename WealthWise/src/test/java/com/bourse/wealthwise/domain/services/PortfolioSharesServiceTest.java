package com.bourse.wealthwise.domain.services;

import com.bourse.wealthwise.domain.entity.account.User;
import com.bourse.wealthwise.domain.entity.action.*;
import com.bourse.wealthwise.domain.entity.portfolio.Portfolio;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityType;
import com.bourse.wealthwise.repository.ActionRepository;
import com.bourse.wealthwise.repository.PortfolioRepository;
import com.bourse.wealthwise.repository.SecurityPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PortfolioSharesServiceTest {

    @Autowired
    private PortfolioSharesService portfolioSharesService;
    @Autowired
    private ActionRepository actionRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private SecurityPriceRepository securityPriceRepository;

    private Portfolio portfolio;
    private Security appleStock, microsoftStock;

    @BeforeEach
    void setUp() {
        // Given: Clean repositories
        actionRepository.deleteAll();
        portfolioRepository.deleteAll();
        securityPriceRepository.clear();

        // Given: A portfolio exists
        User user = User.builder().uuid("user1").firstName("John").lastName("Doe").build();
        portfolio = new Portfolio("portfolio-123", user, "Test Portfolio");
        portfolioRepository.save(portfolio);

        // Given: Securities exist with prices
        appleStock = Security.builder()
                .name("Apple Inc")
                .symbol("AAPL")
                .isin("US0378331005")
                .securityType(SecurityType.STOCK)
                .build();

        microsoftStock = Security.builder()
                .name("Microsoft Corp")
                .symbol("MSFT")
                .isin("US5949181045")
                .securityType(SecurityType.STOCK)
                .build();

        securityPriceRepository.addPrice("US0378331005", LocalDate.now(), 150.0);
        securityPriceRepository.addPrice("US5949181045", LocalDate.now(), 300.0);
    }

    @Test
    void givenEmptyPortfolio_whenGettingShares_thenReturnEmptyList() {
        // When: Getting portfolio shares for empty portfolio
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: Empty list is returned
        assertTrue(holdings.isEmpty());
    }

    @Test
    void givenPortfolioWithOneBuyAction_whenGettingShares_thenReturnCorrectHolding() {
        // Given: A buy action exists
        Buy buyAction = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusMinutes(30))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(150)
                .totalValue(BigInteger.valueOf(15000))
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buyAction);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: One holding with correct values is returned
        assertEquals(1, holdings.size());
        PortfolioSharesService.SecurityHolding holding = holdings.get(0);
        assertEquals(appleStock, holding.getSecurity());
        assertEquals(BigInteger.valueOf(100), holding.getVolume());
        assertEquals(15000.0, holding.getValue());
    }

    @Test
    void givenMultipleBuyAndSellActions_whenGettingShares_thenReturnNetPosition() {
        // Given: Multiple buy and sell actions
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

        Buy buy1 = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime)
                .security(appleStock)
                .volume(BigInteger.valueOf(200))
                .price(150)
                .totalValue(BigInteger.valueOf(30000))
                .actionType(ActionType.BUY)
                .build();

        Buy buy2 = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusMinutes(30))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(155)
                .totalValue(BigInteger.valueOf(15500))
                .actionType(ActionType.BUY)
                .build();

        Sale sell1 = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusHours(1))
                .security(appleStock)
                .volume(BigInteger.valueOf(50))
                .price(160)
                .totalValue(BigInteger.valueOf(8000))
                .actionType(ActionType.SALE)
                .build();

        actionRepository.save(buy1);
        actionRepository.save(buy2);
        actionRepository.save(sell1);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: Net position is correct (200 + 100 - 50 = 250)
        assertEquals(1, holdings.size());
        assertEquals(BigInteger.valueOf(250), holdings.get(0).getVolume());
        assertEquals(37500.0, holdings.get(0).getValue()); // 250 * 150
    }

    @Test
    void givenMultipleSecurities_whenGettingShares_thenReturnSortedByName() {
        // Given: Holdings in multiple securities
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);

        Buy buyMicrosoft = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime)
                .security(microsoftStock)
                .volume(BigInteger.valueOf(50))
                .price(300)
                .totalValue(BigInteger.valueOf(15000))
                .actionType(ActionType.BUY)
                .build();

        Buy buyApple = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusMinutes(10))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(150)
                .totalValue(BigInteger.valueOf(15000))
                .actionType(ActionType.BUY)
                .build();

        actionRepository.save(buyMicrosoft);
        actionRepository.save(buyApple);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: Holdings are sorted by security name (Apple Inc comes before Microsoft Corp)
        assertEquals(2, holdings.size());
        assertEquals("Apple Inc", holdings.get(0).getSecurity().getName());
        assertEquals("Microsoft Corp", holdings.get(1).getSecurity().getName());
    }

    @Test
    void givenActionsAfterQueryTime_whenGettingShares_thenIgnoreFutureActions() {
        // Given: Actions before and after query time
        LocalDateTime queryTime = LocalDateTime.now();

        Buy pastBuy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(queryTime.minusHours(1))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(150)
                .totalValue(BigInteger.valueOf(15000))
                .actionType(ActionType.BUY)
                .build();

        Buy futureBuy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(queryTime.plusHours(1))
                .security(appleStock)
                .volume(BigInteger.valueOf(200))
                .price(160)
                .totalValue(BigInteger.valueOf(32000))
                .actionType(ActionType.BUY)
                .build();

        actionRepository.save(pastBuy);
        actionRepository.save(futureBuy);

        // When: Getting portfolio shares at query time
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", queryTime);

        // Then: Only past actions are considered
        assertEquals(1, holdings.size());
        assertEquals(BigInteger.valueOf(100), holdings.get(0).getVolume());
    }

    @Test
    void givenInvalidPortfolio_whenGettingShares_thenThrowException() {
        // When & Then: Getting shares for non-existent portfolio throws exception
        assertThrows(IllegalArgumentException.class, () ->
                portfolioSharesService.getPortfolioSharesAtDateTime("invalid-id", LocalDateTime.now()));
    }

    @Test
    void givenSecurityWithoutPrice_whenGettingShares_thenReturnNullValue() {
        // Given: Security without price data
        Security unknownStock = Security.builder()
                .name("Unknown Corp")
                .symbol("UNK")
                .isin("US9999999999")
                .securityType(SecurityType.STOCK)
                .build();

        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusMinutes(30))
                .security(unknownStock)
                .volume(BigInteger.valueOf(50))
                .price(100)
                .totalValue(BigInteger.valueOf(5000))
                .actionType(ActionType.BUY)
                .build();
        actionRepository.save(buy);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: Value is null for security without price
        assertEquals(1, holdings.size());
        assertNull(holdings.get(0).getValue());
    }

    @Test
    void givenPortfolioWithCompletelyLiquidatedPosition_whenGettingShares_thenSecurityNotInResults() {
        // Given: Buy and then sell all shares
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);

        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime)
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(150)
                .totalValue(BigInteger.valueOf(15000))
                .actionType(ActionType.BUY)
                .build();

        Sale sellAll = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusMinutes(30))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(160)
                .totalValue(BigInteger.valueOf(16000))
                .actionType(ActionType.SALE)
                .build();

        actionRepository.save(buy);
        actionRepository.save(sellAll);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: No holdings are returned as position is completely liquidated
        assertTrue(holdings.isEmpty());
    }

    @Test
    void givenPortfolioWithOversoldPosition_whenGettingShares_thenSecurityNotInResults() {
        // Given: Sell more than owned (creating negative position)
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);

        Buy buy = Buy.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime)
                .security(appleStock)
                .volume(BigInteger.valueOf(50))
                .price(150)
                .totalValue(BigInteger.valueOf(7500))
                .actionType(ActionType.BUY)
                .build();

        Sale oversell = Sale.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusMinutes(30))
                .security(appleStock)
                .volume(BigInteger.valueOf(100))
                .price(160)
                .totalValue(BigInteger.valueOf(16000))
                .actionType(ActionType.SALE)
                .build();

        actionRepository.save(buy);
        actionRepository.save(oversell);

        // When: Getting portfolio shares
        List<PortfolioSharesService.SecurityHolding> holdings =
                portfolioSharesService.getPortfolioSharesAtDateTime("portfolio-123", LocalDateTime.now());

        // Then: No holdings are returned as negative positions are excluded
        assertTrue(holdings.isEmpty());
    }
}