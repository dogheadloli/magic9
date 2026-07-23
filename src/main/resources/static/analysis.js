'use strict';

const $ = (id) => document.getElementById(id);
let chart = null;
let reviewData = null;

async function getJson(url) {
    const res = await fetch(url);
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error((data && data.error) || res.statusText);
    return data;
}

async function sendJson(url, method, body) {
    const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error((data && data.error) || res.statusText);
    return data;
}

function today() { return new Date().toISOString().slice(0, 10); }

function setStatus(msg, isErr) {
    const el = $('status');
    el.textContent = msg || '';
    el.style.color = isErr ? '#ffd2d2' : '';
    if (msg && !isErr) setTimeout(() => { if (el.textContent === msg) el.textContent = ''; }, 3000);
}

function fmt(v) { return (v === null || v === undefined) ? '-' : Number(v).toFixed(2); }
function signed(v) { if (v === null || v === undefined) return '-'; const n = Number(v); return (n > 0 ? '+' : '') + n.toFixed(2); }
function ts(dateStr) { return new Date(dateStr + 'T00:00:00').getTime(); }
function toKline(b) { return { timestamp: ts(b.tradeDate), open: b.open, high: b.high, low: b.low, close: b.close, volume: b.volume }; }

/* ---------------- 图表 ---------------- */
function initChart() {
    chart = klinecharts.init('kline');
    chart.setStyles({
        candle: {
            bar: {
                upColor: '#ef5350', downColor: '#26a69a', noChangeColor: '#888888',
                upBorderColor: '#ef5350', downBorderColor: '#26a69a',
                upWickColor: '#ef5350', downWickColor: '#26a69a'
            },
            tooltip: { showRule: 'follow_cross' }
        }
    });
    chart.createIndicator({ name: 'MA', calcParams: [20, 60] }, true, { id: 'candle_pane' });
    chart.createIndicator('VOL', false);
    chart.createIndicator('MACD', false);
    window.addEventListener('resize', () => chart && chart.resize());
}

function addAnno(dateStr, value, text) {
    if (value == null || isNaN(value) || !dateStr) return;
    chart.createOverlay({
        name: 'simpleAnnotation',
        points: [{ timestamp: ts(dateStr), value: Number(value) }],
        extendData: text
    });
}

async function loadChart(item) {
    let bars;
    try { bars = await getJson(`/api/indicator/realtime?code=${item.code}`); } catch (e) { return; }
    if (!bars || !bars.length) {
        $('chartHint').textContent = '暂无日K数据，请先在控制台回补日K';
        $('chartHint').style.display = '';
        return;
    }
    $('chartHint').style.display = 'none';
    chart.applyNewData(bars.map(toKline));
    chart.removeOverlay();
    addAnno(item.tradeDate, item.entryPrice, '低9买');
    if (item.exitDate && item.exitPrice != null) {
        const tag = item.reason === 'TP' ? '盈' : (item.reason === 'SL' ? '损' : (item.reason === 'TIME' ? '结' : ''));
        if (tag) addAnno(item.exitDate, item.exitPrice, tag);
    }
    $('chartTitle').textContent = `K线图 · ${item.name || ''}(${item.code}) · 信号 ${item.tradeDate}`;
    try { chart.scrollToTimestamp(ts(item.tradeDate), 0); } catch (e) { /* older api */ }
}

/* ---------------- 低9信号复盘 ---------------- */
const RSN = { TP: '止盈', SL: '止损', TIME: '到期', OPEN: '持有中', NA: '—' };

function revRow(it, i) {
    const ret = it.returnPct;
    const retCls = ret == null ? '' : (ret >= 0 ? 'up' : 'down');
    const rsn = it.reason || 'NA';
    return `<tr data-i="${i}" style="cursor:pointer">
        <td>${it.tradeDate}</td>
        <td>${it.code}</td>
        <td>${it.name || ''}</td>
        <td>${it.score}/${it.maxScore}${it.strong ? ' <span class="tag strong">强</span>' : ''}</td>
        <td><span class="rsn ${rsn}">${RSN[rsn] || rsn}</span></td>
        <td class="${retCls}">${ret == null ? '-' : signed(ret)}</td>
        <td>${it.holdDays == null ? '-' : it.holdDays}</td>
        <td class="muted">${(it.factors || []).join(' / ')}</td>
    </tr>`;
}

function renderReview(d) {
    reviewData = d;
    const losses = d.pageClosed - d.pageWins;
    $('revStats').textContent = `· 共 ${d.totalElements} 个信号 · 本页 ${d.pageWins}胜/${losses}负`;
    const body = $('revBody');
    if (!d.items || !d.items.length) {
        body.innerHTML = `<tr><td class="muted" colspan="8">数据库暂无低9信号，请先在控制台执行扫描</td></tr>`;
    } else {
        body.innerHTML = d.items.map(revRow).join('');
        body.querySelectorAll('tr[data-i]').forEach(tr => {
            tr.addEventListener('click', () => {
                const it = reviewData.items[+tr.dataset.i];
                body.querySelectorAll('tr').forEach(x => x.classList.remove('row-active'));
                tr.classList.add('row-active');
                loadChart(it);
            });
        });
    }
    $('revInfo').textContent = `第 ${d.page + 1} / ${Math.max(1, d.totalPages)} 页`;
    $('revPrev').disabled = d.page <= 0;
    $('revNext').disabled = d.page >= d.totalPages - 1;
}

async function loadReview(page) {
    setStatus('加载中…');
    let d;
    try { d = await getJson(`/api/review/low9?page=${page}&size=20`); }
    catch (e) { setStatus('加载失败: ' + e.message, true); return; }
    renderReview(d);
    setStatus('');
    if (d.items && d.items.length) {
        const first = $('revBody').querySelector('tr[data-i]');
        if (first) { first.classList.add('row-active'); loadChart(d.items[0]); }
    }
}

/* ---------------- 实盘记账 ---------------- */
function jRow(t) {
    const cls = t.pnl == null ? '' : (t.pnl >= 0 ? 'up' : 'down');
    const isOpen = t.status === 'OPEN';
    const ops = isOpen
        ? `<span class="switch" onclick="closeTrade(${t.id})">平仓</span>　<span class="del" onclick="delTrade(${t.id})">删</span>`
        : `<span class="del" onclick="delTrade(${t.id})">删</span>`;
    const st = isOpen ? `<span class="rsn OPEN">持仓</span>` : `<span class="rsn TIME">已平</span>`;
    return `<tr>
        <td>${t.code}</td><td>${t.name || ''}</td><td>${t.entryDate}</td><td>${fmt(t.entryPrice)}</td>
        <td>${t.qty}</td><td>${fmt(t.stopPrice)}</td><td>${fmt(t.targetPrice)}</td>
        <td>${fmt(t.price)}</td>
        <td class="${cls}">${t.pnl == null ? '-' : signed(t.pnl)}</td>
        <td class="${cls}">${t.returnPct == null ? '-' : signed(t.returnPct)}</td>
        <td>${st}</td><td>${ops}</td>
    </tr>`;
}

async function loadJournal() {
    let v;
    try { v = await getJson('/api/journal'); } catch (e) { return; }
    const body = $('jBody');
    if (!v.trades || !v.trades.length) {
        body.innerHTML = `<tr><td class="muted" colspan="12">暂无持仓记录</td></tr>`;
    } else {
        body.innerHTML = v.trades.map(jRow).join('');
    }
    $('jSummary').textContent =
        `持仓 ${v.openCount} · 浮盈 ${signed(v.unrealizedPnl)} ｜ 已平 ${v.closedCount} · 已实现 ${signed(v.realizedPnl)} · 胜率 ${v.winRatePct}% ｜ 合计 ${signed(v.totalPnl)}`;
}

async function createTrade(e) {
    e.preventDefault();
    const body = {
        code: $('jCode').value.trim(),
        entryDate: $('jDate').value || today(),
        entryPrice: parseFloat($('jEntry').value),
        qty: parseInt($('jQty').value, 10),
        stopPrice: $('jStop').value ? parseFloat($('jStop').value) : null,
        targetPrice: $('jTarget').value ? parseFloat($('jTarget').value) : null,
        latestExitDate: $('jLatest').value || null
    };
    if (!/^\d{6}$/.test(body.code) || !body.entryPrice || !body.qty) {
        setStatus('请填写正确的代码 / 进场价 / 数量', true);
        return;
    }
    try {
        await sendJson('/api/journal', 'POST', body);
        $('jForm').reset();
        await loadJournal();
        setStatus('已建仓');
    } catch (err) { setStatus('建仓失败: ' + err.message, true); }
}

async function closeTrade(id) {
    const px = prompt('出场价？');
    if (px === null) return;
    const price = parseFloat(px);
    if (!price) { setStatus('出场价无效', true); return; }
    try {
        await sendJson(`/api/journal/${id}/close`, 'PUT', { exitDate: today(), exitPrice: price });
        await loadJournal();
    } catch (err) { setStatus('平仓失败: ' + err.message, true); }
}

async function delTrade(id) {
    if (!confirm('删除该记录？')) return;
    try {
        await fetch(`/api/journal/${id}`, { method: 'DELETE' });
        await loadJournal();
    } catch (err) { setStatus('删除失败: ' + err.message, true); }
}

window.closeTrade = closeTrade;
window.delTrade = delTrade;

function init() {
    initChart();
    loadJournal();
    loadReview(0);
    $('revPrev').addEventListener('click', () => {
        if (reviewData && reviewData.page > 0) loadReview(reviewData.page - 1);
    });
    $('revNext').addEventListener('click', () => {
        if (reviewData && reviewData.page < reviewData.totalPages - 1) loadReview(reviewData.page + 1);
    });
    $('jForm').addEventListener('submit', createTrade);
}

document.addEventListener('DOMContentLoaded', init);
