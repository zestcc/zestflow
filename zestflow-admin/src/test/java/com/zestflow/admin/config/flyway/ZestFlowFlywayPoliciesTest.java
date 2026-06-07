package com.zestflow.admin.config.flyway;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class ZestFlowFlywayPoliciesTest {

    @Test
    void nonProductionPolicy_allowsOutOfOrderAndSkipsValidate() {
        FluentConfiguration configuration = Mockito.mock(FluentConfiguration.class);
        Mockito.when(configuration.outOfOrder(true)).thenReturn(configuration);
        Mockito.when(configuration.validateOnMigrate(false)).thenReturn(configuration);

        ZestFlowFlywayPolicies.applyNonProductionPolicy(configuration);

        verify(configuration).outOfOrder(true);
        verify(configuration).validateOnMigrate(false);
    }

    @Test
    void productionPolicy_strictOrderAndValidate() {
        FluentConfiguration configuration = Mockito.mock(FluentConfiguration.class);
        Mockito.when(configuration.outOfOrder(false)).thenReturn(configuration);
        Mockito.when(configuration.validateOnMigrate(true)).thenReturn(configuration);

        ZestFlowFlywayPolicies.applyProductionPolicy(configuration);

        verify(configuration).outOfOrder(false);
        verify(configuration).validateOnMigrate(true);
    }
}
