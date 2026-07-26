const currentUser = () => JSON.parse(localStorage.getItem('user') || 'null');

function notice(text, ok = true) {
    const box = document.getElementById('message');
    if (box) {
        box.className = 'message ' + (ok ? 'success' : 'error');
        box.textContent = text;
    }
}

async function submitItem(event, type) {
    event.preventDefault();
    const user = currentUser();
    if (!user) {
        location.href = 'login.html';
        return;
    }

    const form = event.target;
    const data = new FormData(form);
    data.set('type', type);
    data.set('userId', user.userId);

    const image = data.get('image');
    if (image && image.size > 10 * 1024 * 1024) {
        notice('Image must be 10 MB or smaller.', false);
        return;
    }

    const button = form.querySelector('button[type="submit"], button:not([type])');
    if (button) {
        button.disabled = true;
        button.textContent = 'Uploading...';
    }

    try {
        const response = await fetch(`${API_BASE_URL}/items`, {
            method: 'POST',
            body: data
        });
        const result = await response.json();
        notice(result.message, response.ok);
        if (response.ok) form.reset();
    } catch (error) {
        notice('Unable to submit item: ' + error.message, false);
    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = `Submit ${type} Report`;
        }
    }
}

async function loadItems(my = false) {
    const user = currentUser();
    const url = my ? `${API_BASE_URL}/items?userId=${encodeURIComponent(user.userId)}` : `${API_BASE_URL}/items`;
    const response = await fetch(url);
    const items = await response.json();
    const box = document.getElementById('items');
    box.innerHTML = items.length ? '' : '<div class="card empty"><h3>No items available</h3><p class="muted">New lost and found reports will appear here.</p></div>';

    items.forEach(item => {
        const mine = user && item.userId === user.userId;
        const image = item.imageUrl
            ? `<img class="item-image" src="${escapeHtml(item.imageUrl)}" alt="${escapeHtml(item.itemName)}" onerror="this.outerHTML='<div class=&quot;item-image image-placeholder&quot;>📦</div>'">`
            : '<div class="item-image image-placeholder">📦</div>';

        box.innerHTML += `<article class="card item">
            ${image}
            <div>
                <div><span class="badge ${item.type}">${escapeHtml(item.type)}</span> <span class="badge">${escapeHtml(item.status)}</span></div>
                <h3>${escapeHtml(item.itemName)}</h3>
                <div class="item-meta">
                    <span>📁 ${escapeHtml(item.category)}</span>
                    <span>🎨 ${escapeHtml(item.color || 'Not specified')}</span>
                    <span>📍 ${escapeHtml(item.location || 'Not specified')}</span>
                    <span>📅 ${escapeHtml(item.eventDate || 'Not specified')}</span>
                </div>
                <p>${escapeHtml(item.description || 'No description provided.')}</p>
                ${mine && item.status !== 'RESOLVED' ? `<div class="actions">
                    <button onclick="viewMatches('${item._id}')">Find Matches</button>
                    <button class="secondary" onclick="resolveItem('${item._id}')">Mark Resolved</button>
                    <button class="danger" onclick="deleteItem('${item._id}')">Delete</button>
                </div>` : ''}
            </div>
        </article>`;
    });
}

async function resolveItem(id) {
    const user = currentUser();
    const response = await fetch(`${API_BASE_URL}/items?id=${encodeURIComponent(id)}&userId=${encodeURIComponent(user.userId)}`, { method: 'PUT' });
    const result = await response.json();
    alert(result.message);
    if (response.ok) loadItems(true);
}

async function deleteItem(id) {
    if (!confirm('Delete this post permanently?')) return;
    const user = currentUser();
    const response = await fetch(`${API_BASE_URL}/items?id=${encodeURIComponent(id)}&userId=${encodeURIComponent(user.userId)}`, { method: 'DELETE' });
    const result = await response.json();
    alert(result.message);
    if (response.ok) loadItems(true);
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    }[character]));
}

function viewMatches(id) {
    window.location.href = `matches.html?itemId=${encodeURIComponent(id)}`;
}
