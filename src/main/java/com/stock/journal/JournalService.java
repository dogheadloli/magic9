package com.stock.journal;

import com.stock.datafetch.MarketDataProvider;
import com.stock.datafetch.StockQuote;
import com.stock.domain.ManualTrade;
import com.stock.repository.ManualTradeRepository;
import com.stock.repository.StockPoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实盘记账：手动建仓/平仓，并按现价计算浮动盈亏。
 */
@Service
public class JournalService {

    private static final int BATCH = 10;

    private final ManualTradeRepository repository;
    private final StockPoolRepository poolRepository;
    private final MarketDataProvider provider;

    public JournalService(ManualTradeRepository repository, StockPoolRepository poolRepository,
                          MarketDataProvider provider) {
        this.repository = repository;
        this.poolRepository = poolRepository;
        this.provider = provider;
    }

    @Transactional
    public ManualTrade create(JournalRequest req) {
        ManualTrade t = new ManualTrade();
        t.setCode(req.getCode());
        String name = req.getName();
        if (name == null || name.isEmpty()) {
            name = poolRepository.findByCode(req.getCode()).map(p -> p.getName()).orElse(req.getCode());
        }
        t.setName(name);
        t.setEntryDate(req.getEntryDate());
        t.setEntryPrice(req.getEntryPrice());
        t.setQty(req.getQty());
        t.setStopPrice(req.getStopPrice());
        t.setTargetPrice(req.getTargetPrice());
        t.setLatestExitDate(req.getLatestExitDate());
        t.setNote(req.getNote());
        t.setSignalId(req.getSignalId());
        t.setStatus(ManualTrade.Status.OPEN);
        return repository.save(t);
    }

    @Transactional
    public ManualTrade close(Long id, CloseRequest req) {
        ManualTrade t = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        t.setExitDate(req.getExitDate());
        t.setExitPrice(req.getExitPrice());
        t.setStatus(ManualTrade.Status.CLOSED);
        return repository.save(t);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public JournalView list() {
        List<ManualTrade> all = repository.findByOrderByStatusAscEntryDateDesc();
        Map<String, Double> priceByCode = currentPrices(all);

        JournalView view = new JournalView();
        int wins = 0, closed = 0;
        for (ManualTrade t : all) {
            TradeView v = TradeView.of(t);
            if (t.getStatus() == ManualTrade.Status.CLOSED) {
                closed++;
                double pnl = (t.getExitPrice() - t.getEntryPrice()) * t.getQty();
                v.setPrice(t.getExitPrice());
                v.setPnl(round(pnl));
                v.setReturnPct(round((t.getExitPrice() - t.getEntryPrice()) / t.getEntryPrice() * 100));
                view.setRealizedPnl(view.getRealizedPnl() + pnl);
                if (pnl > 0) {
                    wins++;
                }
            } else {
                view.setOpenCount(view.getOpenCount() + 1);
                Double price = priceByCode.get(t.getCode());
                double cost = t.getEntryPrice() * t.getQty();
                view.setCost(view.getCost() + cost);
                if (price != null) {
                    double pnl = (price - t.getEntryPrice()) * t.getQty();
                    v.setPrice(price);
                    v.setPnl(round(pnl));
                    v.setReturnPct(round((price - t.getEntryPrice()) / t.getEntryPrice() * 100));
                    view.setUnrealizedPnl(view.getUnrealizedPnl() + pnl);
                    view.setMarketValue(view.getMarketValue() + price * t.getQty());
                } else {
                    view.setMarketValue(view.getMarketValue() + cost);
                }
            }
            view.getTrades().add(v);
        }
        view.setClosedCount(closed);
        view.setUnrealizedPnl(round(view.getUnrealizedPnl()));
        view.setRealizedPnl(round(view.getRealizedPnl()));
        view.setCost(round(view.getCost()));
        view.setMarketValue(round(view.getMarketValue()));
        view.setTotalPnl(round(view.getUnrealizedPnl() + view.getRealizedPnl()));
        view.setWinRatePct(closed > 0 ? round(wins * 100.0 / closed) : 0);
        return view;
    }

    /** 批量取持仓中标的现价（每 10 只一批）。 */
    private Map<String, Double> currentPrices(List<ManualTrade> all) {
        Set<String> codes = new LinkedHashSet<>();
        for (ManualTrade t : all) {
            if (t.getStatus() == ManualTrade.Status.OPEN) {
                codes.add(t.getCode());
            }
        }
        Map<String, Double> map = new HashMap<>();
        List<String> list = new ArrayList<>(codes);
        for (int i = 0; i < list.size(); i += BATCH) {
            List<String> chunk = list.subList(i, Math.min(i + BATCH, list.size()));
            for (StockQuote q : provider.fetchQuotes(chunk)) {
                if (q != null && q.getCode() != null && q.getPrice() != null) {
                    map.put(q.getCode(), q.getPrice().doubleValue());
                }
            }
        }
        return map;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
