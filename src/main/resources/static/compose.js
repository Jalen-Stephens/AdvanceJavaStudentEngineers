(() => {
  // Use same-origin API base (works locally and on Heroku)
  const apiBase = '';
  const TOKEN_STORAGE_KEY = 'pulse-demo-token';

  const fileInput = document.getElementById('file-input');
  const fileName = document.getElementById('file-name');
  const captionInput = document.getElementById('caption-input');
  const labelsInput = document.getElementById('labels-input');
  const uploadForm = document.getElementById('upload-form');
  const statusEl = document.getElementById('status');
  const feedGrid = document.getElementById('feed-grid');
  const feedCount = document.getElementById('feed-count');
  const refreshBtn = document.getElementById('refresh-feed');
  const cachedToken = localStorage.getItem(TOKEN_STORAGE_KEY) || '';

  const getToken = () => cachedToken.trim();

  const ensureToken = () => {
    if (!getToken()) {
      updateStatus('Log in via Pulse before continuing.', 'error');
      return false;
    }
    return true;
  };

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

  const buildAiBadges = (image) => {
    const wrap = document.createElement('div');
    wrap.className = 'ai-flags';

    if (typeof image.confidenceScore === 'number') {
      const scorePill = document.createElement('span');
      scorePill.className = 'pill score';
      scorePill.textContent = `Score: ${image.confidenceScore.toFixed(2)}`;
      wrap.appendChild(scorePill);
    }

    if (image.aiTag) {
      const tagPill = document.createElement('span');
      tagPill.className = `pill ${image.aiTagClass || 'ai'}`;
      tagPill.textContent = image.aiTag;
      wrap.appendChild(tagPill);
    }
    return wrap;
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

    // Overlay banner only for strong AI cases
    if (image.aiTagClass === 'ai-strong') {
      const banner = document.createElement('span');
      banner.className = 'ai-banner';
      banner.textContent = image.aiTag || 'AI generated';
      media.appendChild(banner);
    }

    const body = document.createElement('div');
    body.className = 'post-body';

    const ts = document.createElement('span');
    ts.className = 'timestamp';
    ts.textContent = formatTimestamp(image.uploadedAt);

    const caption = document.createElement('p');
    caption.className = 'caption';
    caption.textContent = image.note || 'No caption yet.';

    const aiFlags = buildAiBadges(image);

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

    body.append(ts, caption, aiFlags, labelsWrap, actions);
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

  const analyzeImage = async (imageId) => {
    try {
      const start = await requestJson(`/api/analyze/${imageId}`, { method: 'POST' });
      const analysisId = start?.analysisId;
      if (!analysisId) {
        return { isAiGenerated: false };
      }

      const poll = async (attempt = 0) => {
        const result = await requestJson(`/api/analyze/${analysisId}`);
        if (result?.status && result.status !== 'PENDING') {
          const statusString = result.status.toUpperCase();
          const score = typeof result.confidenceScore === 'number'
            ? result.confidenceScore
            : (typeof result.score === 'number' ? result.score : null);

          let aiTag = null;
          let aiTagClass = null;
          const isDone = statusString === 'DONE';
          if (typeof score === 'number') {
            if (score >= 0.75) {
              aiTag = 'AI generated';
              aiTagClass = 'ai-strong';
            } else if (score >= 0.5) {
              aiTag = 'Likely AI generated';
              aiTagClass = 'ai-soft';
            }
          }

          const aiDetected = isDone && ((typeof score === 'number' && score >= 0.5) || statusString === 'DONE');
          return {
            isAiGenerated: aiDetected,
            confidenceScore: typeof score === 'number' ? Math.min(Math.max(score, 0), 1) : null,
            aiTag,
            aiTagClass
          };
        }
        if (attempt >= 3) {
          return { isAiGenerated: false };
        }
        await new Promise((resolve) => setTimeout(resolve, 600));
        return poll(attempt + 1);
      };

      return await poll();
    } catch (err) {
      console.warn('Analyze failed', err);
      return { isAiGenerated: false };
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
          signedUrl: await fetchSignedUrl(image.id),
          ...(await analyzeImage(image.id))
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
