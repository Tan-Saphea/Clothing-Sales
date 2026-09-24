/**
 * Clothing Sales Management System - Global Application Script
 * Features: Database Diagnostics (Check Connection), Toast Notifications, Modal Utilities, Table Filter
 */

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[char]);
}

// Toast Notifications
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast-custom ${type}`;

  const body = document.createElement('div');
  body.className = 'flex-grow-1';
  body.textContent = message;
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'btn-close ms-2';
  close.setAttribute('aria-label', 'Close');
  toast.append(body, close);

  toast.querySelector('.btn-close').onclick = () => toast.remove();
  container.appendChild(toast);

  setTimeout(() => {
    if (toast.parentNode) {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }
  }, 4000);
}

// Database Connection Diagnostics
async function checkDatabaseConnection(interactive = false) {
  const pill = document.getElementById('topbar-db-pill');
  const pillText = document.getElementById('topbar-db-text');
  const modalEl = document.getElementById('dbCheckModal');
  const modalBody = document.getElementById('dbCheckModalBody');

  if (pillText) {
    pillText.textContent = 'Checking...';
  }

  const startMs = Date.now();
  try {
    const response = await fetch('/api/test-db');
    const data = await response.json();
    const elapsed = data.latencyMs || (Date.now() - startMs);

    if (response.ok && data.status === 'success') {
      if (pill) {
        pill.className = 'connection-pill connected';
      }
      if (pillText) {
        pillText.textContent = `Database connected (${elapsed}ms)`;
      }

      if (modalBody) {
        modalBody.innerHTML = `
          <div class="p-3 bg-light rounded-3 mb-3 border">
            <div class="d-flex align-items-center justify-content-between mb-2">
              <span class="text-muted small text-uppercase fw-bold">Connection State</span>
              <span class="badge bg-success px-3 py-2 fw-bold">[ONLINE] CONNECTED</span>
            </div>
            <div class="d-flex align-items-center justify-content-between mb-2">
              <span class="text-muted small text-uppercase fw-bold">Response Latency</span>
              <span class="fw-bold text-success">${Number(elapsed)} ms</span>
            </div>
            <div class="d-flex align-items-center justify-content-between mb-2">
              <span class="text-muted small text-uppercase fw-bold">Database Service</span>
              <span class="badge bg-primary px-2 py-1">${escapeHtml(data.service || 'Unknown')}</span>
            </div>
            <div class="d-flex align-items-center justify-content-between">
              <span class="text-muted small text-uppercase fw-bold">Schema User</span>
              <span class="fw-semibold text-dark">${escapeHtml(data.user || 'Unknown')}</span>
            </div>
          </div>

          <div class="row g-2 text-center mb-3">
            <div class="col-4">
              <div class="p-2 border rounded-2 bg-white shadow-sm">
                <div class="fs-4 fw-bold text-primary">${Number(data.tables ?? 0)}</div>
                <div class="small text-muted fw-semibold">Tables</div>
              </div>
            </div>
            <div class="col-4">
              <div class="p-2 border rounded-2 bg-white shadow-sm">
                <div class="fs-4 fw-bold text-info">${Number(data.views ?? 0)}</div>
                <div class="small text-muted fw-semibold">Views</div>
              </div>
            </div>
            <div class="col-4">
              <div class="p-2 border rounded-2 bg-white shadow-sm">
                <div class="fs-4 fw-bold text-warning">${Number(data.routines ?? 0)}</div>
                <div class="small text-muted fw-semibold">Procedures</div>
              </div>
            </div>
          </div>

          <div class="small text-muted border-top pt-2">
            <div><strong>Engine:</strong> ${escapeHtml(data.version || 'Oracle Database')}</div>
            <div><strong>Server time:</strong> ${escapeHtml(data.serverTime || '')}</div>
          </div>
        `;
      }

      if (interactive) {
        showToast(`Database ping successful! Latency: ${elapsed}ms`, 'success');
      }
    } else {
      throw new Error(data.message || 'Database returned unhealthy response');
    }
  } catch (err) {
    if (pill) {
      pill.className = 'connection-pill disconnected';
    }
    if (pillText) {
      pillText.textContent = 'Disconnected';
    }

      if (modalBody) {
        modalBody.replaceChildren();
        const message = document.createElement('div');
        message.className = 'alert alert-danger mb-0';
        message.textContent = `Database connection failed: ${err.message}`;
        modalBody.appendChild(message);
      }

    if (interactive) {
      showToast(`Connection check failed: ${err.message}`, 'error');
    }
  }

  if (interactive && modalEl) {
    const bsModal = bootstrap.Modal.getOrCreateInstance(modalEl);
    bsModal.show();
  }
}

// Table Search Filter
function setupTableSearch(inputId, tableId) {
  const input = document.getElementById(inputId);
  const table = document.getElementById(tableId);
  if (!input || !table) return;

  input.addEventListener('input', function() {
    const q = this.value.toLowerCase().trim();
    const rows = table.querySelectorAll('tbody tr');
    rows.forEach(row => {
      const text = row.innerText.toLowerCase();
      row.style.display = text.includes(q) ? '' : 'none';
    });
  });
}

// Generic API Call helper
async function apiRequest(url, method = 'GET', data = null) {
  const options = {
    method: method,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    }
  };
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (csrfToken && csrfHeader && !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    options.headers[csrfHeader] = csrfToken;
  }
  if (data && (method === 'POST' || method === 'PUT')) {
    options.body = JSON.stringify(data);
  }

  const response = await fetch(url, options);
  if (!response.ok) {
    let errorMsg = `Server error (${response.status})`;
    try {
      const json = await response.json();
      if (json.error) errorMsg = json.error;
      else if (json.message) errorMsg = json.message;
    } catch (_) {}
    throw new Error(errorMsg);
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return await response.json();
  }
  return null;
}

// Global initialization
document.addEventListener('DOMContentLoaded', function() {
  const pill = document.getElementById('topbar-db-pill');
  if (pill) {
    checkDatabaseConnection(false);
    pill.addEventListener('click', () => checkDatabaseConnection(true));
  }
});
