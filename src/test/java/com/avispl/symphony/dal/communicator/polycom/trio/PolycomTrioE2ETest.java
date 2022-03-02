/*
 * Copyright (c) 2018-2022 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.polycom.trio;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class PolycomTrioE2ETest {

    PolycomTrio polycomTrio;

    @Before
    public void setUp() throws Exception {
        polycomTrio = new PolycomTrio();
        polycomTrio.setHost("***REMOVED***");
        polycomTrio.setPort(443);
        polycomTrio.setProtocol("https");
        polycomTrio.setPassword("1234");
        polycomTrio.setLogin("Polycom");
        polycomTrio.init();
    }

    @Test
    public void testGetMultipleStatistics () throws Exception {
        List<Statistics> statisticsList = polycomTrio.getMultipleStatistics();
        ExtendedStatistics extendedStatistics = (ExtendedStatistics) statisticsList.stream().filter(statistics -> statistics instanceof ExtendedStatistics).findAny().get();

        Assert.assertNotNull(extendedStatistics);
        Assert.assertNotNull(extendedStatistics.getStatistics());
        Assert.assertEquals("hardwareEndpoint", extendedStatistics.getStatistics().get("DeviceInfo#DeviceType"));
        Assert.assertEquals("Polycom", extendedStatistics.getStatistics().get("DeviceInfo#DeviceVendor"));
        Assert.assertEquals("Idle", extendedStatistics.getStatistics().get("DeviceStatus#State"));
    }

    @Test
    public void testReboot() throws Exception {
        List<Statistics> statisticsList = polycomTrio.getMultipleStatistics();
        ExtendedStatistics extendedStatistics = (ExtendedStatistics) statisticsList.stream().filter(statistics -> statistics instanceof ExtendedStatistics).findAny().get();
        Assert.assertFalse(extendedStatistics.getStatistics().get("DeviceInfo#Uptime").startsWith("0 Day 0:00:"));
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setProperty("RebootDevice");
        polycomTrio.controlProperty(controllableProperty);
        Thread.sleep(30000L);
        statisticsList = polycomTrio.getMultipleStatistics();
        extendedStatistics = (ExtendedStatistics) statisticsList.stream().filter(statistics -> statistics instanceof ExtendedStatistics).findAny().get();
        Assert.assertTrue(extendedStatistics.getStatistics().get("DeviceInfo#Uptime").startsWith("0 Day 0:00:"));
    }

    @Test
    public void testRestart() throws Exception {
        List<Statistics> statisticsList = polycomTrio.getMultipleStatistics();
        ExtendedStatistics extendedStatistics = (ExtendedStatistics) statisticsList.stream().filter(statistics -> statistics instanceof ExtendedStatistics).findAny().get();
        Assert.assertFalse(extendedStatistics.getStatistics().get("DeviceInfo#Uptime").startsWith("0 Day 0:00:"));
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setProperty("RestartDevice");
        polycomTrio.controlProperty(controllableProperty);
        Thread.sleep(30000L);
        statisticsList = polycomTrio.getMultipleStatistics();
        extendedStatistics = (ExtendedStatistics) statisticsList.stream().filter(statistics -> statistics instanceof ExtendedStatistics).findAny().get();
        Assert.assertTrue(extendedStatistics.getStatistics().get("DeviceInfo#Uptime").startsWith("0 Day 0:00:"));

    }

    private void testDial() {}
    private void testMute() {}
}
