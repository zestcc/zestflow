package com.zestflow.executor.chain;

import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainReloadMonitorTest {

    @Mock
    private ChainRepository chainRepo;
    @Mock
    private DesignRepository designRepo;
    @Mock
    private ChainManager chainManager;
    @Mock
    private ChainLoader chainLoader;

    private ChainReloadMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ChainReloadMonitor(chainRepo, designRepo, chainManager, chainLoader);
    }

    @Test
    void pollAndReload_unloadsWhenStatusBelowPublished() {
        ChainPO chain = chain("c1", "d1", 2, "2026-01-01", 1);
        when(chainRepo.list(null, null)).thenReturn(List.of(chain));
        when(chainManager.getActiveCodes()).thenReturn(Set.of("c1"));

        monitor.pollAndReload();

        verify(chainManager).unload("c1");
        verify(chainLoader, never()).reloadFromDatabase(eq("c1"));
    }

    @Test
    void pollAndReload_reloadsWhenFingerprintChanges() {
        ChainPO chain = chain("c1", "d1", 3, "2026-01-01", 1);
        DesignPO design = new DesignPO();
        design.setUpdatedAt("2026-01-02");
        when(chainRepo.list(null, null)).thenReturn(List.of(chain));
        when(chainManager.getActiveCodes()).thenReturn(Set.of());
        when(designRepo.get("d1")).thenReturn(design);
        when(chainLoader.reloadFromDatabase("c1"))
                .thenReturn(new ChainLoader.ChainReloadResult(true, null, 3));

        monitor.pollAndReload();

        verify(chainLoader).reloadFromDatabase("c1");
    }

    @Test
    void pollAndReload_skipsWhenFingerprintUnchanged() {
        ChainPO chain = chain("c1", "d1", 3, "2026-01-01", 1);
        DesignPO design = new DesignPO();
        design.setUpdatedAt("2026-01-02");
        when(chainRepo.list(null, null)).thenReturn(List.of(chain));
        when(chainManager.getActiveCodes()).thenReturn(Set.of());
        when(designRepo.get("d1")).thenReturn(design);
        when(chainLoader.reloadFromDatabase("c1"))
                .thenReturn(new ChainLoader.ChainReloadResult(true, null, 3));

        monitor.pollAndReload();
        monitor.pollAndReload();

        verify(chainLoader).reloadFromDatabase("c1");
    }

    private static ChainPO chain(String code, String designCode, int status, String updatedAt, int version) {
        ChainPO chain = new ChainPO();
        chain.setCode(code);
        chain.setDesignCode(designCode);
        chain.setStatus(status);
        chain.setUpdatedAt(updatedAt);
        chain.setVersion(version);
        return chain;
    }
}
