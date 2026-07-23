package com.stock.datafetch;

import com.stock.domain.Market;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情数据源适配接口（可插拔，后续可切换东方财富/腾讯/Tushare）。
 */
public interface MarketDataProvider {

    /**
     * 拉取日K线（前复权），按交易日升序返回。
     *
     * @param code   股票代码
     * @param market 市场
     * @param limit  回补根数
     */
    List<KlineBar> fetchDailyKline(String code, Market market, int limit);

    /**
     * 拉取实时行情快照（含名称）。
     */
    StockQuote fetchQuote(String code, Market market);

    /**
     * 批量拉取实时行情（一次请求查询多只）。默认实现逐只调用 {@link #fetchQuote}，
     * 支持批量接口的数据源（如腾讯）应覆盖此方法。市场由代码前缀推断。
     *
     * @param codes 股票代码列表（建议每批不超过数据源上限）
     */
    default List<StockQuote> fetchQuotes(List<String> codes) {
        List<StockQuote> out = new ArrayList<>();
        if (codes == null) {
            return out;
        }
        for (String code : codes) {
            out.add(fetchQuote(code, Market.inferByCode(code)));
        }
        return out;
    }
}
