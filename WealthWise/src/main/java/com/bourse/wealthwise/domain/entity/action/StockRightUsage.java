package com.bourse.wealthwise.domain.entity.action;

import com.bourse.wealthwise.domain.entity.action.utils.ActionVisitor;
import com.bourse.wealthwise.domain.entity.balance.BalanceChange;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityChange;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SuperBuilder
@Getter
public class StockRightUsage extends BaseAction {
    private final Security rightSecurity;
    private final Security mainSecurity;
    private final BigInteger volumeToConvert;
    private final BigInteger conversionCost;

    @Override
    public List<BalanceChange> getBalanceChanges() {
        return List.of(
                BalanceChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .change_amount(conversionCost.negate())
                        .action(this)
                        .build()
        );
    }

    @Override
    public List<SecurityChange> getSecurityChanges() {
        return Arrays.asList(
                // Remove stock rights
                SecurityChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .security(this.rightSecurity)
                        .volumeChange(this.volumeToConvert.negate())
                        .action(this)
                        .isTradable(true)
                        .build(),
                // Add main shares (initially untradable)
                SecurityChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .security(this.mainSecurity)
                        .volumeChange(this.volumeToConvert)
                        .action(this)
                        .isTradable(false)
                        .build()
        );
    }

    @Override
    public String accept(ActionVisitor visitor) {
        return visitor.visit(this);
    }

    private void setActionType() {
        this.actionType = ActionType.STOCK_RIGHT_USAGE;
    }
}