package com.gak.fuelstats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gak.fuelstats.domain.FuelPriceSnapshot;
import com.gak.fuelstats.integration.JuheFuelPriceClient;
import com.gak.fuelstats.integration.OnlineFuelPriceQuote;
import com.gak.fuelstats.mapper.FuelPriceSnapshotMapper;
import com.gak.fuelstats.vo.FuelLatestPricesVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FuelPriceServiceTest {

    private FuelPriceSnapshotMapper fuelPriceSnapshotMapper;
    private JuheFuelPriceClient juheFuelPriceClient;
    private FuelPriceService fuelPriceService;

    @BeforeEach
    void setUp() {
        fuelPriceSnapshotMapper = mock(FuelPriceSnapshotMapper.class);
        juheFuelPriceClient = mock(JuheFuelPriceClient.class);
        fuelPriceService = new FuelPriceService(fuelPriceSnapshotMapper, juheFuelPriceClient);
    }

    @Test
    void shouldReturnOnlineRegionalPricesFirst() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 7, 30, 0, 30);
        when(juheFuelPriceClient.findByRegion("北京")).thenReturn(Optional.of(
                new OnlineFuelPriceQuote(
                        "北京",
                        new BigDecimal("7.66"),
                        new BigDecimal("8.15"),
                        new BigDecimal("9.05"),
                        new BigDecimal("7.35"),
                        fetchedAt
                )
        ));

        FuelLatestPricesVO result = fuelPriceService.getLatestPrices("北京");

        assertTrue(result.getOnlineData());
        assertEquals("北京", result.getRegion());
        assertEquals("聚合数据", result.getDataSource());
        assertEquals(new BigDecimal("7.66"), result.getPrices().get("92"));
        assertEquals(fetchedAt, result.getPublishDate());
        verifyNoInteractions(fuelPriceSnapshotMapper);
    }

    @Test
    void shouldUseDefaultRegionSnapshotWhenOnlineServiceIsUnavailable() {
        when(juheFuelPriceClient.findByRegion(FuelPriceService.DEFAULT_REGION))
                .thenReturn(Optional.empty());
        FuelPriceSnapshot snapshot = new FuelPriceSnapshot();
        snapshot.setPublishDate(LocalDateTime.of(2026, 7, 29, 18, 0));
        snapshot.setPrice92(new BigDecimal("7.58"));
        snapshot.setPrice95(new BigDecimal("8.09"));
        snapshot.setPrice98(new BigDecimal("8.96"));
        snapshot.setPriceDiesel(new BigDecimal("7.26"));
        when(fuelPriceSnapshotMapper.selectOne(any())).thenReturn(snapshot);

        FuelLatestPricesVO result = fuelPriceService.getLatestPrices(FuelPriceService.DEFAULT_REGION);

        assertFalse(result.getOnlineData());
        assertEquals("本地快照", result.getDataSource());
        assertEquals(new BigDecimal("8.09"), result.getPrices().get("95"));
    }

    @Test
    void shouldRejectUnsupportedRegion() {
        assertThrows(ResponseStatusException.class, () -> fuelPriceService.getLatestPrices("火星"));
    }

    @Test
    void shouldStillReturnRegionsWhenSnapshotQueryFails() {
        when(juheFuelPriceClient.findByRegion(FuelPriceService.DEFAULT_REGION))
                .thenReturn(Optional.empty());
        when(fuelPriceSnapshotMapper.selectOne(any()))
                .thenThrow(new IllegalStateException("snapshot table unavailable"));

        FuelLatestPricesVO result = fuelPriceService.getLatestPrices(FuelPriceService.DEFAULT_REGION);

        assertFalse(result.getOnlineData());
        assertEquals(31, result.getSupportedRegions().size());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getPrices().get("92"));
    }
}
