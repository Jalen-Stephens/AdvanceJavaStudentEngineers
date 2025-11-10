(() => {
  const DEFAULT_CONFIG = { apiBaseUrl: 'http://localhost:8080' };
  const config = Object.assign({}, DEFAULT_CONFIG, window.APP_CONFIG || {});
  const apiBase = config.apiBaseUrl?.replace(/\/$/, '') || DEFAULT_CONFIG.apiBaseUrl;
  const TOKEN_STORAGE_KEY = 'pulse-demo-token';

  const tokenInput = document.getElementById('token-input');
  const fileInput = document.getElementById('file-input');
  const fileName = document.getElementById('file-name');
  const captionInput = document.getElementById('caption-input');
  const labelsInput = document.getElementById('labels-input');
  const uploadForm = document.getElementById('upload-form');
  const statusEl = document.getElementById('status');
  const feedGrid = document.getElementById('feed-grid');
  const feedCount = document.getElementById('feed-count');
  const refreshBtn = document.getElementById('refresh-feed');
  const instanceLabel = document.getElementById('instance-label');
  const tokenBadge = document.getElementById('token-badge');
  const editTokenBtn = document.getElementById('edit-token');
  const tokenEditor = document.getElementById('token-edit');
  const saveTokenBtn = document.getElementById('save-token');
  const cancelTokenBtn = document.getElementById('cancel-token');

  instanceLabel.textContent = apiBase.replace(/^https?:\/\//, '');
  let cachedToken = localStorage.getItem(TOKEN_STORAGE_KEY) || '';

  const getToken = () => cachedToken.trim();

  const persistToken = (value) => {
    cachedToken = value.trim();
    localStorage.setItem(TOKEN_STORAGE_KEY, cachedToken);
    updateTokenBadge();
  };

  const updateTokenBadge = () => {
    const hasToken = !!getToken();
    if (tokenBadge) {
      tokenBadge.textContent = hasToken ? 'Linked' : 'Not linked';
      tokenBadge.classList.toggle('on', hasToken);
      tokenBadge.classList.toggle('off', !hasToken);
    }
  };

  const showTokenEditor = () => {
    if (!tokenEditor) {
      return;
    }
    tokenInput.value = getToken();
    tokenInput.classList.remove('error');
    tokenEditor.classList.remove('hidden');
  };

  const hideTokenEditor = () => {
    if (!tokenEditor) {
      return;
    }
    tokenEditor.classList.add('hidden');
    tokenInput.classList.remove('error');
  };

  updateTokenBadge();

  const ensureToken = () => {
    if (!getToken()) {
      updateStatus('Log in via Pulse (or add a token) before continuing.', 'error');
      showTokenEditor();
      return false;
    }
    return true;
  };

  if (editTokenBtn) {
    editTokenBtn.addEventListener('click', showTokenEditor);
  }

  if (cancelTokenBtn) {
    cancelTokenBtn.addEventListener('click', () => {
      hideTokenEditor();
    });
  }

  if (saveTokenBtn) {
    saveTokenBtn.addEventListener('click', () => {
      const raw = tokenInput.value.trim();
      if (!raw) {
        tokenInput.classList.add('error');
        return;
      }
      persistToken(raw);
      hideTokenEditor();
      updateStatus('Token updated locally.', 'success');
      loadFeed();
    });
  }

  tokenInput?.addEventListener('input', () => {
    tokenInput.classList.remove('error');
  });

  fileInput.addEventListener('change', () => {
    fileName.textContent = fileInput.files?.[0]?.name || 'No file selected';
  });

  const updateStatus = (message, tone) => {
    statusEl.textContent = message || '';
    statusEl.className = `status ${tone || ''}`.trim();
  };

  const formatTimestamp = (value) => {
    if (!value) {
      return '—';
    }
    try {
      const date = new Date(value);
      return date.toLocaleString(undefined, {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
      });
    } catch (err) {
      return value;
    }
  };

  const parseLabels = (raw) => {
    if (!raw) {
      return null;
    }
    const labels = raw.split(',')
      .map((chunk) => chunk.trim().replace(/^#/, ''))
      .filter(Boolean);
    return labels.length ? labels : null;
  };

  const authorizedFetch = (path, init = {}) => {
    const token = getToken();
    const headers = new Headers(init.headers || {});
    headers.set('Authorization', `Bearer ${token}`);
    return fetch(`${apiBase}${path}`, { ...init, headers });
  };

  const requestJson = async (path, init = {}) => {
    if (!ensureToken()) {
      throw new Error('Missing bearer token');
    }
    const response = await authorizedFetch(path, init);
    const text = await response.text();
    let data = text;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (err) {
      // leave as string
    }
    if (!response.ok) {
      const err = new Error(`Request failed with ${response.status}`);
      err.status = response.status;
      err.payload = data;
      throw err;
    }
    return data;
  };

  const buildCard = (image) => {
    const card = document.createElement('article');
    card.className = 'post-card';
    card.dataset.id = image.id;

    const media = document.createElement('div');
    media.className = 'post-media';
    const img = document.createElement('img');
    img.alt = image.filename || 'Uploaded asset';
    img.src = image.signedUrl || 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="400" height="300"/%3E';
    media.appendChild(img);

    const body = document.createElement('div');
    body.className = 'post-body';

    const ts = document.createElement('span');
    ts.className = 'timestamp';
    ts.textContent = formatTimestamp(image.uploadedAt);

    const caption = document.createElement('p');
    caption.className = 'caption';
    caption.textContent = image.note || 'No caption yet.';

    const labelsWrap = document.createElement('div');
    labelsWrap.className = 'labels';
    if (Array.isArray(image.labels) && image.labels.length) {
      image.labels.forEach((label) => {
        const pill = document.createElement('span');
        pill.textContent = `#${label}`;
        labelsWrap.appendChild(pill);
      });
    }

    const actions = document.createElement('div');
    actions.className = 'post-actions';
    const del = document.createElement('button');
    del.className = 'delete-btn';
    del.type = 'button';
    del.textContent = 'Delete';
    del.addEventListener('click', () => deleteImage(image.id));
    actions.appendChild(del);

    body.append(ts, caption, labelsWrap, actions);
    card.append(media, body);
    return card;
  };

  const renderFeed = (images) => {
    feedGrid.innerHTML = '';
    if (!images.length) {
      const empty = document.createElement('p');
      empty.textContent = 'No posts yet — upload something above!';
      empty.className = 'hint';
      feedGrid.appendChild(empty);
    } else {
      images.forEach((image) => feedGrid.appendChild(buildCard(image)));
    }
    feedCount.textContent = `${images.length} ${images.length === 1 ? 'post' : 'posts'}`;
  };

  const fetchSignedUrl = async (imageId) => {
    try {
      const data = await requestJson(`/api/images/${imageId}/url`);
      return data?.url;
    } catch (err) {
      return null;
    }
  };

  const loadFeed = async () => {
    if (!ensureToken()) {
      return;
    }
    updateStatus('Loading feed…');
    try {
      const images = await requestJson('/api/images?page=0&size=12');
      const enriched = await Promise.all(
        images.map(async (image) => ({
          ...image,
          signedUrl: await fetchSignedUrl(image.id)
        }))
      );
      renderFeed(enriched);
      updateStatus('Feed refreshed', 'success');
    } catch (err) {
      console.error(err);
      updateStatus(`Unable to load feed (HTTP ${err.status || '??'}).`, 'error');
    }
  };

  const deleteImage = async (imageId) => {
    if (!ensureToken()) {
      return;
    }
    const confirmed = window.confirm('Delete this post permanently?');
    if (!confirmed) {
      return;
    }
    updateStatus('Deleting post…');
    try {
      await requestJson(`/api/images/${imageId}`, { method: 'DELETE' });
      updateStatus('Post removed', 'success');
      await loadFeed();
    } catch (err) {
      console.error(err);
      updateStatus(`Failed to delete (HTTP ${err.status || '??'}).`, 'error');
    }
  };

  const handleUpload = async (event) => {
    event.preventDefault();
    if (!ensureToken()) {
      return;
    }

    const file = fileInput.files?.[0];
    if (!file) {
      updateStatus('Pick an image before posting.', 'error');
      return;
    }

    const caption = captionInput.value.trim();
    const labels = parseLabels(labelsInput.value.trim());

    const formData = new FormData();
    formData.append('file', file);

    updateStatus('Uploading photo…');
    uploadForm.querySelector('button[type="submit"]').disabled = true;

    try {
      const response = await authorizedFetch('/api/images/upload', {
        method: 'POST',
        body: formData
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload?.message || 'Upload failed');
      }

      if (caption || labels) {
        try {
          await requestJson(`/api/images/${payload.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ note: caption || null, labels })
          });
        } catch (err) {
          console.warn('Metadata update failed', err);
        }
      }

      fileInput.value = '';
      fileName.textContent = 'No file selected';
      captionInput.value = '';
      labelsInput.value = '';

      updateStatus('Post published ✨', 'success');
      await loadFeed();
    } catch (err) {
      console.error(err);
      updateStatus('Upload failed — see console for details.', 'error');
    } finally {
      uploadForm.querySelector('button[type="submit"]').disabled = false;
    }
  };

  uploadForm.addEventListener('submit', handleUpload);
  refreshBtn.addEventListener('click', loadFeed);

  if (getToken()) {
    loadFeed();
  }
})();
