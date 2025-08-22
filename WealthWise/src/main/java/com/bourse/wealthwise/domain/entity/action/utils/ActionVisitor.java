package com.bourse.wealthwise.domain.entity.action.utils;


import com.bourse.wealthwise.domain.entity.action.*;

public interface ActionVisitor {
    String visit(Buy buy);
    String visit(Sale sale);
    String visit(Deposit deposit);
    String visit(Withdrawal withdrawal);
    String visit(CapitalRaise capitalRaise);
    String visit(StockRightUsage stockRightUsage);
}