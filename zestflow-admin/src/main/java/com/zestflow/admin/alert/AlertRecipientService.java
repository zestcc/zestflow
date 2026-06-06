package com.zestflow.admin.alert;

import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertRecipientService {

    private final UserMapper userMapper;

    /** 模块已分配用户邮箱（去重） */
    public List<String> resolveRecipientEmails(Long tenantId, String appCode) {
        return userMapper.selectAppRecipients(tenantId, appCode).stream()
                .map(UserPO::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
