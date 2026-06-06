package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.model.dto.AiRagDocumentSaveDTO;
import com.zestflow.admin.ai.model.entity.AiRagDocumentPO;
import com.zestflow.admin.ai.model.vo.AiRagDocumentVO;
import com.zestflow.admin.ai.repository.AiRagDocumentMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRagDocumentService {

    private final AiRagDocumentMapper documentMapper;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiProperties aiProperties;
    @Lazy
    private final AiRagService aiRagService;

    public List<AiRagDocumentVO> list(String appCode) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        LambdaQueryWrapper<AiRagDocumentPO> q = baseQuery(tenantId);
        if (StringUtils.hasText(appCode)) {
            q.and(w -> w.isNull(AiRagDocumentPO::getAppCode).or().eq(AiRagDocumentPO::getAppCode, appCode));
        }
        return documentMapper.selectList(q).stream().map(this::toVo).toList();
    }

    public AiRagDocumentVO get(Long id) {
        return toVo(requireOwned(id));
    }

    public AiRagDocumentVO save(AiRagDocumentSaveDTO dto) {
        validateContent(dto.getTitle(), dto.getContent());
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        enforceDocumentLimit(tenantId);

        AiRagDocumentPO po = new AiRagDocumentPO();
        po.setTenantId(tenantId);
        po.setTitle(dto.getTitle().trim());
        po.setAppCode(StringUtils.hasText(dto.getAppCode()) ? dto.getAppCode().trim() : null);
        po.setContent(dto.getContent());
        po.setEnabled(Boolean.FALSE.equals(dto.getEnabled()) ? 0 : 1);
        po.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        po.setSourceType("upload");
        po.setCreatedBy(SecurityUtils.getCurrentUsername());
        po.setUpdatedBy(po.getCreatedBy());
        po.setIsDeleted(0);
        documentMapper.insert(po);
        aiRagService.invalidateTenantIndex(tenantId);
        return toVo(po);
    }

    public AiRagDocumentVO update(Long id, AiRagDocumentSaveDTO dto) {
        validateContent(dto.getTitle(), dto.getContent());
        AiRagDocumentPO po = requireOwned(id);
        po.setTitle(dto.getTitle().trim());
        po.setAppCode(StringUtils.hasText(dto.getAppCode()) ? dto.getAppCode().trim() : null);
        po.setContent(dto.getContent());
        if (dto.getEnabled() != null) {
            po.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        }
        if (dto.getSortOrder() != null) {
            po.setSortOrder(dto.getSortOrder());
        }
        po.setUpdatedBy(SecurityUtils.getCurrentUsername());
        documentMapper.updateById(po);
        aiRagService.invalidateTenantIndex(po.getTenantId());
        return toVo(po);
    }

    public void delete(Long id) {
        AiRagDocumentPO po = requireOwned(id);
        po.setIsDeleted(1);
        documentMapper.updateById(po);
        aiRagService.invalidateTenantIndex(po.getTenantId());
    }

    public void rebuildIndex() {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        aiRagService.invalidateTenantIndex(tenantId);
        aiRagService.warmTenantIndex(tenantId);
    }

    List<AiRagService.IndexedChunk> loadTenantChunks(Long tenantId, String appCode) {
        List<AiRagDocumentPO> docs = documentMapper.selectList(baseQuery(tenantId).eq(AiRagDocumentPO::getEnabled, 1));
        List<AiRagService.IndexedChunk> chunks = docs.stream()
                .filter(doc -> matchesAppScope(doc.getAppCode(), appCode))
                .sorted(Comparator.comparingInt(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()))
                .flatMap(doc -> AiRagIndexEngine.splitMarkdown(doc.getTitle(), doc.getContent()).stream())
                .toList();
        if (aiProperties.isRagTenantFilesystemEnabled()) {
            chunks = Stream.concat(chunks.stream(), loadFilesystemChunks(tenantId).stream()).toList();
        }
        return chunks;
    }

    int countTenantDocuments(Long tenantId) {
        return Math.toIntExact(documentMapper.selectCount(baseQuery(tenantId)));
    }

    private List<AiRagService.IndexedChunk> loadFilesystemChunks(Long tenantId) {
        Path dir = Path.of(aiProperties.getRagTenantDataDir(), String.valueOf(tenantId));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".md"))
                    .sorted()
                    .flatMap(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            return AiRagIndexEngine.splitMarkdown(path.getFileName().toString(), text).stream();
                        } catch (IOException e) {
                            log.warn("读取租户 RAG 文件失败 path={}", path, e);
                            return Stream.empty();
                        }
                    })
                    .toList();
        } catch (IOException e) {
            log.warn("扫描租户 RAG 目录失败 tenantId={} dir={}", tenantId, dir, e);
            return List.of();
        }
    }

    private static boolean matchesAppScope(String docAppCode, String queryAppCode) {
        if (!StringUtils.hasText(docAppCode)) {
            return true;
        }
        return StringUtils.hasText(queryAppCode) && docAppCode.equals(queryAppCode);
    }

    private LambdaQueryWrapper<AiRagDocumentPO> baseQuery(Long tenantId) {
        return new LambdaQueryWrapper<AiRagDocumentPO>()
                .eq(AiRagDocumentPO::getTenantId, tenantId)
                .eq(AiRagDocumentPO::getIsDeleted, 0)
                .orderByAsc(AiRagDocumentPO::getSortOrder)
                .orderByDesc(AiRagDocumentPO::getUpdatedAt);
    }

    private AiRagDocumentPO requireOwned(Long id) {
        AiRagDocumentPO po = documentMapper.selectById(id);
        if (po == null || po.getIsDeleted() != null && po.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!tenantAiConfigService.getCurrentTenantId().equals(po.getTenantId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return po;
    }

    private void validateContent(String title, String content) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (content.length() > aiProperties.getRagTenantMaxContentBytes()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "RAG 文档过大");
        }
    }

    private void enforceDocumentLimit(Long tenantId) {
        if (aiProperties.getRagTenantMaxDocuments() <= 0) {
            return;
        }
        if (countTenantDocuments(tenantId) >= aiProperties.getRagTenantMaxDocuments()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户 RAG 文档数量已达上限");
        }
    }

    private AiRagDocumentVO toVo(AiRagDocumentPO po) {
        return AiRagDocumentVO.builder()
                .id(po.getId())
                .title(po.getTitle())
                .appCode(po.getAppCode())
                .content(po.getContent())
                .enabled(po.getEnabled() == null || po.getEnabled() == 1)
                .sortOrder(po.getSortOrder())
                .sourceType(po.getSourceType())
                .createdBy(po.getCreatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
