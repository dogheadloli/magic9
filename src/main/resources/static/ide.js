'use strict';

const REFRESH_MS = 60000;

async function getJson(url) {
    const res = await fetch(url);
    const t = await res.text();
    return t ? JSON.parse(t) : null;
}

function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/* 极简 Python 高亮（针对受控代码，够用即可） */
function hl(line) {
    const hash = line.indexOf('#');
    let code = line, comment = '';
    if (hash >= 0) { code = line.slice(0, hash); comment = line.slice(hash); }
    let h = esc(code)
        .replace(/("[^"]*"|'[^']*')/g, '<span class="str">$1</span>')
        .replace(/\b(import|from|as|def|return|if|elif|else|class|for|in|None|True|False|and|or|not|with|lambda)\b/g, '<span class="kw">$1</span>')
        .replace(/\b(\d+\.?\d*)\b/g, '<span class="num">$1</span>');
    if (comment) h += '<span class="cmt">' + esc(comment) + '</span>';
    return h;
}

function signed(v, suffix) {
    if (v === null || v === undefined) return '0.00' + (suffix || '');
    const n = Number(v);
    return (n > 0 ? '+' : '') + n.toFixed(2) + (suffix || '');
}

function fmtPrice(v) {
    return (v === null || v === undefined) ? '0.00' : Number(v).toFixed(2);
}

const SIG_CODE = { BUY_LOW9: 'LOW9', SELL_HIGH9: 'HIGH9' };
const SIG_CN = { BUY_LOW9: '低9抄底', SELL_HIGH9: '高9逃顶' };
const BATCH_SIZE = 10;

function chunk(arr, n) {
    const out = [];
    for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n));
    return out;
}

/* 每 10 只一批，分批请求腾讯批量行情接口 */
async function loadQuotes() {
    const pool = (await getJson('/api/pool')) || [];
    const codes = pool.filter(p => p.enabled).map(p => p.code);
    const all = [];
    for (const batch of chunk(codes, BATCH_SIZE)) {
        const part = (await getJson('/api/quotes?codes=' + batch.join(','))) || [];
        all.push(...part);
    }
    return all;
}

function pad(n) { return String(n).padStart(2, '0'); }
function nowHMS() { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`; }

function renderEditor(quotes) {
    const top = [
        'import datetime as dt',
        'from analytics.pipeline import Pipeline',
        'from analytics.indicators import TD9, MACD, MA',
        '',
        '# auto-generated market snapshot — do not edit',
        'SNAPSHOT = {'
    ];
    const rows = quotes.map(q =>
        `    "${q.code}": ${fmtPrice(q.price)},   # ${q.name || ''}  ${signed(q.changePct, '%')}`);
    const bottom = [
        '}',
        '',
        'def build(snapshot: dict) -> Pipeline:',
        '    pipe = Pipeline()',
        '    pipe.add(TD9(setup=9)).add(MACD(12, 26, 9)).add(MA([20, 60]))',
        '    return pipe.fit(snapshot)',
        '',
        'if __name__ == "__main__":',
        '    build(SNAPSHOT).run(verbose=True)'
    ];
    const lines = top.concat(rows, bottom);
    const html = lines.map((ln, i) =>
        `<div class="ln-row"><span class="ln">${i + 1}</span><span class="src">${hl(ln) || '&nbsp;'}</span></div>`).join('');
    document.getElementById('editor').innerHTML = html;
    document.getElementById('symbols').textContent = quotes.length + ' symbols';
}

function renderTerminal(quotes, sig) {
    const t = nowHMS();
    const lines = [];
    lines.push('<span class="prompt">$</span> python strategy.py --watch 60');
    lines.push(`<span class="dim">[${t}]</span> compiling analytics... <span class="ok">ok</span>`);
    lines.push(`<span class="dim">[${t}]</span> loaded ${quotes.length} symbols, fitting pipeline`);
    if (sig) {
        const when = (sig.createTime || (sig.tradeDate + 'T00:00')).slice(5, 16).replace('T', ' ');
        lines.push(`analyzer.py:88: <span class="note">note:</span> ${SIG_CODE[sig.signalType] || sig.signalType} matched ${sig.code} (score ${sig.score}/${sig.maxScore}) at ${when}`);
    } else {
        lines.push('analyzer.py: <span class="ok">0 notes</span>, pipeline clean');
    }
    lines.push(`<span class="dim">[${t}]</span> build finished in 0.42s`);
    document.getElementById('term').innerHTML = lines.join('\n');
}

function renderAi(sig) {
    const el = document.getElementById('aiMsg');
    if (sig) {
        const when = (sig.createTime || (sig.tradeDate + 'T00:00')).slice(5, 16).replace('T', ' ');
        el.innerHTML = `Re-ran the pipeline on the latest snapshot. Heads up — the analyzer flagged a diagnostic: `
            + `<code>${SIG_CODE[sig.signalType] || sig.signalType}</code> on <code>${sig.code}</code> `
            + `(${SIG_CN[sig.signalType] || ''}, score ${sig.score}/${sig.maxScore}) at <code>${when}</code>. `
            + `Want me to open <code>analyzer.py</code> at that line?`;
    } else {
        el.innerHTML = `Synced the market snapshot and re-ran the pipeline. No diagnostics this round — everything looks clean.`;
    }
}

function sigWhen(sig) {
    return (sig.createTime || (sig.tradeDate + 'T00:00')).slice(5, 16).replace('T', ' ');
}

function renderStatusBar(sig) {
    const problems = document.getElementById('sbProblems');
    const sb = document.getElementById('sbSig');
    if (sig) {
        problems.innerHTML = '✓ 0&nbsp;&nbsp;⚠ 1';
        sb.textContent = `⚠ ${SIG_CODE[sig.signalType] || sig.signalType} ${sig.code} · ${sigWhen(sig)}`;
    } else {
        problems.innerHTML = '✓ 0&nbsp;&nbsp;⚠ 0';
        sb.textContent = 'no diagnostics';
    }
}

async function refresh() {
    let quotes = [], sig = null;
    try { quotes = await loadQuotes(); } catch (e) { /* keep */ }
    try { const list = await getJson('/api/signals'); if (list && list.length) sig = list[0]; } catch (e) { /* keep */ }
    renderEditor(quotes);
    renderTerminal(quotes, sig);
    renderAi(sig);
    renderStatusBar(sig);
    document.getElementById('clock').textContent = '⟳ ' + nowHMS();
}

refresh();
setInterval(refresh, REFRESH_MS);
