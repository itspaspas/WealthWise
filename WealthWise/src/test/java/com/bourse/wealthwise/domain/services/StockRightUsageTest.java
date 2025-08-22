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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockRightUsageTest {

    @Autowired
    private ActionRepository actionRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private SecurityRepository securityRepository;
    @Autowired
    private BalanceActionService balanceService;

    private Portfolio portfolio;
    private Security mainSecurity, rightSecurity;

    @BeforeEach
    void setUp() {
        actionRepository.deleteAll();
        portfolioRepository.deleteAll();
        securityRepository.clear();

        User user = User.builder().uuid("user1").build();
        portfolio = new Portfolio("test-portfolio", user, "Test Portfolio");
        portfolioRepository.save(portfolio);

        mainSecurity = Security.builder()
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

        securityRepository.addSecurity(mainSecurity);
        securityRepository.addSecurity(rightSecurity);
    }

    @Test
    void givenPortfolioWithStockRights_whenUsingRights_thenRightsConvertedToShares() {
        // Given: Portfolio has stock rights and sufficient balance
        Deposit deposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(2))
                .amount(BigInteger.valueOf(10000))
                .actionType(ActionType.DEPOSIT)
                .build();

        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(1))
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(100))
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();

        actionRepository.save(deposit);
        actionRepository.save(capitalRaise);

        // When: Using 50 stock rights
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(50))
                .conversionCost(BigInteger.valueOf(5000)) // 50 * 100 Tomans
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        // Then: Rights are reduced and main shares are increased
        BigInteger remainingRights = getRemainingVolume(rightSecurity);
        BigInteger mainShares = getRemainingVolume(mainSecurity);

        assertEquals(BigInteger.valueOf(50), remainingRights);
        assertEquals(BigInteger.valueOf(50), mainShares);
    }

    @Test
    void givenStockRightUsage_whenCheckingBalance_thenBalanceIsReduced() {
        // Given: Portfolio with balance and rights
        LocalDateTime now = LocalDateTime.now();

        Deposit deposit = Deposit.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(now.minusHours(2))
                .amount(BigInteger.valueOf(10000))
                .actionType(ActionType.DEPOSIT)
                .build();

        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(now.minusHours(1))
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(100))
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();

        actionRepository.save(deposit);
        actionRepository.save(capitalRaise);

        BigInteger balanceBefore = balanceService.getBalanceForPortfolio(portfolio.getUuid(), now.minusMinutes(1));

        // When: Using rights with cost
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(now)
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(30))
                .conversionCost(BigInteger.valueOf(3000)) // 30 * 100 Tomans
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        // Then: Balance is reduced by conversion cost
        BigInteger balanceAfter = balanceService.getBalanceForPortfolio(portfolio.getUuid(), now.plusMinutes(1));
        assertEquals(balanceBefore.subtract(BigInteger.valueOf(3000)), balanceAfter);
    }

    @Test
    void givenZeroRightsUsage_whenConverting_thenNoChanges() {
        // Given: Portfolio with rights
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(1))
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(100))
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        // When: Using zero rights
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.ZERO)
                .conversionCost(BigInteger.ZERO)
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        // Then: No changes in volumes
        assertEquals(BigInteger.valueOf(100), getRemainingVolume(rightSecurity));
        assertEquals(BigInteger.ZERO, getRemainingVolume(mainSecurity));
    }

    @Test
    void givenMultipleStockRightUsages_whenConverting_thenAllUsagesApplied() {
        // Given: Portfolio with rights
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(2))
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(100))
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        // When: Multiple right usages
        StockRightUsage usage1 = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(1))
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(30))
                .conversionCost(BigInteger.valueOf(3000))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();

        StockRightUsage usage2 = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusMinutes(30))
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(25))
                .conversionCost(BigInteger.valueOf(2500))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();

        actionRepository.save(usage1);
        actionRepository.save(usage2);

        // Then: All usages are applied (100 - 30 - 25 = 45 rights remaining, 30 + 25 = 55 main shares)
        assertEquals(BigInteger.valueOf(45), getRemainingVolume(rightSecurity));
        assertEquals(BigInteger.valueOf(55), getRemainingVolume(mainSecurity));
    }

    @Test
    void givenStockRightUsageWithLargeVolume_whenConverting_thenCorrectCalculations() {
        // Given: Portfolio with large volume of rights
        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now().minusHours(1))
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(1000000)) // 1 million rights
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();
        actionRepository.save(capitalRaise);

        // When: Using large volume of rights
        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(LocalDateTime.now())
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(500000)) // 500k rights
                .conversionCost(BigInteger.valueOf(50000000)) // 500k * 100 Tomans
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();
        actionRepository.save(usage);

        // Then: Calculations are correct for large volumes
        assertEquals(BigInteger.valueOf(500000), getRemainingVolume(rightSecurity));
        assertEquals(BigInteger.valueOf(500000), getRemainingVolume(mainSecurity));
    }

    @Test
    void givenStockRightUsageOnDifferentDates_whenGettingVolumesAtSpecificDate_thenCorrectVolumes() {
        // Given: Portfolio with rights and usage at different times
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1);

        CapitalRaise capitalRaise = CapitalRaise.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime)
                .originalSecurity(mainSecurity)
                .rightSecurity(rightSecurity)
                .rightVolume(BigInteger.valueOf(100))
                .rightPerShare(1.0)
                .actionType(ActionType.CAPITAL_RAISE)
                .build();

        StockRightUsage usage = StockRightUsage.builder()
                .uuid(UUID.randomUUID().toString())
                .portfolio(portfolio)
                .datetime(baseTime.plusHours(12))
                .rightSecurity(rightSecurity)
                .mainSecurity(mainSecurity)
                .volumeToConvert(BigInteger.valueOf(40))
                .conversionCost(BigInteger.valueOf(4000))
                .actionType(ActionType.STOCK_RIGHT_USAGE)
                .build();

        actionRepository.save(capitalRaise);
        actionRepository.save(usage);

        // When: Getting volumes at time before usage
        BigInteger rightsBeforeUsage = getVolumeAtTime(rightSecurity, baseTime.plusHours(6));
        BigInteger mainSharesBeforeUsage = getVolumeAtTime(mainSecurity, baseTime.plusHours(6));

        // Then: Before usage, only rights exist
        assertEquals(BigInteger.valueOf(100), rightsBeforeUsage);
        assertEquals(BigInteger.ZERO, mainSharesBeforeUsage);

        // When: Getting volumes at time after usage
        BigInteger rightsAfterUsage = getVolumeAtTime(rightSecurity, baseTime.plusHours(18));
        BigInteger mainSharesAfterUsage = getVolumeAtTime(mainSecurity, baseTime.plusHours(18));

        // Then: After usage, rights are reduced and main shares increased
        assertEquals(BigInteger.valueOf(60), rightsAfterUsage);
        assertEquals(BigInteger.valueOf(40), mainSharesAfterUsage);
    }

    private BigInteger getRemainingVolume(Security security) {
        return actionRepository.findAllActionsOf(portfolio.getUuid()).stream()
                .flatMap(action -> action.getSecurityChanges().stream())
                .filter(sc -> sc.getSecurity().equals(security))
                .map(sc -> sc.getVolumeChange())
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private BigInteger getVolumeAtTime(Security security, LocalDateTime time) {
        return actionRepository.findAllActionsOf(portfolio.getUuid()).stream()
                .filter(action -> action.getDatetime() != null && !action.getDatetime().isAfter(time))
                .flatMap(action -> action.getSecurityChanges().stream())
                .filter(sc -> sc.getSecurity().equals(security))
                .map(sc -> sc.getVolumeChange())
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}