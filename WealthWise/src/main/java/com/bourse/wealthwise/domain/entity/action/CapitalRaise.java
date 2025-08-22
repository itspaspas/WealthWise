package com.bourse.wealthwise.domain.entity.action;

import com.bourse.wealthwise.domain.entity.action.utils.ActionVisitor;
import com.bourse.wealthwise.domain.entity.balance.BalanceChange;
import com.bourse.wealthwise.domain.entity.security.Security;
import com.bourse.wealthwise.domain.entity.security.SecurityChange;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@SuperBuilder
@Getter
public class CapitalRaise extends BaseAction {
    private final Security originalSecurity;
    private final Security rightSecurity;
    private final BigInteger rightVolume;
    private final double rightPerShare;

    @Override
    public List<BalanceChange> getBalanceChanges() {
        return List.of();
    }

    @Override
    public List<SecurityChange> getSecurityChanges() {
        return List.of(
                SecurityChange.builder()
                        .uuid(UUID.randomUUID())
                        .portfolio(this.portfolio)
                        .datetime(this.datetime)
                        .security(this.rightSecurity)
                        .volumeChange(this.rightVolume)
                        .action(this)
                        .isTradable(true)
                        .build()
        );
    }

    @Override
    public String accept(ActionVisitor visitor) {
        return visitor.visit(this);
    }

    private void setActionType() {
        this.actionType = ActionType.CAPITAL_RAISE;
    }

    public static BigInteger calculateRightVolume(BigInteger originalVolume, double rightPerShare) {
        double rightAmount = originalVolume.doubleValue() * rightPerShare;
        return BigInteger.valueOf((long) Math.floor(rightAmount));
    }
}