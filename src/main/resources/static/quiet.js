'use strict';

const REFRESH_MS = 60000;

async function getJson(url) {
    const res = await fetch(url);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

function fmtPrice(v) {
    return (v === null || v === undefined) ? '--' : Number(v).toFixed(2);
}

function fmtSigned(v, suffix) {
    if (v === null || v === undefined) return '--';
    const n = Number(v);
    const s = (n > 0 ? '+' : '') + n.toFixed(2);
    return suffix ? s + suffix : s;
}

const SIG_LABEL = { BUY_LOW9: '低9抄底', SELL_HIGH9: '高9逃顶' };
const BATCH_SIZE = 10;

function chunk(arr, n) {
    const out = [];
    for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n));
    return out;
}

function rowHtml(q) {
    return `
        <tr>
            <td class="l name">${q.name || ''}</td>
            <td class="l code">${q.code}</td>
            <td>${fmtPrice(q.price)}</td>
            <td>${fmtSigned(q.change)}</td>
            <td>${fmtSigned(q.changePct, '%')}</td>
        </tr>`;
}

async function loadQuotes() {
    const body = document.getElementById('rows');
    const pool = (await getJson('/api/pool')) || [];
    const codes = pool.filter(p => p.enabled).map(p => p.code);
    if (!codes.length) {
        body.innerHTML = '<tr><td class="l" colspan="5" style="color:#6f747d">暂无数据</td></tr>';
        return;
    }
    // 每 10 只一批，分批请求腾讯批量行情接口，逐批渲染
    const all = [];
    for (const batch of chunk(codes, BATCH_SIZE)) {
        const part = (await getJson('/api/quotes?codes=' + batch.join(','))) || [];
        all.push(...part);
        body.innerHTML = all.map(rowHtml).join('');
    }
}

async function loadLastSignal() {
    const el = document.getElementById('lastSignal');
    try {
        const list = await getJson('/api/signals');
        if (!list || !list.length) {
            el.textContent = '最后信号 无';
            return;
        }
        const s = list[0];
        const when = (s.createTime || (s.tradeDate + 'T00:00')).slice(5, 16).replace('T', ' ');
        const type = SIG_LABEL[s.signalType] || s.signalType;
        el.textContent = `最后信号 ${when} ${type}（${s.code}）`;
    } catch (e) {
        el.textContent = '最后信号 无';
    }
}

function fmtNow() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

async function refresh() {
    try {
        await Promise.all([loadQuotes(), loadLastSignal()]);
    } catch (e) { /* 静默 */ }
    document.getElementById('refresh').textContent = '最后刷新 ' + fmtNow();
}

refresh();
setInterval(refresh, REFRESH_MS);
