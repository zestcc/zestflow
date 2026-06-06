package com.zestflow.admin.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.AlertCooldownPO;
import com.zestflow.admin.repository.AlertCooldownMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlertCooldownService {

    private final AlertCooldownMapper alertCooldownMapper;

    public static String buildKey(Long tenantId, String appCode, AlertRule rule) {
        return tenantId + ":" + appCode + ":" + rule.name();
    }

    public boolean shouldSend(String alertKey, int cooldownMinutes) {
        AlertCooldownPO existing = alertCooldownMapper.selectOne(
                new LambdaQueryWrapper<AlertCooldownPO>().eq(AlertCooldownPO::getAlertKey, alertKey));
        if (existing == null || existing.getLastSentAt() == null) {
            return true;
        }
        LocalDateTime nextAllowed = existing.getLastSentAt()
                .plusMinutes(Math.max(1, cooldownMinutes));
        return !LocalDateTime.now().isBefore(nextAllowed);
    }

    public void markSent(String alertKey) {
        LocalDateTime now = LocalDateTime.now();
        AlertCooldownPO existing = alertCooldownMapper.selectOne(
                new LambdaQueryWrapper<AlertCooldownPO>().eq(AlertCooldownPO::getAlertKey, alertKey));
        if (existing == null) {
            AlertCooldownPO created = new AlertCooldownPO();
            created.setAlertKey(alertKey);
            created.setLastSentAt(now);
            alertCooldownMapper.insert(created);
            return;
        }
        existing.setLastSentAt(now);
        alertCooldownMapper.updateById(existing);
    }
}
