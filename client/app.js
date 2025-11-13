(() => {
  const DEFAULT_CONFIG = { apiBaseUrl: 'http://localhost:8080' };
  const config = Object.assign({}, DEFAULT_CONFIG, window.APP_CONFIG || {});
  const apiBase = config.apiBaseUrl?.replace(/\/$/, '') || DEFAULT_CONFIG.apiBaseUrl;
  const TOKEN_STORAGE_KEY = 'pulse-demo-token';

  const tabs = document.querySelectorAll('.tab');
  const loginForm = document.getElementById('login-form');
  const signupForm = document.getElementById('signup-form');
  const statusEl = document.getElementById('status');
  const responseOutput = document.getElementById('response-output');
  const copyButton = document.getElementById('copy-response');
  const instanceLabel = document.getElementById('instance-label');

  instanceLabel.textContent = apiBase.replace(/^https?:\/\//, '');

  const setMode = (mode) => {
    tabs.forEach((tab) => {
      const isActive = tab.dataset.mode === mode;
      tab.classList.toggle('active', isActive);
      tab.setAttribute('aria-selected', String(isActive));
    });

    loginForm.classList.toggle('hidden', mode !== 'login');
    signupForm.classList.toggle('hidden', mode !== 'signup');
    statusEl.textContent = '';
  };

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => setMode(tab.dataset.mode));
  });

  const updateStatus = (message, tone) => {
    statusEl.textContent = message;
    statusEl.className = `status ${tone || ''}`.trim();
  };

  const updateResponse = (payload) => {
    responseOutput.textContent = typeof payload === 'string'
      ? payload
      : JSON.stringify(payload, null, 2);
  };

  const toggleFormDisabled = (form, disabled) => {
    const button = form.querySelector('button[type="submit"]');
    if (button) {
      if (!button.dataset.defaultLabel) {
        button.dataset.defaultLabel = button.textContent.trim() || 'Submit';
      }
      button.disabled = disabled;
      button.textContent = disabled ? 'Sending…' : button.dataset.defaultLabel;
    }
    form.querySelectorAll('input').forEach((input) => {
      input.disabled = disabled;
    });
  };

  const postJson = async (path, body) => {
    const url = `${apiBase}${path}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });

    const text = await response.text();
    let parsed = text;
    try {
      parsed = JSON.parse(text);
    } catch (err) {
      // plain text; leave as-is
    }

    return { response, parsed };
  };

  const saveAccessTokenIfPresent = (payload) => {
    if (!payload || typeof payload !== 'object') {
      return false;
    }
    const direct = payload.access_token || payload.accessToken;
    const nested = payload.session?.access_token || payload.session?.accessToken;
    const token = direct || nested;
    if (token) {
      try {
        localStorage.setItem(TOKEN_STORAGE_KEY, token);
        return true;
      } catch (err) {
        console.warn('Unable to persist token', err);
      }
    }
    return false;
  };

  const handleAuthSubmit = async (event, mode) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const email = (data.get('email') || '').toString().trim();
    const password = data.get('password')?.toString();

    if (!email || !password) {
      updateStatus('Email and password are required.', 'error');
      return;
    }

    if (mode === 'signup') {
      const confirm = data.get('confirm')?.toString();
      if (password !== confirm) {
        updateStatus('Passwords must match before continuing.', 'error');
        return;
      }
    }

    toggleFormDisabled(form, true);
    updateStatus('Contacting MetaDetect API…');

    try {
      const path = mode === 'login' ? '/auth/login' : '/auth/signup';
      const { response, parsed } = await postJson(path, { email, password });
      const tokenSaved = response.ok ? saveAccessTokenIfPresent(parsed) : false;
      const prefix = response.ok ? 'Success' : `Error ${response.status}`;
      const suffix = tokenSaved ? ' — access token saved for Pulse Studio' : '';
      updateStatus(`${prefix}: ${response.statusText}${suffix}`, response.ok ? 'success' : 'error');
      updateResponse(parsed);

      if (response.ok && mode === 'login') {
        setTimeout(() => {
          window.location.href = './compose.html';
        }, 600);
      }
    } catch (error) {
      console.error(error);
      updateStatus('Network error — confirm the API is running on the configured host.', 'error');
      updateResponse(error.message);
    } finally {
      toggleFormDisabled(form, false);
    }
  };

  loginForm.addEventListener('submit', (event) => handleAuthSubmit(event, 'login'));
  signupForm.addEventListener('submit', (event) => handleAuthSubmit(event, 'signup'));

  copyButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(responseOutput.textContent);
      copyButton.textContent = 'Copied!';
      setTimeout(() => {
        copyButton.textContent = 'Copy';
      }, 1500);
    } catch (err) {
      copyButton.textContent = 'Unable to copy';
      setTimeout(() => {
        copyButton.textContent = 'Copy';
      }, 1500);
    }
  });

  setMode('login');
})();
