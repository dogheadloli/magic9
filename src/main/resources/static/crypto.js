'use strict';

const $ = (id) => document.getElementById(id);
let chart = null;
let currentSymbol = null;
let currentName = null;
let currentInterval = 'D1';
const REFRESH_MS = 60000;
const INTERVAL_LABEL = { D1: '日K', H4: '4小时' };

async function api(url, opts) {
    const res = await fetch(url, opts);
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
        throw new Error((data && data.error) || res.statusText);
    }
    return data;
}

function setStatus(msg, isErr) {
    const el = $('status');
    el.textContent = msg || '';
    el.style.color = isErr ? '#ffd2d2' : '';
    if (msg) {
        setTimeout(() => { if (el.textContent === msg) el.textContent = ''; }, 4000);
    }
}

function barTs(b) {
    if (b.openTimeMs != null) return Number(b.openTimeMs);
    if (b.openTime) return Date.parse(b.openTime + 'Z');
    return new Date((b.tradeDate || '') + 'T00:00:00Z').getTime();
}

function fmtPx(v) {
    if (v == null || v === '') return '-';
    const n = Number(v);
    if (!isFinite(n)) return '-';
    if (Math.abs(n) >= 1000) return n.toFixed(2);
    if (Math.abs(n) >= 1) return n.toFixed(4);
    return n.toPrecision(6);
}

function fmtPct(v) {
    if (v == null || v === '') return '-';
    const n = Number(v);
    if (!isFinite(n)) return '-';
    return (n > 0 ? '+' : '') + n.toFixed(2) + '%';
}

function initChart() {
    chart = klinecharts.init('kline');
    chart.setStyles({
        candle: {
            bar: {
                upColor: '#26a69a', downColor: '#ef5350', noChangeColor: '#888888',
                upBorderColor: '#26a69a', downBorderColor: '#ef5350',
                upWickColor: '#26a69a', downWickColor: '#ef5350'
            },
            tooltip: { showRule: 'follow_cross' }
        }
    });
    chart.createIndicator({ name: 'MA', calcParams: [20, 60] }, true, { id: 'candle_pane' });
    chart.createIndicator('VOL', false);
    chart.createIndicator('MACD', false);
    window.addEventListener('resize', () => chart && chart.resize());
}

function addAnno(ts, value, text) {
    if (value == null || isNaN(value) || !ts) return;
    chart.createOverlay({
        name: 'simpleAnnotation',
        points: [{ timestamp: ts, value: Number(value) }],
        extendData: text
    });
}

function toKline(b) {
    return {
        timestamp: barTs(b),
        open: b.open, high: b.high, low: b.low, close: b.close, volume: b.volume
    };
}

async function loadChart(symbol, name) {
    currentSymbol = symbol;
    currentName = name;
    $('btnBackfill').disabled = false;
    $('chartTitle').textContent = `K线图 · ${name || ''}(${symbol}) · ${INTERVAL_LABEL[currentInterval] || currentInterval}`;
    highlightPoolRow(symbol);
    let bars;
    try {
        bars = await api(`/api/crypto/indicator?symbol=${encodeURIComponent(symbol)}&interval=${currentInterval}&live=true`);
    } catch (e) {
        setStatus('加载指标失败: ' + e.message, true);
        return;
    }
    if (!bars || !bars.length) {
        $('chartHint').textContent = '暂无K线，请点击「回补K线」';
        $('chartHint').style.display = '';
        return;
    }
    $('chartHint').style.display = 'none';
    chart.applyNewData(bars.map(toKline));
    await drawOverlays(symbol, bars);
    loadQuote(symbol);
    loadEval(symbol);
}

async function drawOverlays(symbol, bars) {
    chart.removeOverlay();
    bars.forEach(b => {
        if (b.tdSignal === 'LOW_9') addAnno(barTs(b), b.low, '低9');
        else if (b.tdSignal === 'HIGH_9') addAnno(barTs(b), b.high, '高9');
    });
    try {
        const sigs = await api(`/api/crypto/signals?symbol=${encodeURIComponent(symbol)}&interval=${currentInterval}`);
        sigs.forEach(s => {
            let price = null;
            try { price = JSON.parse(s.detailJson).close; } catch (e) { /* ignore */ }
            const txt = (s.signalType === 'BUY_LOW9' ? '抄底' : '逃顶') + (s.strong ? '★' : '');
            const ts = s.openTime ? Date.parse(s.openTime + 'Z') : null;
            addAnno(ts, price, txt);
        });
    } catch (e) { /* 信号可选 */ }
}

async function refreshLive() {
    if (!currentSymbol) return;
    let bars;
    try {
        bars = await api(`/api/crypto/indicator?symbol=${encodeURIComponent(currentSymbol)}&interval=${currentInterval}&live=true`);
    } catch (e) { return; }
    if (!bars || !bars.length) return;
    chart.updateData(toKline(bars[bars.length - 1]));
    await drawOverlays(currentSymbol, bars);
    loadQuote(currentSymbol);
    loadEval(currentSymbol);
}

function renderQuote(q) {
    const el = $('quoteBox');
    if (!q) { el.innerHTML = '<div class="muted" style="grid-column:1/-1">行情失败</div>'; return; }
    const up = Number(q.changePct) >= 0;
    const cls = up ? 'crypto-up' : 'crypto-down';
    $('quoteName').textContent = q.name ? `${q.name} / ${q.symbol}` : (q.symbol || '');
    el.innerHTML = `
        <div><div class="k">最新</div><div class="v ${cls}">${fmtPx(q.price)}</div></div>
        <div><div class="k">24h涨跌</div><div class="v ${cls}">${fmtPct(q.changePct)}</div></div>
        <div><div class="k">24h高</div><div class="v">${fmtPx(q.high)}</div></div>
        <div><div class="k">24h低</div><div class="v">${fmtPx(q.low)}</div></div>`;
}

async function loadQuote(symbol) {
    try {
        renderQuote(await api(`/api/crypto/quote?symbol=${encodeURIComponent(symbol)}`));
    } catch (e) {
        $('quoteBox').innerHTML = '<div class="muted" style="grid-column:1/-1">行情失败</div>';
    }
}

function renderEval(list) {
    const el = $('evalBox');
    $('evalInterval').textContent = INTERVAL_LABEL[currentInterval] || currentInterval;
    if (!list || !list.length) {
        el.innerHTML = '<div class="muted">当前K线未形成低9/高9信号</div>';
        return;
    }
    const s = list[0];
    const buy = s.type === 'BUY_LOW9';
    const plan = (buy && s.entryPrice != null)
        ? `<div class="muted" style="margin-top:8px;line-height:1.6">
            进场 ${fmtPx(s.entryPrice)} · 止损 ${fmtPx(s.stopPrice)} · 目标 ${fmtPx(s.targetPrice)}
            ${s.latestExitDate ? '<br/>最晚了结 ' + s.latestExitDate : ''}</div>`
        : '';
    el.innerHTML = `
        <div>
            <span class="tag ${buy ? 'buy' : 'sell'}">${buy ? '低9抄底' : '高9逃顶'}</span>
            ${s.strong ? '<span class="tag strong">强</span>' : ''}
            <span class="muted"> ${s.score}/${s.maxScore}</span>
        </div>
        <div class="muted" style="margin-top:6px">${(s.factorLabels || s.factors || []).join(' / ')}</div>
        ${plan}`;
}

async function loadEval(symbol) {
    try {
        renderEval(await api(`/api/crypto/signal/evaluate?symbol=${encodeURIComponent(symbol)}&interval=${currentInterval}`));
    } catch (e) {
        $('evalBox').innerHTML = '<div class="muted">评估失败</div>';
    }
}

async function loadPool() {
    const list = await api('/api/crypto/pool').catch((e) => {
        setStatus('加载监控池失败: ' + e.message, true);
        return [];
    });
    const body = $('poolBody');
    body.innerHTML = '';
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="4" class="muted">暂无交易对，添加 BTCUSDT 试试</td></tr>';
        return;
    }
    list.forEach(s => {
        const tr = document.createElement('tr');
        tr.dataset.symbol = s.symbol;
        tr.innerHTML = `
            <td class="switch" data-act="view">${s.symbol}</td>
            <td class="switch" data-act="view">${s.name || ''}</td>
            <td>${s.enabled ? '<span style="color:#389e0d">启用</span>' : '<span class="muted">停用</span>'}</td>
            <td>
                <a class="switch" data-act="toggle" data-id="${s.id}" data-enabled="${s.enabled}">${s.enabled ? '停用' : '启用'}</a>
                &nbsp;<a class="del" data-act="del" data-id="${s.id}">删除</a>
            </td>`;
        tr.querySelectorAll('[data-act="view"]').forEach(el =>
            el.addEventListener('click', () => loadChart(s.symbol, s.name)));
        body.appendChild(tr);
    });
    body.querySelectorAll('[data-act="toggle"]').forEach(el =>
        el.addEventListener('click', async () => {
            await api(`/api/crypto/pool/${el.dataset.id}/enabled?enabled=${el.dataset.enabled !== 'true'}`, { method: 'PUT' });
            loadPool();
        }));
    body.querySelectorAll('[data-act="del"]').forEach(el =>
        el.addEventListener('click', async () => {
            if (!confirm('确认从监控池删除？')) return;
            await api(`/api/crypto/pool/${el.dataset.id}`, { method: 'DELETE' });
            loadPool();
        }));
}

function highlightPoolRow(symbol) {
    document.querySelectorAll('#poolBody tr').forEach(tr =>
        tr.classList.toggle('row-active', tr.dataset.symbol === symbol));
}

function normalizeInput(raw) {
    return (raw || '').trim().toUpperCase().replace(/[-_/\s]/g, '');
}

async function addSymbol(e) {
    e.preventDefault();
    const symbol = normalizeInput($('symbolInput').value);
    const group = $('groupInput').value.trim();
    if (!/^[A-Z0-9]{5,20}$/.test(symbol)) {
        setStatus('请输入币安现货交易对，如 BTCUSDT', true);
        return;
    }
    try {
        setStatus('添加并回补中...');
        await api('/api/crypto/pool', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ symbol, group: group || null })
        });
        $('symbolInput').value = '';
        $('groupInput').value = '';
        setStatus('已添加 ' + symbol);
        await loadPool();
        await loadChart(symbol, symbol);
    } catch (err) {
        setStatus('添加失败: ' + err.message, true);
    }
}

function intervalLabel(it) {
    return INTERVAL_LABEL[it] || it || '';
}

function fmtBarTime(s) {
    if (s.openTime) return String(s.openTime).replace('T', ' ').slice(0, 16);
    return s.tradeDate || '';
}

async function loadSignals() {
    const interval = $('fltInterval').value;
    const type = $('fltType').value;
    const symbol = normalizeInput($('fltSymbol').value);
    const params = new URLSearchParams();
    if (interval) params.set('interval', interval);
    if (type) params.set('type', type);
    if (symbol) params.set('symbol', symbol);
    let list;
    try {
        list = await api('/api/crypto/signals?' + params.toString());
    } catch (e) {
        setStatus('查询失败: ' + e.message, true);
        return;
    }
    const body = $('signalBody');
    body.innerHTML = '';
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="7" class="muted">暂无信号</td></tr>';
        return;
    }
    list.forEach(s => {
        const buy = s.signalType === 'BUY_LOW9';
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${fmtBarTime(s)}</td>
            <td>${intervalLabel(s.interval)}</td>
            <td>${s.symbol}</td>
            <td><span class="tag ${buy ? 'buy' : 'sell'}">${buy ? '低9抄底' : '高9逃顶'}</span>${s.strong ? '<span class="tag strong">强</span>' : ''}</td>
            <td>${s.score}/${s.maxScore}</td>
            <td class="muted">${(s.hitFactors || '').replace(/,/g, ' / ')}</td>
            <td>${s.notified ? '✓' : ''}</td>`;
        tr.style.cursor = 'pointer';
        tr.addEventListener('click', () => {
            currentInterval = s.interval || currentInterval;
            syncIntervalButtons();
            loadChart(s.symbol, s.name);
        });
        body.appendChild(tr);
    });
}

function syncIntervalButtons() {
    document.querySelectorAll('.interval-switch button').forEach(btn =>
        btn.classList.toggle('active', btn.dataset.interval === currentInterval));
}

function switchInterval(next) {
    currentInterval = next;
    syncIntervalButtons();
    if (currentSymbol) loadChart(currentSymbol, currentName);
}

async function tickAutoRefresh() {
    let st;
    try {
        st = await api('/api/crypto/status');
    } catch (e) {
        $('autoStatus').textContent = '';
        return;
    }
    if (st.active) {
        $('autoStatus').textContent = '● 24h监控·自动刷新';
        $('autoStatus').style.color = '#7CFC8A';
        await Promise.all([refreshLive(), loadSignals()]);
    } else {
        $('autoStatus').textContent = '○ 加密扫描已关闭';
        $('autoStatus').style.color = '';
    }
}

function init() {
    initChart();
    $('addForm').addEventListener('submit', addSymbol);
    $('btnQuery').addEventListener('click', loadSignals);
    $('btnD1').addEventListener('click', () => switchInterval('D1'));
    $('btnH4').addEventListener('click', () => switchInterval('H4'));
    $('btnBackfill').addEventListener('click', async () => {
        if (!currentSymbol) return;
        setStatus('回补中...');
        try {
            const r = await api(`/api/crypto/kline/backfill?symbol=${encodeURIComponent(currentSymbol)}&interval=${currentInterval}`, { method: 'POST' });
            setStatus(`回补完成 ${r.saved} 根`);
            loadChart(currentSymbol, currentName);
        } catch (e) { setStatus('回补失败: ' + e.message, true); }
    });
    $('btnScan').addEventListener('click', async () => {
        setStatus('扫描中...');
        try {
            const hits = await api('/api/crypto/scan', { method: 'POST' });
            setStatus(`扫描完成，命中 ${hits.length} 条`);
            loadSignals();
            if (currentSymbol) loadEval(currentSymbol);
        } catch (e) { setStatus('扫描失败: ' + e.message, true); }
    });
    loadPool();
    loadSignals();
    tickAutoRefresh();
    setInterval(tickAutoRefresh, REFRESH_MS);
}

document.addEventListener('DOMContentLoaded', init);
