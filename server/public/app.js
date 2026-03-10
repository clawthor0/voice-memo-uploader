function fmtDate(v) {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString();
}

function escapeHtml(str) {
  return String(str || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

const listView = document.getElementById('listView');
const detailView = document.getElementById('detailView');
const bodyEl = document.getElementById('recordingsBody');
const categoryFilterEl = document.getElementById('categoryFilter');
const searchInputEl = document.getElementById('searchInput');
const resultsCountEl = document.getElementById('resultsCount');

const BASE_PATH = window.location.pathname.startsWith('/voice') ? '/voice' : '';
const DASHBOARD_PATH = `${BASE_PATH}/dashboard`;

let allRecordings = [];

function statusBadge(status) {
  const clean = (status || 'unknown').toLowerCase();
  return `<span class="badge status-${clean}">${escapeHtml(status || 'unknown')}</span>`;
}

function categoryBadge(category) {
  return `<span class="badge">${escapeHtml(category || 'other')}</span>`;
}

function applyFilters() {
  const cat = categoryFilterEl.value;
  const q = searchInputEl.value.trim().toLowerCase();

  const filtered = allRecordings.filter((item) => {
    if (cat && item.category !== cat) return false;
    if (!q) return true;
    const blob = [item.uploadId, item.filename, item.transcript, item.summary, item.category, item.status]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return blob.includes(q);
  });

  resultsCountEl.textContent = `${filtered.length} of ${allRecordings.length} recording(s)`;

  bodyEl.innerHTML = filtered.map((item, i) => {
    const tid = `t${i}`;
    const transcript = item.transcript || '';
    return `<tr>
      <td><a class="link" href="${DASHBOARD_PATH}/recordings/${encodeURIComponent(item.uploadId)}">${escapeHtml(item.uploadId)}</a></td>
      <td>${escapeHtml(item.filename || '—')}</td>
      <td>${fmtDate(item.createdAt || item.processedAt || item.updatedAt)}</td>
      <td>${escapeHtml(item.summary || '—')}</td>
      <td>${categoryBadge(item.category)}</td>
      <td>${statusBadge(item.status)}</td>
      <td>
        <button class="transcript-toggle" data-target="${tid}">${transcript ? 'Show transcript' : 'No transcript'}</button>
        <div id="${tid}" class="transcript hidden">${escapeHtml(transcript || '')}</div>
      </td>
    </tr>`;
  }).join('');

  document.querySelectorAll('.transcript-toggle').forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = document.getElementById(btn.dataset.target);
      if (!target) return;
      target.classList.toggle('hidden');
      btn.textContent = target.classList.contains('hidden') ? 'Show transcript' : 'Hide transcript';
    });
  });
}

async function loadList() {
  const res = await fetch(`${BASE_PATH}/api/recordings`);
  if (!res.ok) throw new Error('Failed to load recordings');
  const data = await res.json();
  allRecordings = data.recordings || [];

  const categories = [...new Set(allRecordings.map((r) => r.category).filter(Boolean))].sort();
  categoryFilterEl.innerHTML = '<option value="">All categories</option>' +
    categories.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');

  applyFilters();
}

async function loadDetail(uploadId) {
  const res = await fetch(`${BASE_PATH}/api/recordings/${encodeURIComponent(uploadId)}`);
  if (!res.ok) {
    detailView.innerHTML = `<p>Recording not found.</p><a class="link back" href="${DASHBOARD_PATH}">← Back</a>`;
    return;
  }
  const data = await res.json();
  const item = data.recording;
  detailView.innerHTML = `
    <a class="link back" href="${DASHBOARD_PATH}">← Back</a>
    <h2>Recording ${escapeHtml(item.uploadId)}</h2>
    <p><strong>Filename:</strong> ${escapeHtml(item.filename || '—')}</p>
    <p><strong>Date:</strong> ${fmtDate(item.createdAt || item.processedAt || item.updatedAt)}</p>
    <p><strong>Category:</strong> ${categoryBadge(item.category)}</p>
    <p><strong>Status:</strong> ${statusBadge(item.status)}</p>
    <p><strong>Summary:</strong><br/>${escapeHtml(item.summary || '—')}</p>
    <p><strong>Transcript:</strong></p>
    <div class="transcript">${escapeHtml(item.transcript || '—')}</div>
    ${item.error ? `<p><strong>Error:</strong> ${escapeHtml(item.error)}</p>` : ''}
  `;
}

function route() {
  const path = window.location.pathname;
  const detailRegex = BASE_PATH
    ? new RegExp(`^${BASE_PATH}/dashboard/recordings/([^/]+)$`)
    : /^\/dashboard\/recordings\/([^/]+)$/;
  const match = path.match(detailRegex);
  if (match) {
    listView.classList.add('hidden');
    detailView.classList.remove('hidden');
    loadDetail(decodeURIComponent(match[1]));
  } else {
    detailView.classList.add('hidden');
    listView.classList.remove('hidden');
    loadList();
  }
}

searchInputEl.addEventListener('input', applyFilters);
categoryFilterEl.addEventListener('change', applyFilters);
route();
