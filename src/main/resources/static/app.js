'use strict';

const state = {
    mailboxes: [],
    currentMailbox: null,
    messages: [],
    currentMessageId: null,
    // Signatures of the last rendered list state, so polling only rebuilds the DOM
    // when something actually changed (avoids flicker, scroll reset and lost clicks).
    mailboxSig: null,
    messageSig: null,
};

const $ = (id) => document.getElementById(id);
const enc = encodeURIComponent;

async function api(path, options) {
    const res = await fetch(path, options);
    if (!res.ok) {
        throw new Error('HTTP ' + res.status);
    }
    return res.status === 204 ? null : res.json();
}

function fmtDate(millis) {
    if (!millis) return '';
    const d = new Date(millis);
    return d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

function escapeHtml(s) {
    return (s || '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
}

// ---- config -----------------------------------------------------------------

async function loadConfig() {
    try {
        const c = await api('/api/config');
        const parts = [];
        if (c.smtpEnabled) parts.push(`SMTP <b>:${c.smtpPort}</b>`);
        if (c.imapEnabled) parts.push(`IMAP <b>:${c.imapPort}</b>`);
        parts.push(c.forwardEnabled ? `Weiterleitung <b>${escapeHtml(c.forwardHost)}</b>` : 'Weiterleitung <b>aus</b>');
        $('ports').innerHTML = parts.map((p) => `<span>${p}</span>`).join('');
    } catch (e) {
        // config is best-effort
    }
}

// ---- mailboxes --------------------------------------------------------------

async function loadMailboxes() {
    state.mailboxes = await api('/api/mailboxes');
    $('mailboxCount').textContent = `(${state.mailboxes.length})`;
    const sig = JSON.stringify(state.mailboxes) + '|' + state.currentMailbox;
    if (sig === state.mailboxSig) return; // nothing changed, keep the DOM as-is
    state.mailboxSig = sig;
    const ul = $('mailboxList');
    ul.innerHTML = '';
    for (const box of state.mailboxes) {
        const li = document.createElement('li');
        li.className = box.address === state.currentMailbox ? 'active' : '';
        const badgeClass = box.unseen > 0 ? 'badge' : 'badge zero';
        li.innerHTML = `<div class="mailbox-row">
            <span class="mailbox-addr" title="${escapeHtml(box.address)}">${escapeHtml(box.address)}</span>
            <span class="${badgeClass}">${box.unseen || box.total}</span>
        </div>`;
        li.onclick = () => selectMailbox(box.address);
        ul.appendChild(li);
    }
}

async function selectMailbox(address) {
    state.currentMailbox = address;
    state.currentMessageId = null;
    state.messageSig = null;
    $('messagesTitle').textContent = address;
    hideDetail();
    await loadMailboxes();
    await loadMessages();
}

// ---- messages ---------------------------------------------------------------

async function loadMessages() {
    if (!state.currentMailbox) return;
    state.messages = await api(`/api/mailboxes/${enc(state.currentMailbox)}/messages`);
    const sig = JSON.stringify(state.messages) + '|' + state.currentMessageId;
    if (sig === state.messageSig) return; // nothing changed, keep the DOM as-is
    state.messageSig = sig;
    const ul = $('messageList');
    ul.innerHTML = '';
    for (const m of state.messages) {
        const li = document.createElement('li');
        li.className = m.id === state.currentMessageId ? 'active' : '';
        const subj = m.subject || '(kein Betreff)';
        li.innerHTML = `
            <div class="msg-subject ${m.seen ? '' : 'unread'}">${escapeHtml(subj)}</div>
            <div class="msg-from">${escapeHtml(m.from || '')}</div>
            <div class="msg-date">${fmtDate(m.receivedAt)}</div>`;
        li.onclick = () => selectMessage(m.id);
        ul.appendChild(li);
    }
}

async function selectMessage(id) {
    state.currentMessageId = id;
    const box = state.currentMailbox;
    const m = await api(`/api/mailboxes/${enc(box)}/messages/${enc(id)}`);
    renderDetail(m);
    if (!m.seen) {
        await api(`/api/mailboxes/${enc(box)}/messages/${enc(id)}/seen`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ seen: true }),
        }).catch(() => {});
        loadMailboxes();
        loadMessages();
    } else {
        loadMessages();
    }
}

function hideDetail() {
    $('detail').classList.add('hidden');
    $('detailEmpty').classList.remove('hidden');
}

function renderDetail(m) {
    $('detailEmpty').classList.add('hidden');
    $('detail').classList.remove('hidden');
    $('dSubject').textContent = m.subject || '(kein Betreff)';
    $('dFrom').textContent = m.from || '';
    $('dTo').textContent = (m.recipients || []).join(', ');
    $('dDate').textContent = fmtDate(m.receivedAt);

    const rawUrl = `/api/mailboxes/${enc(m.mailbox)}/messages/${enc(m.id)}/raw`;
    $('rawLink').href = rawUrl;

    const html = m.html || '';
    const text = m.text || '';
    // Render HTML sandboxed via srcdoc to keep message scripts out of the app.
    const htmlPane = $('bodyHtml');
    htmlPane.innerHTML = '';
    if (html) {
        const frame = document.createElement('iframe');
        frame.setAttribute('sandbox', '');
        frame.style.width = '100%';
        frame.style.minHeight = '400px';
        frame.style.border = 'none';
        frame.srcdoc = html;
        htmlPane.appendChild(frame);
    } else {
        htmlPane.textContent = text || '(kein Inhalt)';
    }
    $('bodyText').textContent = text || '(kein Text-Teil)';

    // Attachments
    const at = $('attachments');
    at.innerHTML = '';
    for (const a of m.attachments || []) {
        const link = document.createElement('a');
        link.className = 'attachment';
        link.href = `/api/mailboxes/${enc(m.mailbox)}/messages/${enc(m.id)}/attachments/${a.index}`;
        link.textContent = `📎 ${a.filename} (${Math.round((a.size || 0) / 1024)} KB)`;
        at.appendChild(link);
    }

    setTab(html ? 'html' : 'text');

    $('deleteMsgBtn').onclick = async () => {
        await api(`/api/mailboxes/${enc(m.mailbox)}/messages/${enc(m.id)}`, { method: 'DELETE' });
        state.currentMessageId = null;
        hideDetail();
        loadMailboxes();
        loadMessages();
    };
}

function setTab(tab) {
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
    $('bodyHtml').classList.toggle('hidden', tab !== 'html');
    $('bodyText').classList.toggle('hidden', tab !== 'text');
}

// ---- compose ----------------------------------------------------------------

function openCompose() {
    $('cError').textContent = '';
    $('composeModal').classList.remove('hidden');
}

function closeCompose() {
    $('composeModal').classList.add('hidden');
}

async function sendMessage() {
    const to = $('cTo').value.split(',').map((s) => s.trim()).filter(Boolean);
    if (to.length === 0) {
        $('cError').textContent = 'Mindestens ein Empfänger nötig.';
        return;
    }
    try {
        await api('/api/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                from: $('cFrom').value.trim(),
                to,
                subject: $('cSubject').value,
                text: $('cText').value,
            }),
        });
        closeCompose();
        $('cTo').value = '';
        $('cSubject').value = '';
        $('cText').value = '';
        await loadMailboxes();
        if (to[0]) await selectMailbox(to[0].toLowerCase());
    } catch (e) {
        $('cError').textContent = 'Senden fehlgeschlagen: ' + e.message;
    }
}

// ---- wiring -----------------------------------------------------------------

function init() {
    $('composeBtn').onclick = openCompose;
    $('cCancel').onclick = closeCompose;
    $('cSend').onclick = sendMessage;
    $('refreshBtn').onclick = refresh;
    document.querySelectorAll('.tab').forEach((t) => {
        t.onclick = () => setTab(t.dataset.tab);
    });
    $('composeModal').onclick = (e) => {
        if (e.target === $('composeModal')) closeCompose();
    };

    loadConfig();
    refresh();
    setInterval(refresh, 5000);
}

async function refresh() {
    await loadMailboxes();
    if (state.currentMailbox) await loadMessages();
}

document.addEventListener('DOMContentLoaded', init);
