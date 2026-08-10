package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.service.PaymentPasswordAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentPasswordAttemptServiceImpl implements PaymentPasswordAttemptService {

    private final DmsShopMemberDao memberDao;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long memberId, int lockThreshold) {
        memberDao.increaseFailedPayPassword(memberId, lockThreshold);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean clearIfUnchanged(Long memberId, int expectedFailedCount) {
        return memberDao.clearPayPasswordLockIfCount(memberId, expectedFailedCount) > 0;
    }
}
