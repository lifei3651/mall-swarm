package com.macro.mall.distribution.notification;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsMessageDeliveryAttemptDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryReceiptDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryTaskDao;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.entity.DmsMessageDeliveryReceipt;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationReceiptService {
    private static final Set<String> ALLOWED = Set.of("ACCEPTED","DELIVERED","PERMANENT");
    private final DmsMessageDeliveryTaskDao taskDao;
    private final DmsMessageDeliveryAttemptDao attemptDao;
    private final DmsMessageDeliveryReceiptDao receiptDao;
    private final Map<String,ExternalNotificationAdapter> adapters;

    public NotificationReceiptService(DmsMessageDeliveryTaskDao taskDao,DmsMessageDeliveryAttemptDao attemptDao,
                                      DmsMessageDeliveryReceiptDao receiptDao,List<ExternalNotificationAdapter> adapters) {
        this.taskDao=taskDao; this.attemptDao=attemptDao; this.receiptDao=receiptDao;
        Map<String,ExternalNotificationAdapter> mapped=new HashMap<>();
        for (ExternalNotificationAdapter adapter:adapters) mapped.put(adapter.channel()+":"+adapter.providerCode(),adapter);
        this.adapters=Map.copyOf(mapped);
    }

    @Transactional
    public boolean accept(Long tenantId,String channel,String provider,Map<String,String> headers,byte[] body) {
        if (tenantId==null||tenantId<=0||body==null||body.length==0||body.length>16384) Asserts.fail("回执请求不正确");
        ExternalNotificationAdapter adapter=adapters.get(channel+":"+provider);
        if (adapter==null||!adapter.verifyReceipt(headers,body)) Asserts.fail("回执验签失败");
        NotificationReceipt parsed=adapter.parseReceipt(body);
        if (parsed==null||parsed.taskId()==null||parsed.taskId()<=0||!safeId(parsed.receiptId())||!ALLOWED.contains(parsed.status())) Asserts.fail("回执内容不正确");
        DmsMessageDeliveryTask task=taskDao.selectById(parsed.taskId());
        if (task==null||!tenantId.equals(task.getTenantId())||!channel.equals(task.getChannel())) Asserts.fail("回执任务不存在");
        DmsMessageDeliveryReceipt receipt=new DmsMessageDeliveryReceipt();
        receipt.setTenantId(tenantId); receipt.setChannel(channel); receipt.setProviderCode(provider);
        receipt.setReceiptId(parsed.receiptId()); receipt.setTaskId(parsed.taskId()); receipt.setPayloadDigest(digest(body));
        receipt.setSignatureValid(1); receipt.setReceiptStatus(parsed.status()); receipt.setErrorCode(safeCode(parsed.errorCode()));
        if (receiptDao.insertIgnore(receipt)==0) return true;
        LocalDateTime now=LocalDateTime.now();
        taskDao.applyReceipt(tenantId,task.getId(),parsed.status(),provider,safeId(parsed.providerMessageId())?parsed.providerMessageId():null,safeCode(parsed.errorCode()),now);
        DmsMessageDeliveryAttempt latest=attemptDao.selectLatest(tenantId,task.getId());
        if (latest!=null) attemptDao.updateResult(tenantId,latest.getId(),parsed.status(),
                safeId(parsed.providerMessageId())?parsed.providerMessageId():latest.getProviderMessageId(),
                latest.getActualCost()==null?BigDecimal.ZERO:latest.getActualCost(),safeCode(parsed.errorCode()),
                "PERMANENT".equals(parsed.status())?"供应商回执确认发送失败":null,now);
        return true;
    }
    private boolean safeId(String value) { return value!=null&&!value.isBlank()&&value.length()<=128&&value.matches("[A-Za-z0-9_.:-]+"); }
    private String safeCode(String value) { if (value==null||value.isBlank()) return null; String safe=value.replaceAll("[^A-Za-z0-9_.-]","_"); return safe.substring(0,Math.min(64,safe.length())); }
    private String digest(byte[] body) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}catch(Exception ignored){throw new IllegalStateException("SHA-256不可用");} }
}
