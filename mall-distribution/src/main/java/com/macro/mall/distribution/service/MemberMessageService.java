package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.distribution.entity.DmsMemberMessage;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.entity.DmsMessageTemplate;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.MessageUnreadSummaryVO;

import java.util.List;

public interface MemberMessageService {
    void publish(MemberMessageEvent event);
    CommonPage<DmsMemberMessage> list(DmsShopMember member, String category, int pageNum, int pageSize);
    DmsMemberMessage detail(DmsShopMember member, Long id);
    MessageUnreadSummaryVO unread(DmsShopMember member);
    boolean markRead(DmsShopMember member, Long id);
    int markAllRead(DmsShopMember member, String category);
    List<DmsMessageTemplate> listTemplates();
    DmsMessageTemplate updateTemplate(Long id, DmsMessageTemplate input);
    List<DmsMessageChannelConfig> listChannels();
    DmsMessageChannelConfig updateInAppChannel(Long id, boolean enabled);
    CommonPage<DmsMessageDeliveryTask> listDeliveries(String channel, String status, int pageNum, int pageSize);
}
