package com.stock.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.ai.AiClient;
import com.stock.analysis.AnalysisService;
import com.stock.analysis.CurrentDecision;
import com.stock.analysis.HistoryReport;
import com.stock.domain.NewsItem;
import com.stock.domain.StockDiagnosis;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.news.NewsService;
import com.stock.news.Sentiment;
import com.stock.repository.StockDiagnosisRepository;
import com.stock.repository.StockPoolRepository;
import com.stock.scan.RealtimeScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AI 诊股：聚合 K线/指标 + 九转信号 + 历史回测 + 舆情 → 规则综合分 → DeepSeek 解读 → 按交易日缓存。
 */
@Slf4j
@Service
public class DiagnosisService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RealtimeScanService realtimeScanService;
    private final AnalysisService analysisService;
    private final NewsService newsService;
    private final StockPoolRepository poolRepository;
    private final StockDiagnosisRepository diagnosisRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public DiagnosisService(RealtimeScanService realtimeScanService, AnalysisService analysisService,
                            NewsService newsService, StockPoolRepository poolRepository,
                            StockDiagnosisRepository diagnosisRepository, AiClient aiClient,
                            ObjectMapper objectMapper) {
        this.realtimeScanService = realtimeScanService;
        this.analysisService = analysisService;
        this.newsService = newsService;
        this.poolRepository = poolRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public DiagnosisView diagnose(String code, boolean refresh) {
        String name = poolRepository.findByCode(code).map(p -> p.getName()).orElse(code);

        IndicatorSeries series = realtimeScanService.computeRealtimeSeries(code);
        if (series == null || series.isEmpty()) {
            DiagnosisView v = new DiagnosisView();
            v.setCode(code);
            v.setName(name);
            v.setStance("观望");
            v.setSummary("暂无K线数据，无法诊断");
            v.setGeneratedAt(LocalDateTime.now().format(TS));
            return v;
        }
        BarIndicator last = series.getBars().get(series.size() - 1);

        if (!refresh) {
            DiagnosisView cached = diagnosisRepository
                    .findFirstByCodeAndTradeDateOrderByIdDesc(code, last.getTradeDate())
                    .map(this::deserialize)
                    .orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        CurrentDecision cur = analysisService.current(code);
        HistoryReport hist = analysisService.history(code);
        List<NewsItem> news = newsService.list(code);

        DiagnosisView view = new DiagnosisView();
        view.setCode(code);
        view.setName(name);
        view.setAsOf(last.getTradeDate());
        view.setPrice(round(last.getClose()));

        // 关键事实快照
        DiagnosisView.Facts f = view.getFacts();
        fillFacts(f, last, cur, hist, news);

        // 规则综合分
        int ruleScore = ruleScore(last, cur, f);
        view.setRuleScore(ruleScore);
        String ruleStance = stanceOf(ruleScore);

        // AI 解读
        boolean ai = false;
        if (aiClient.isReady()) {
            ai = applyAi(view, name, code, last, cur, hist, news, ruleScore);
        }
        if (!ai) {
            fillRuleFallback(view, ruleScore, ruleStance, cur, f);
        }
        view.setAiUsed(ai);
        if (view.getScore() == null) {
            view.setScore(ruleScore);
        }
        if (view.getStance() == null || view.getStance().isEmpty()) {
            view.setStance(ruleStance);
        }
        view.setGeneratedAt(LocalDateTime.now().format(TS));

        persist(view);
        return view;
    }

    /* ---------------- 事实聚合 ---------------- */
    private void fillFacts(DiagnosisView.Facts f, BarIndicator b, CurrentDecision cur,
                           HistoryReport hist, List<NewsItem> news) {
        f.setChangePct(b.getChangePct());
        f.setBias20(b.getBias20());
        Double ma20 = b.getMa20(), ma60 = b.getMa60();
        if (ma20 != null && ma60 != null) {
            f.setMaTrend(ma20 > ma60 ? "多头排列" : "空头排列");
        } else {
            f.setMaTrend("数据不足");
        }
        f.setPriceVsMa20(ma20 != null ? (b.getClose() > ma20 ? "站上MA20" : "跌破MA20") : "数据不足");
        if (b.getDif() != null && b.getDea() != null) {
            f.setMacdState(b.getDif() > b.getDea() ? "金叉区" : "死叉区");
        }
        Double vm5 = b.getVolMa5();
        if (vm5 != null && vm5 > 0) {
            double r = b.getVolume() / vm5;
            f.setVolState(r > 1.2 ? "放量" : (r < 0.8 ? "缩量" : "平量"));
        }
        if (cur != null && cur.isHasSignal()) {
            f.setSignalType("BUY_LOW9".equals(cur.getSignalType()) ? "低9买入"
                    : ("SELL_HIGH9".equals(cur.getSignalType()) ? "高9卖出" : cur.getSignalType()));
            f.setSignalDate(cur.getSignalDate());
            f.setSignalFresh(cur.isFresh());
            f.setSignalScore(cur.getScore() + "/" + cur.getMaxScore());
        } else {
            f.setSignalType("无");
        }
        if (hist != null && hist.getCount() > 0) {
            f.setHistSamples(hist.getCount());
            f.setHistWinRatePct(hist.getWinRatePct());
            f.setHistProfitFactor(hist.getProfitFactor());
        }
        for (NewsItem it : news) {
            if (it.getSentiment() == Sentiment.BULLISH) {
                f.setNewsBullish(f.getNewsBullish() + 1);
            } else if (it.getSentiment() == Sentiment.BEARISH) {
                f.setNewsBearish(f.getNewsBearish() + 1);
            } else if (it.getSentiment() == Sentiment.NEUTRAL) {
                f.setNewsNeutral(f.getNewsNeutral() + 1);
            }
        }
    }

    /* ---------------- 规则综合分 ---------------- */
    private int ruleScore(BarIndicator b, CurrentDecision cur, DiagnosisView.Facts f) {
        double close = b.getClose();
        double trend = 50;
        if (b.getMa20() != null) {
            trend += close > b.getMa20() ? 12 : -12;
        }
        if (b.getMa60() != null) {
            trend += close > b.getMa60() ? 10 : -10;
        }
        if (b.getMa20() != null && b.getMa60() != null) {
            trend += b.getMa20() > b.getMa60() ? 10 : -10;
        }
        if (b.getMa5() != null && b.getMa20() != null) {
            trend += b.getMa5() > b.getMa20() ? 8 : -8;
        }
        trend = clamp(trend);

        double mom = 50;
        if (b.getDif() != null && b.getDea() != null) {
            mom += b.getDif() > b.getDea() ? 12 : -12;
        }
        if (b.getMacd() != null) {
            mom += b.getMacd() > 0 ? 10 : -10;
        }
        if (b.getDif() != null) {
            mom += b.getDif() > 0 ? 8 : -8;
        }
        if (b.getVolMa5() != null && b.getVolMa5() > 0 && b.getChangePct() != null
                && b.getVolume() > b.getVolMa5() * 1.2) {
            mom += b.getChangePct() > 0 ? 8 : -8;
        }
        mom = clamp(mom);

        double sig = 50;
        if (cur != null && cur.isHasSignal()) {
            double ratio = cur.getMaxScore() > 0 ? (double) cur.getScore() / cur.getMaxScore() : 0;
            if ("BUY_LOW9".equals(cur.getSignalType())) {
                sig = (cur.isFresh() ? 70 : 60) + ratio * 25;
                if (cur.isBrokeStop() || cur.isExpired()) {
                    sig = 45;
                }
            } else if ("SELL_HIGH9".equals(cur.getSignalType())) {
                sig = (cur.isFresh() ? 25 : 38) - ratio * 12;
            }
        }
        sig = clamp(sig);

        double sen = 50 + (f.getNewsBullish() - f.getNewsBearish()) * 7;
        sen = clamp(sen);

        double total = trend * 0.30 + mom * 0.25 + sig * 0.30 + sen * 0.15;
        return (int) Math.round(clamp(total));
    }

    private static String stanceOf(int score) {
        if (score >= 65) {
            return "偏多";
        }
        if (score <= 40) {
            return "偏空";
        }
        return "中性";
    }

    /* ---------------- AI 解读 ---------------- */
    private boolean applyAi(DiagnosisView view, String name, String code, BarIndicator b,
                            CurrentDecision cur, HistoryReport hist, List<NewsItem> news, int ruleScore) {
        String facts = buildFactsJson(name, code, b, cur, hist, news, ruleScore);
        String sys = "你是严谨的A股技术分析助手。基于给定的量化事实(JSON)对该股票做综合诊断："
                + "研判趋势、动能、神奇九转信号、舆情，并给出可执行的操作建议与主要风险。"
                + "要求：①只依据提供的事实，绝不编造或推算未提供的数字；②给出0-100综合评分(越高越偏多)与立场；"
                + "③中文、精炼专业；④严格只输出JSON。"
                + "输出结构：{\"score\":整数,\"stance\":\"偏多|偏空|中性|观望\",\"trend\":\"\",\"momentum\":\"\","
                + "\"signal\":\"\",\"sentiment\":\"\",\"risks\":[\"\"],"
                + "\"action\":{\"direction\":\"买入|持有|减仓|观望\",\"entry\":\"\",\"stop\":数字或null,"
                + "\"target\":数字或null,\"position\":\"空仓|轻仓|半仓|重仓\"},\"summary\":\"30字内结论\"}";
        String user = "量化事实如下(JSON)，请据此诊断：\n" + facts;
        String content = aiClient.completeJson(sys, user, 0.3);
        if (content == null) {
            return false;
        }
        try {
            JsonNode j = objectMapper.readTree(content);
            if (j.has("score")) {
                view.setScore(clampInt(j.path("score").asInt()));
            }
            view.setStance(text(j, "stance"));
            view.setTrend(text(j, "trend"));
            view.setMomentum(text(j, "momentum"));
            view.setSignal(text(j, "signal"));
            view.setSentiment(text(j, "sentiment"));
            JsonNode risks = j.path("risks");
            if (risks.isArray()) {
                view.getRisks().clear();
                for (JsonNode r : risks) {
                    String rs = r.asText("");
                    if (!rs.isEmpty()) {
                        view.getRisks().add(rs);
                    }
                }
            }
            JsonNode a = j.path("action");
            DiagnosisView.Action act = view.getAction();
            act.setDirection(text(a, "direction"));
            act.setEntry(text(a, "entry"));
            act.setPosition(text(a, "position"));
            if (a.hasNonNull("stop")) {
                act.setStop(round(a.path("stop").asDouble()));
            }
            if (a.hasNonNull("target")) {
                act.setTarget(round(a.path("target").asDouble()));
            }
            view.setSummary(text(j, "summary"));
            return view.getSummary() != null && !view.getSummary().isEmpty();
        } catch (Exception e) {
            log.error("解析AI诊股结果失败 code={} err={}", code, e.getMessage());
            return false;
        }
    }

    private String buildFactsJson(String name, String code, BarIndicator b, CurrentDecision cur,
                                  HistoryReport hist, List<NewsItem> news, int ruleScore) {
        try {
            ObjectNode r = objectMapper.createObjectNode();
            r.put("股票", name + "(" + code + ")");
            r.put("日期", String.valueOf(b.getTradeDate()));
            r.put("现价", round(b.getClose()));
            r.put("涨跌幅%", b.getChangePct());
            ObjectNode ma = r.putObject("均线");
            ma.put("MA5", b.getMa5());
            ma.put("MA20", b.getMa20());
            ma.put("MA60", b.getMa60());
            ObjectNode macd = r.putObject("MACD");
            macd.put("DIF", b.getDif());
            macd.put("DEA", b.getDea());
            macd.put("柱", b.getMacd());
            r.put("BIAS20", b.getBias20());
            r.put("BIAS60", b.getBias60());
            ObjectNode vol = r.putObject("量能");
            vol.put("成交量", b.getVolume());
            vol.put("5日均量", b.getVolMa5());
            r.put("ATR", b.getAtr());

            ObjectNode sg = r.putObject("神奇九转");
            if (cur != null && cur.isHasSignal()) {
                sg.put("类型", "BUY_LOW9".equals(cur.getSignalType()) ? "低9买入"
                        : ("SELL_HIGH9".equals(cur.getSignalType()) ? "高9卖出" : cur.getSignalType()));
                sg.put("信号日", String.valueOf(cur.getSignalDate()));
                sg.put("是否今日触发", cur.isFresh());
                sg.put("评分", cur.getScore() + "/" + cur.getMaxScore());
                if (cur.getFactors() != null) {
                    ArrayNode fa = sg.putArray("命中要素");
                    cur.getFactors().forEach(fa::add);
                }
                if ("BUY_LOW9".equals(cur.getSignalType())) {
                    sg.put("止损价", cur.getStopPrice());
                    sg.put("目标价", cur.getTargetPrice());
                    sg.put("距止损%", cur.getDistToStopPct());
                    sg.put("距目标%", cur.getDistToTargetPct());
                    sg.put("最晚了结", String.valueOf(cur.getLatestExitDate()));
                    sg.put("计划是否失效", cur.isBrokeStop() || cur.isExpired());
                }
            } else {
                sg.put("类型", "无");
            }

            if (hist != null && hist.getCount() > 0) {
                ObjectNode h = r.putObject("历史低9回测");
                h.put("样本数", hist.getCount());
                h.put("胜率%", hist.getWinRatePct());
                h.put("盈亏比", hist.getProfitFactor());
                h.put("平均每笔%", hist.getAvgReturnPct());
            }

            ObjectNode se = r.putObject("舆情");
            int bull = 0, bear = 0, neu = 0;
            ArrayNode heads = objectMapper.createArrayNode();
            for (NewsItem it : news) {
                if (it.getSentiment() == Sentiment.BULLISH) {
                    bull++;
                } else if (it.getSentiment() == Sentiment.BEARISH) {
                    bear++;
                } else if (it.getSentiment() == Sentiment.NEUTRAL) {
                    neu++;
                }
                if (heads.size() < 6 && it.getTitle() != null) {
                    ObjectNode hn = heads.addObject();
                    hn.put("情感", it.getSentiment() == null ? "未分析" : it.getSentiment().getLabel());
                    hn.put("标题", it.getTitle());
                    if (it.getReason() != null && !it.getReason().isEmpty()) {
                        hn.put("理由", it.getReason());
                    }
                }
            }
            se.put("利好条数", bull);
            se.put("利空条数", bear);
            se.put("中性条数", neu);
            se.set("近期标题", heads);

            r.put("规则综合分", ruleScore);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r);
        } catch (Exception e) {
            return "{}";
        }
    }

    /* ---------------- 规则降级文案 ---------------- */
    private void fillRuleFallback(DiagnosisView view, int ruleScore, String ruleStance,
                                  CurrentDecision cur, DiagnosisView.Facts f) {
        view.setScore(ruleScore);
        view.setStance(ruleStance);
        view.setTrend("均线" + nz(f.getMaTrend()) + "，价格" + nz(f.getPriceVsMa20()) + "。");
        view.setMomentum("MACD处于" + nz(f.getMacdState()) + "，量能" + nz(f.getVolState()) + "。");
        if (cur != null && cur.isHasSignal()) {
            view.setSignal("最近信号：" + nz(f.getSignalType())
                    + (cur.isFresh() ? "（今日触发）" : "（" + cur.getSignalDate() + "）")
                    + "，评分" + nz(f.getSignalScore()) + "。");
        } else {
            view.setSignal("近期无神奇九转信号。");
        }
        view.setSentiment("近期舆情 利好" + f.getNewsBullish() + " / 利空" + f.getNewsBearish()
                + " / 中性" + f.getNewsNeutral() + "。");
        DiagnosisView.Action act = view.getAction();
        if (cur != null && cur.isHasSignal() && "BUY_LOW9".equals(cur.getSignalType())
                && !cur.isBrokeStop() && !cur.isExpired()) {
            act.setDirection("买入");
            act.setStop(cur.getStopPrice());
            act.setTarget(cur.getTargetPrice());
            act.setEntry("现价附近");
            act.setPosition("轻仓试探");
        } else {
            act.setDirection(ruleScore >= 65 ? "持有" : "观望");
            act.setPosition(ruleScore >= 65 ? "轻仓" : "空仓");
        }
        view.setSummary("规则评分 " + ruleScore + "，立场" + ruleStance + "（AI未启用，规则降级结果）。");
    }

    /* ---------------- 缓存 ---------------- */
    private void persist(DiagnosisView view) {
        try {
            StockDiagnosis e = new StockDiagnosis();
            e.setCode(view.getCode());
            e.setTradeDate(view.getAsOf());
            e.setScore(view.getScore());
            e.setStance(view.getStance());
            e.setAiUsed(view.isAiUsed());
            e.setPayload(objectMapper.writeValueAsString(view));
            diagnosisRepository.save(e);
        } catch (Exception ex) {
            log.warn("保存诊股缓存失败 code={} err={}", view.getCode(), ex.getMessage());
        }
    }

    private DiagnosisView deserialize(StockDiagnosis e) {
        try {
            return objectMapper.readValue(e.getPayload(), DiagnosisView.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /* ---------------- 工具 ---------------- */
    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        return v.isEmpty() ? null : v;
    }

    private static String nz(String s) {
        return s == null ? "—" : s;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int clampInt(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static Double round(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
