package com.stock.alert;

import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;

import java.util.Map;

/**
 * 预警消息格式化。
 */
public final class AlertMessageFormatter {

    private AlertMessageFormatter() {
    }

    /** 企业微信 markdown 内容（买入用绿色info，卖出用红色warning）。 */
    public static String toWeComMarkdown(SignalResult s) {
        String color = s.getType() == SignalType.BUY_LOW9 ? "info" : "warning";
        String title = (s.getType() == SignalType.BUY_LOW9 ? "低9抄底预警" : "高9逃顶预警")
                + (s.isStrong() ? " · 强信号" : "");
        StringBuilder sb = new StringBuilder();
        sb.append("**<font color=\"").append(color).append("\">").append(title)
                .append(" ").append(s.getScore()).append("/").append(s.getMaxScore()).append("</font>**\n");
        sb.append("> ").append(assetKind(s)).append("：**")
                .append(nv(s.getName())).append("(").append(s.getCode()).append(")**\n");
        sb.append("> 时间：").append(when(s)).append("\n");
        sb.append("> 命中：").append(String.join(" / ", s.getFactorLabels())).append("\n");
        Map<String, Object> d = s.getDetail();
        sb.append("> 收盘：").append(fmt(d.get("close")))
                .append("  涨跌：").append(fmt(d.get("changePct"))).append("%\n");
        sb.append("> MA20：").append(fmt(d.get("ma20")))
                .append("  MA60：").append(fmt(d.get("ma60"))).append("\n");
        sb.append("> BIAS20：").append(fmt(d.get("bias20")))
                .append("  BIAS60：").append(fmt(d.get("bias60")));
        if (s.getType() == SignalType.BUY_LOW9 && s.getStopPrice() != null) {
            sb.append("\n> ──────────\n");
            sb.append("> 进场：**").append(fmt(s.getEntryPrice())).append("**")
                    .append("  止损：<font color=\"warning\">").append(fmt(s.getStopPrice())).append("</font>\n");
            sb.append("> 目标(回MA20)：<font color=\"info\">").append(fmt(s.getTargetPrice())).append("</font>")
                    .append("  最晚了结：").append(fmt(s.getLatestExitDate()));
        }
        return sb.toString();
    }

    /** 控制台/日志纯文本。 */
    public static String toPlainText(SignalResult s) {
        String title = (s.getType() == SignalType.BUY_LOW9 ? "低9抄底" : "高9逃顶")
                + (s.isStrong() ? "(强信号)" : "");
        Map<String, Object> d = s.getDetail();
        String base = String.format("[预警] %s %s %s(%s) %s 命中:%s 评分:%d/%d 收盘:%s 涨跌:%s%% MA20:%s MA60:%s",
                title, assetKind(s), nv(s.getName()), s.getCode(), when(s),
                String.join("/", s.getFactorLabels()), s.getScore(), s.getMaxScore(),
                fmt(d.get("close")), fmt(d.get("changePct")), fmt(d.get("ma20")), fmt(d.get("ma60")));
        if (s.getType() == SignalType.BUY_LOW9 && s.getStopPrice() != null) {
            base += String.format(" | 进场:%s 止损:%s 目标:%s 最晚了结:%s",
                    fmt(s.getEntryPrice()), fmt(s.getStopPrice()),
                    fmt(s.getTargetPrice()), fmt(s.getLatestExitDate()));
        }
        return base;
    }

    public static String exitSubject(SignalTradeTrack t) {
        return "低9计划·" + exitLabel(t.getStatus()) + " "
                + nv(t.getName()) + "(" + t.getCode() + ")";
    }

    public static String exitToPlainText(SignalTradeTrack t) {
        return String.format("[低9计划·%s] %s(%s) 信号:%s 进场:%s 出场:%s 出场日:%s "
                        + "收益:%s%% 持有:%s个交易日",
                exitLabel(t.getStatus()), nv(t.getName()), t.getCode(), t.getSignalDate(),
                fmt(t.getEntryPrice()), fmt(t.getExitPrice()), fmt(t.getExitDate()),
                signed(t.getReturnPct()), fmt(t.getHoldDays()));
    }

    public static String exitToWeComMarkdown(SignalTradeTrack t) {
        String color = t.getStatus() == TradeTrackStatus.TP ? "info" : "warning";
        return "**<font color=\"" + color + "\">低9计划·" + exitLabel(t.getStatus()) + "</font>**\n"
                + "> 股票：**" + nv(t.getName()) + "(" + t.getCode() + ")**\n"
                + "> 信号日期：" + t.getSignalDate() + "\n"
                + "> 进场：" + fmt(t.getEntryPrice()) + "  出场：" + fmt(t.getExitPrice()) + "\n"
                + "> 出场日期：" + fmt(t.getExitDate()) + "  持有：" + fmt(t.getHoldDays()) + "个交易日\n"
                + "> 收益：**" + signed(t.getReturnPct()) + "%**";
    }

    public static String exitToHtml(SignalTradeTrack t) {
        boolean profit = t.getReturnPct() != null && t.getReturnPct() >= 0;
        String accent = profit ? "#cf1322" : "#08979c";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:-apple-system,Segoe UI,Microsoft YaHei,sans-serif;")
                .append("max-width:560px;margin:0 auto;border:1px solid #eee;border-radius:10px;overflow:hidden\">");
        sb.append("<div style=\"background:").append(accent)
                .append(";color:#fff;padding:14px 18px;font-size:18px;font-weight:700\">")
                .append("低9计划·").append(exitLabel(t.getStatus())).append("</div>");
        sb.append("<div style=\"padding:16px 18px;font-size:14px;color:#333;line-height:1.9\">");
        row(sb, "股票", nv(t.getName()) + " (" + t.getCode() + ")");
        row(sb, "信号日期", fmt(t.getSignalDate()));
        row(sb, "进场价", fmt(t.getEntryPrice()));
        row(sb, "出场价", "<b>" + fmt(t.getExitPrice()) + "</b>");
        row(sb, "出场日期", fmt(t.getExitDate()));
        row(sb, "持有时间", fmt(t.getHoldDays()) + " 个交易日");
        row(sb, "收益", "<span style=\"color:" + accent + ";font-weight:700\">"
                + signed(t.getReturnPct()) + "%</span>");
        sb.append("</div><div style=\"padding:10px 18px;background:#fafafa;color:#999;font-size:12px\">")
                .append("本邮件由神奇九转选股监控系统自动发送，仅供参考，不构成投资建议。</div></div>");
        return sb.toString();
    }

    /** 邮件标题。 */
    public static String mailSubject(SignalResult s) {
        String t = (s.getType() == SignalType.BUY_LOW9 ? "低9抄底" : "高9逃顶")
                + (s.isStrong() ? "·强信号" : "");
        return String.format("%s %s(%s) %s/%d", t, nv(s.getName()), s.getCode(),
                String.valueOf(s.getScore()), s.getMaxScore());
    }

    /** 邮件 HTML 正文。 */
    public static String toHtml(SignalResult s) {
        boolean buy = s.getType() == SignalType.BUY_LOW9;
        String accent = buy ? "#cf1322" : "#08979c";
        String title = (buy ? "低9抄底预警" : "高9逃顶预警") + (s.isStrong() ? " · 强信号" : "");
        Map<String, Object> d = s.getDetail();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:-apple-system,Segoe UI,Microsoft YaHei,sans-serif;")
                .append("max-width:560px;margin:0 auto;border:1px solid #eee;border-radius:10px;overflow:hidden\">");
        sb.append("<div style=\"background:").append(accent)
                .append(";color:#fff;padding:14px 18px;font-size:18px;font-weight:700\">")
                .append(title).append("　").append(s.getScore()).append("/").append(s.getMaxScore())
                .append("</div>");
        sb.append("<div style=\"padding:16px 18px;font-size:14px;color:#333;line-height:1.9\">");
        row(sb, assetKind(s), nv(s.getName()) + " (" + s.getCode() + ")");
        row(sb, "时间", when(s));
        row(sb, "命中要素", String.join(" / ", s.getFactorLabels()));
        row(sb, "收盘 / 涨跌", fmt(d.get("close")) + "　/　" + fmt(d.get("changePct")) + "%");
        row(sb, "MA20 / MA60", fmt(d.get("ma20")) + "　/　" + fmt(d.get("ma60")));
        row(sb, "BIAS20 / BIAS60", fmt(d.get("bias20")) + "　/　" + fmt(d.get("bias60")));
        if (buy && s.getStopPrice() != null) {
            sb.append("<hr style=\"border:none;border-top:1px dashed #ddd;margin:10px 0\"/>");
            row(sb, "进场价", "<b>" + fmt(s.getEntryPrice()) + "</b>");
            row(sb, "止损价", "<span style=\"color:#cf1322\">" + fmt(s.getStopPrice()) + "</span>");
            row(sb, "目标价(回MA20)", "<span style=\"color:#08979c\">" + fmt(s.getTargetPrice()) + "</span>");
            row(sb, "最晚了结", fmt(s.getLatestExitDate()));
        }
        sb.append("</div>");
        sb.append("<div style=\"padding:10px 18px;background:#fafafa;color:#999;font-size:12px\">")
                .append("本邮件由神奇九转选股监控系统自动发送，仅供参考，不构成投资建议。</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("<div><span style=\"display:inline-block;width:120px;color:#888\">")
                .append(k).append("</span>").append(v).append("</div>");
    }

    private static String assetKind(SignalResult s) {
        return s.getAssetKind() == null || s.getAssetKind().trim().isEmpty() ? "股票" : s.getAssetKind();
    }

    private static String when(SignalResult s) {
        StringBuilder sb = new StringBuilder();
        if (s.getBarTime() != null) {
            sb.append(s.getBarTime().toString().replace('T', ' '));
        } else if (s.getTradeDate() != null) {
            sb.append(s.getTradeDate());
        } else {
            sb.append("-");
        }
        if (s.getInterval() != null && !s.getInterval().trim().isEmpty()) {
            sb.append(" · ").append(s.getInterval());
        }
        return sb.toString();
    }

    private static String nv(String v) {
        return v == null ? "" : v;
    }

    private static String fmt(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }

    private static String signed(Double value) {
        if (value == null) {
            return "-";
        }
        return (value > 0 ? "+" : "") + value;
    }

    private static String exitLabel(TradeTrackStatus status) {
        if (status == TradeTrackStatus.TP) {
            return "止盈";
        }
        if (status == TradeTrackStatus.SL) {
            return "止损";
        }
        if (status == TradeTrackStatus.TIME) {
            return "到期";
        }
        return "持有中";
    }
}
