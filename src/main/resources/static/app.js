'use strict';

const $ = (id) => document.getElementById(id);
let chart = null;
let currentCode = null;
let currentName = null;
const REFRESH_MS = 60000;

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

function ts(dateStr) {
    return new Date(dateStr + 'T00:00:00').getTime();
}

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
    if (value == null || isNaN(value)) return;
    chart.createOverlay({
        name: 'simpleAnnotation',
        points: [{ timestamp: ts(dateStr), value: Number(value) }],
        extendData: text
    });
}

async function loadChart(code, name) {
    currentCode = code;
    currentName = name;
    $('btnBackfill').disabled = false;
    $('chartTitle').textContent = `K线图 · ${name || ''}(${code})`;
    highlightPoolRow(code);
    let bars;
    try {
        bars = await api(`/api/indicator/realtime?code=${code}`);
    } catch (e) {
        setStatus('加载指标失败: ' + e.message, true);
        return;
    }
    if (!bars || !bars.length) {
        $('chartHint').textContent = '暂无日K数据，请点击「回补日K」';
        $('chartHint').style.display = '';
        return;
    }
    $('chartHint').style.display = 'none';
    chart.applyNewData(bars.map(toKline));
    await drawOverlays(code, bars);
    loadNews(code, false);
    loadDiag(code, false);
}

/* ---------------- AI 诊股（精简版） ---------------- */
function scoreColor(n) {
    if (n == null) return 'var(--muted)';
    if (n >= 65) return '#cf1322';
    if (n <= 40) return '#08979c';
    return '#d46b08';
}

function stanceCls(s) {
    if (s && s.indexOf('多') >= 0) return 'buy';
    if (s && s.indexOf('空') >= 0) return 'sell';
    return 'none';
}

function renderDiag(d) {
    const el = $('diag');
    if (!d) { el.innerHTML = '<div class="muted">诊断失败</div>'; return; }
    $('diagState').textContent = d.aiUsed ? 'AI研判' : '规则降级';
    const a = d.action || {};
    const act = (a.direction || a.position)
        ? `<div class="muted" style="font-size:12px;margin-top:4px">建议：${a.direction || ''}${a.position ? ' · ' + a.position : ''}${a.stop != null ? ' · 止损' + Number(a.stop).toFixed(2) : ''}${a.target != null ? ' · 目标' + Number(a.target).toFixed(2) : ''}</div>`
        : '';
    el.innerHTML = `
        <div class="diag-mini">
            <span class="s" style="color:${scoreColor(d.score)}">${d.score == null ? '-' : d.score}</span>
            <span class="tag ${stanceCls(d.stance)}">${d.stance || '-'}</span>
        </div>
        <div style="margin-top:6px;line-height:1.5">${d.summary || ''}</div>
        ${act}`;
}

async function loadDiag(code, refresh) {
    const el = $('diag');
    if (!code) return;
    el.innerHTML = `<div class="muted">${refresh ? 'AI诊断中…' : '加载中…'}</div>`;
    let d;
    try {
        d = await api(`/api/diagnosis/${code}?refresh=${refresh ? 'true' : 'false'}`);
    } catch (e) { el.innerHTML = '<div class="muted">诊断失败</div>'; return; }
    renderDiag(d);
}

/* ---------------- 舆情 ---------------- */
const SENTI_CN = { BULLISH: '利好', BEARISH: '利空', NEUTRAL: '中性', UNKNOWN: '待分析' };

function newsRow(it) {
    const s = it.sentiment || 'UNKNOWN';
    const when = (it.publishTime || '').replace('T', ' ').slice(0, 16);
    const conf = it.confidence != null ? ` ·置信${Math.round(it.confidence * 100)}%` : '';
    const reason = it.reason ? ` · ${it.reason}` : '';
    return `<div class="news-item">
        <span class="senti ${s}">${SENTI_CN[s] || s}</span>
        <a href="${it.url}" target="_blank" rel="noopener">${it.title || ''}</a>
        <div class="news-meta">${it.source || ''} · ${when}${conf}${reason}</div>
    </div>`;
}

async function loadNews(code, refresh) {
    const el = $('newsList');
    if (!code) return;
    if (refresh) el.innerHTML = '<div class="muted">抓取中…</div>';
    let data;
    try {
        data = await api(`/api/news/${code}?refresh=${refresh ? 'true' : 'false'}&limit=10`);
    } catch (e) { el.innerHTML = '<div class="muted">获取失败</div>'; return; }
    $('aiState').textContent = data.aiReady ? 'AI已启用' : 'AI未启用(仅入库)';
    const items = data.items || [];
    el.innerHTML = items.length
        ? items.map(newsRow).join('')
        : '<div class="muted">暂无新闻，点「刷新」抓取</div>';
}

function toKline(b) {
    return { timestamp: ts(b.tradeDate), open: b.open, high: b.high, low: b.low, close: b.close, volume: b.volume };
}

async function drawOverlays(code, bars) {
    chart.removeOverlay();
    bars.forEach(b => {
        if (b.tdSignal === 'LOW_9') addAnno(b.tradeDate, b.low, '低9');
        else if (b.tdSignal === 'HIGH_9') addAnno(b.tradeDate, b.high, '高9');
    });
    try {
        const sigs = await api(`/api/signals?code=${code}`);
        sigs.forEach(s => {
            let price = null;
            try { price = JSON.parse(s.detailJson).close; } catch (e) { /* ignore */ }
            const txt = (s.signalType === 'BUY_LOW9' ? '抄底' : '逃顶') + (s.strong ? '★' : '');
            addAnno(s.tradeDate, price, txt);
        });
    } catch (e) { /* 信号可选 */ }
}

/* 盘中刷新：只更新最新一根K线，保留缩放视图，再重绘标注 */
async function refreshLive() {
    if (!currentCode) return;
    let bars;
    try {
        bars = await api(`/api/indicator/realtime?code=${currentCode}`);
    } catch (e) { return; }
    if (!bars || !bars.length) return;
    chart.updateData(toKline(bars[bars.length - 1]));
    await drawOverlays(currentCode, bars);
}

/* ---------------- 自选股池 ---------------- */
async function loadPool() {
    const list = await api('/api/pool');
    const body = $('poolBody');
    body.innerHTML = '';
    if (!list.length) {
        body.innerHTML = '<tr><td colspan="5" class="muted">暂无自选股，添加一个吧</td></tr>';
        return;
    }
    list.forEach(s => {
        const tr = document.createElement('tr');
        tr.dataset.code = s.code;
        tr.innerHTML = `
            <td class="switch" data-act="view">${s.code}</td>
            <td class="switch" data-act="view">${s.name || ''}</td>
            <td>${s.market || ''}</td>
            <td>${s.enabled ? '<span style="color:#389e0d">启用</span>' : '<span class="muted">停用</span>'}</td>
            <td>
                <a class="switch" data-act="toggle" data-id="${s.id}" data-enabled="${s.enabled}">${s.enabled ? '停用' : '启用'}</a>
                &nbsp;<a class="del" data-act="del" data-id="${s.id}">删除</a>
            </td>`;
        tr.querySelectorAll('[data-act="view"]').forEach(el =>
            el.addEventListener('click', () => loadChart(s.code, s.name)));
        body.appendChild(tr);
    });
    body.querySelectorAll('[data-act="toggle"]').forEach(el =>
        el.addEventListener('click', async () => {
            await api(`/api/pool/${el.dataset.id}/enabled?enabled=${el.dataset.enabled !== 'true'}`, { method: 'PUT' });
            loadPool();
        }));
    body.querySelectorAll('[data-act="del"]').forEach(el =>
        el.addEventListener('click', async () => {
            if (!confirm('确认删除该自选股？')) return;
            await api(`/api/pool/${el.dataset.id}`, { method: 'DELETE' });
            loadPool();
        }));
}

function highlightPoolRow(code) {
    document.querySelectorAll('#poolBody tr').forEach(tr =>
        tr.classList.toggle('row-active', tr.dataset.code === code));
}

async function addStock(e) {
    e.preventDefault();
    const code = $('codeInput').value.trim();
    const group = $('groupInput').value.trim();
    if (!/^\d{6}$/.test(code)) { setStatus('请输入6位数字代码', true); return; }
    try {
        await api('/api/pool', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, group: group || null })
        });
        $('codeInput').value = '';
        $('groupInput').value = '';
        setStatus('已添加 ' + code);
        await loadPool();
        await api(`/api/kline/backfill?code=${code}`, { method: 'POST' });
        setStatus('已添加并回补 ' + code);
    } catch (err) {
        setStatus('添加失败: ' + err.message, true);
    }
}

/* ---------------- 信号列表 ---------------- */
async function loadSignals() {
    const date = $('fltDate').value;
    const type = $('fltType').value;
    const code = $('fltCode').value.trim();
    const params = new URLSearchParams();
    if (date) params.set('date', date);
    if (type) params.set('type', type);
    if (code) params.set('code', code);
    const list = await api('/api/signals?' + params.toString());
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
            <td>${s.tradeDate}</td>
            <td>${s.code}</td>
            <td>${s.name || ''}</td>
            <td><span class="tag ${buy ? 'buy' : 'sell'}">${buy ? '低9抄底' : '高9逃顶'}</span>${s.strong ? '<span class="tag strong">强</span>' : ''}</td>
            <td>${s.score}/${s.maxScore}</td>
            <td class="muted">${(s.hitFactors || '').replace(/,/g, ' / ')}</td>
            <td>${s.notified ? '✓' : ''}</td>`;
        tr.style.cursor = 'pointer';
        tr.addEventListener('click', () => loadChart(s.code, s.name));
        body.appendChild(tr);
    });
}

/* ---------------- 启动 ---------------- */
function init() {
    initChart();
    $('addForm').addEventListener('submit', addStock);
    $('btnQuery').addEventListener('click', loadSignals);
    $('btnBackfill').addEventListener('click', async () => {
        if (!currentCode) return;
        setStatus('回补中...');
        try {
            const r = await api(`/api/kline/backfill?code=${currentCode}`, { method: 'POST' });
            setStatus(`回补完成 ${r.saved} 根`);
            loadChart(currentCode, $('chartTitle').textContent);
        } catch (e) { setStatus('回补失败: ' + e.message, true); }
    });
    $('btnScan').addEventListener('click', async () => {
        setStatus('扫描中...');
        try {
            const hits = await api('/api/signal/scan-realtime', { method: 'POST' });
            setStatus(`扫描完成，命中 ${hits.length} 条`);
            loadSignals();
        } catch (e) { setStatus('扫描失败: ' + e.message, true); }
    });
    $('btnNews').addEventListener('click', () => {
        if (currentCode) loadNews(currentCode, true);
        else setStatus('请先从左侧选择一只股票', true);
    });
    $('btnDiag').addEventListener('click', () => {
        if (currentCode) loadDiag(currentCode, true);
        else setStatus('请先从左侧选择一只股票', true);
    });
    loadPool();
    loadSignals();
    startAutoRefresh();
}

/* 自动刷新：仅交易时段每60秒刷新当前K线与信号列表 */
function startAutoRefresh() {
    tickAutoRefresh();
    setInterval(tickAutoRefresh, REFRESH_MS);
}

async function tickAutoRefresh() {
    let st;
    try {
        st = await api('/api/market/status');
    } catch (e) {
        $('autoStatus').textContent = '';
        return;
    }
    if (st.active) {
        $('autoStatus').textContent = '● 交易中·自动刷新';
        $('autoStatus').style.color = '#7CFC8A';
        await Promise.all([refreshLive(), loadSignals()]);
    } else {
        $('autoStatus').textContent = st.tradingDay ? '○ 非交易时段·已暂停' : '○ 休市·已暂停';
        $('autoStatus').style.color = '';
    }
}

document.addEventListener('DOMContentLoaded', init);
