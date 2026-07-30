package com.gak.fuelstats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.fuelstats.domain.FuelPriceSnapshot;
import com.gak.fuelstats.integration.JuheFuelPriceClient;
import com.gak.fuelstats.integration.OnlineFuelPriceQuote;
import com.gak.fuelstats.mapper.FuelPriceSnapshotMapper;
import com.gak.fuelstats.vo.FuelLatestPricesVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 最新油价查询与第三方降级服务。
 */
@Service
public class FuelPriceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FuelPriceService.class);
    public static final String DEFAULT_REGION = "重庆";
    private static final int MONEY_SCALE = 2;
    private static final String LIMIT_ONE_SQL = "LIMIT 1";
    private static final String ONLINE_SOURCE = "聚合数据";
    private static final String LOCAL_SOURCE = "本地快照";
    private static final String ONLINE_NOTICE = "省级参考价，实际价格以加油站当日挂牌价为准。";
    private static final String LOCAL_NOTICE = "实时油价暂不可用，当前显示本地参考快照。";
    private static final String EMPTY_NOTICE = "实时油价暂不可用，当前地区暂无可用快照。";
    private static final List<String> SUPPORTED_REGIONS = List.of(
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
            "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆"
    );

    private final FuelPriceSnapshotMapper fuelPriceSnapshotMapper;
    private final JuheFuelPriceClient juheFuelPriceClient;

    public FuelPriceService(FuelPriceSnapshotMapper fuelPriceSnapshotMapper,
                            JuheFuelPriceClient juheFuelPriceClient) {
        this.fuelPriceSnapshotMapper = fuelPriceSnapshotMapper;
        this.juheFuelPriceClient = juheFuelPriceClient;
    }

    /**
     * 优先返回在线省级油价，第三方不可用时仅为默认地区提供原有本地快照。
     */
    public FuelLatestPricesVO getLatestPrices(String requestedRegion) {
        String region = normalizeRegion(requestedRegion);
        Optional<OnlineFuelPriceQuote> onlineQuote = juheFuelPriceClient.findByRegion(region);
        if (onlineQuote.isPresent()) {
            return buildOnlineResult(onlineQuote.get());
        }
        return buildFallbackResult(region);
    }

    private String normalizeRegion(String requestedRegion) {
        String region = StringUtils.hasText(requestedRegion) ? requestedRegion.trim() : DEFAULT_REGION;
        if (!SUPPORTED_REGIONS.contains(region)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该油价地区：" + region);
        }
        return region;
    }

    private FuelLatestPricesVO buildOnlineResult(OnlineFuelPriceQuote quote) {
        FuelLatestPricesVO result = createBaseResult(quote.region());
        result.setPublishDate(quote.fetchedAt());
        result.setDataSource(ONLINE_SOURCE);
        result.setOnlineData(true);
        result.setDataNotice(ONLINE_NOTICE);
        result.setRemark(ONLINE_NOTICE);
        result.setPrices(buildPriceMap(
                quote.price92(),
                quote.price95(),
                quote.price98(),
                quote.priceDiesel()
        ));
        return result;
    }

    private FuelLatestPricesVO buildFallbackResult(String region) {
        FuelLatestPricesVO result = createBaseResult(region);
        result.setDataSource(LOCAL_SOURCE);
        result.setOnlineData(false);

        FuelPriceSnapshot snapshot = DEFAULT_REGION.equals(region) ? findLatestSnapshot() : null;
        if (snapshot == null) {
            result.setDataNotice(EMPTY_NOTICE);
            result.setRemark(EMPTY_NOTICE);
            result.setPrices(buildPriceMap(null, null, null, null));
            return result;
        }

        result.setPublishDate(snapshot.getPublishDate());
        result.setNextAdjustTime(snapshot.getNextAdjustTime());
        result.setAdjustWindow(snapshot.getAdjustWindow());
        result.setPriceChangeHint(snapshot.getPriceChangeHint());
        result.setRemark(snapshot.getRemark());
        result.setDataNotice(LOCAL_NOTICE);
        result.setPrices(buildPriceMap(
                snapshot.getPrice92(),
                snapshot.getPrice95(),
                snapshot.getPrice98(),
                snapshot.getPriceDiesel()
        ));
        return result;
    }

    private FuelPriceSnapshot findLatestSnapshot() {
        try {
            QueryWrapper<FuelPriceSnapshot> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("publish_date")
                    .orderByDesc("updated_at")
                    .last(LIMIT_ONE_SQL);
            return fuelPriceSnapshotMapper.selectOne(wrapper);
        } catch (RuntimeException exception) {
            // 在线数据不可用时，本地快照只承担兜底职责，不能继续把整个页面拖成 500。
            LOGGER.warn("查询本地油价快照失败，已返回空油价结果，reason={}", exception.getMessage());
            return null;
        }
    }

    private FuelLatestPricesVO createBaseResult(String region) {
        FuelLatestPricesVO result = new FuelLatestPricesVO();
        result.setRegion(region);
        result.setSupportedRegions(SUPPORTED_REGIONS);
        return result;
    }

    private Map<String, BigDecimal> buildPriceMap(BigDecimal price92,
                                                   BigDecimal price95,
                                                   BigDecimal price98,
                                                   BigDecimal priceDiesel) {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        prices.put("92", scaleMoney(price92));
        prices.put("95", scaleMoney(price95));
        prices.put("98", scaleMoney(price98));
        prices.put("DIESEL", scaleMoney(priceDiesel));
        return prices;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
