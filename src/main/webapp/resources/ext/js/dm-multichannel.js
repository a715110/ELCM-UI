/**
 * Enhanced Direct Message Popup with Multi-Channel Support
 *
 * This module extends the DM popup functionality to support multiple messaging channels
 * (Teams, Email, Slack, etc.) based on system configuration.
 *
 */

(function() {
  'use strict';

  // ========================================
  // ADD POPUP STYLES
  // ========================================
  (function ensureStyles() {
    if (document.getElementById('dm-popup-styles')) return;

    const css = document.createElement('style');
    css.id = 'dm-popup-styles';
    css.textContent = `
    #dm-popup {
      position: absolute;
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      box-shadow: 0 10px 25px rgba(0,0,0,.15);
      padding: 20px;
      width: 380px;
      z-index: 999999;
      font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
      animation: dmFadeIn .18s ease-out;
    }
    #dm-popup .row { display:flex; align-items:center; gap:12px; }
    #dm-popup .avatar {
      width:48px; height:48px; border-radius:50%;
      background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);
      color:#fff; display:flex; align-items:center; justify-content:center;
      font-weight:700; font-size:18px;
    }
    #dm-popup .title { font-weight:600; color:#1e293b; font-size:16px; }
    #dm-popup .sub   { color:#64748b; font-size:14px; }
    #dm-popup .actions { display:flex; gap:8px; justify-content:flex-end; margin-top:12px; }
    #dm-popup .btn { 
      border-radius:8px; padding:8px 16px; font-weight:500; 
      cursor:pointer; font-size:14px; border:none;
      display:inline-flex; align-items:center; justify-content:center;
    }
    #dm-popup .btn.sec { background:#f8fafc; border:1px solid #e2e8f0; color:#374151; }
    #dm-popup .btn.sec:hover { background:#f1f5f9; }
    #dm-popup .btn.pri { background:#3b82f6; color:#fff; }
    #dm-popup .btn.pri:hover { background:#2563eb; }
    #dm-popup .btn.ok  { background:#10b981; color:#fff; }
    #dm-popup .btn.ok:hover { background:#059669; }
    #dm-popup textarea {
      width:100%; min-height:80px; resize:vertical; padding:10px;
      border:1px solid #e5e7eb; border-radius:8px; outline:none; margin-top:10px;
      font:inherit; box-sizing:border-box;
    }
    #dm-popup textarea:focus {
      border-color:#3b82f6;
      box-shadow:0 0 0 3px rgba(59,130,246,0.1);
    }
    #dm-popup .composer-bar { 
      display:flex; align-items:center; justify-content:space-between; 
      margin-top:8px; gap:10px; 
    }
    #dm-popup .status { font-size:12px; color:#6b7280; }
    #dm-popup .channel-indicator {
      display:flex; align-items:center; gap:8px;
      margin-bottom:8px; padding:8px 12px;
      background:#f8fafc; border-radius:6px;
    }
    @keyframes dmFadeIn { 
      from { opacity:0; transform:translateY(-6px) scale(.98); } 
      to { opacity:1; transform:none; } 
    }
  `;
    document.head.appendChild(css);
  })();

  // Channel configuration (icons and colors)
  const CHANNEL_CONFIG = {
    TEAMS: {
      name: 'Microsoft Teams',
      icon: 'pi-microsoft',
      color: '#6264A7',
      loginProvider: 'entra'
    },
    SKYPE: {
      name: 'Skype',
      icon: 'pi-skype',
      color: '#00AFF0',
      loginProvider: 'entra'
    },
    SLACK: {
      name: 'Slack',
      icon: 'pi-slack',
      color: '#4A154B',
      loginProvider: 'slack'
    },
    EMAIL: {
      name: 'Email',
      icon: 'pi-envelope',
      color: '#EA4335',
      loginProvider: null
    },
    ZOOM: {
      name: 'Zoom',
      icon: 'pi-video',
      color: '#2D8CFF',
      loginProvider: 'zoom'
    }
  };

  // Current default channel (fetched from backend or cached)
  let cachedDefaultChannel = null;

  /**
   * Fetch the default messaging channel from the backend
   */
  async function fetchDefaultChannel() {
    if (cachedDefaultChannel) {
      return cachedDefaultChannel;
    }

    try {
      const contextPath = window.contextPath || '';
      const response = await fetch(`${contextPath}/api/messaging/default-channel`, {
        method: 'GET',
        credentials: 'same-origin',
        headers: {
          'Accept': 'application/json'
        }
      });

      if (response.ok) {
        const data = await response.json();

        // Map SystemMessagingConfigDTO fields
        cachedDefaultChannel = {
          channelType: data.channelType || 'EMAIL',
          displayName: data.displayName || data.channelType || 'Email',
          configured: data.configured !== false && data.channelType != null,
          priority: data.priority
        };
        return cachedDefaultChannel;
      }
    } catch (e) {
      console.warn('[DM] Could not fetch default channel:', e);
    }

    // Fallback: assume Teams is default
    return { channelType: 'TEAMS', displayName: 'Microsoft Teams' };
  }

  /**
   * Get channel display info
   */
  function getChannelInfo(channelType) {
    const type = (channelType || 'EMAIL').toUpperCase();
    return CHANNEL_CONFIG[type] || CHANNEL_CONFIG.EMAIL;
  }

  /**
   * Enhanced DM UI callbacks
   */
  window.dmUi = window.dmUi || {
    lastStatusEl: null,
    lastPopupEl: null,
    lastChannel: null,

    onStart() {
      const s = this.lastStatusEl;
      if (s) {
        s.style.color = '#64748b';
        s.textContent = 'Sending…';
      }
    },

    onComplete(xhr, status, args) {
      const s = this.lastStatusEl;
      const channel = args && args.channel ? args.channel : this.lastChannel;
      const channelInfo = getChannelInfo(channel);

      // Handle login required
      if (args && args.needsLogin) {
        if (s) {
          s.style.color = '#ef4444';
          s.textContent = `${channelInfo.name} sign-in required…`;
        }

        // Open OAuth login in new window
        const provider = args.loginProvider || channelInfo.loginProvider;
        if (provider) {
          const contextPath = window.contextPath || '';
          window.open(`${contextPath}/oauth2/authorization/${provider}`, '_blank', 'noopener');
        }
        return;
      }

      // Handle success
      if (args && args.success) {
        if (s) {
          s.style.color = '#16a34a';
          const channelName = channelInfo.name;
          s.innerHTML = `<i class="pi ${channelInfo.icon}" style="margin-right:4px;"></i>Sent via ${channelName} ✔`;
        }

        // Close popup after delay
        setTimeout(() => {
          if (typeof window.closeDmPopup === 'function') {
            window.closeDmPopup();
          } else if (typeof closeDmComposer === 'function') {
            closeDmComposer();
          } else if (typeof closeMentionPopup === 'function') {
            closeMentionPopup();
          }
        }, 1200);
        return;
      }

      // Handle failure
      const code = args && args.httpStatus ? ` (HTTP ${args.httpStatus})` : '';
      const detail = args && args.detail ? `: ${args.detail}` : '';
      if (s) {
        s.style.color = '#ef4444';
        s.textContent = `Send failed${code}${detail}`;
      }
    },

    onError() {
      const s = this.lastStatusEl;
      if (s) {
        s.style.color = '#ef4444';
        s.textContent = 'Network/AJAX error';
      }
    }
  };

  /**
   * Enhanced submit function that includes channel type
   */
  window.submitDmWithChannel = function(upn, text, channelType, statusEl, popup) {
    if (typeof sendDmRC !== 'function') {
      console.error('[DM] sendDmRC remoteCommand not found on page.');
      if (statusEl) {
        statusEl.style.color = '#ef4444';
        statusEl.textContent = 'Send not available.';
      }
      return;
    }

    // Save UI refs for callbacks
    window.dmUi.lastStatusEl = statusEl;
    window.dmUi.lastPopupEl = popup;
    window.dmUi.lastChannel = channelType;

    // Build params
    const params = [
      { name: 'recipientId', value: upn },
      { name: 'message', value: text }
    ];

    // Include channel type if specified
    if (channelType) {
      params.push({ name: 'channelType', value: channelType });
    }

    // Call PrimeFaces remoteCommand
    sendDmRC(params);
  };

  /**
   * Original submitDm function (backward compatible)
   */
  window.submitDm = function(upn, text, statusEl, popup) {
    // Use default channel
    window.submitDmWithChannel(upn, text, null, statusEl, popup);
  };

  /**
   * Enhanced DM popup with channel indicator
   */
  window.openDmPopupWithChannel = async function({ anchor, loginId, displayName, openComposer = false }) {
    // Close existing popup
    if (typeof window.closeDmPopup === 'function') {
      window.closeDmPopup();
    }

    // Fetch default channel info
    const channelData = await fetchDefaultChannel();
    const channelInfo = getChannelInfo(channelData.channelType);

    const box = document.createElement('div');
    box.id = 'dm-popup';

    const initials = initialsOf(displayName || loginId);
    const channelDisplay = channelData.displayName || channelInfo.name;

    box.innerHTML = `
      <div class="row" style="margin-bottom:10px;">
        <div class="avatar">${initials}</div>
        <div style="flex:1;min-width:0;">
          <div class="title">${escapeHtml(displayName || loginId || '')}</div>
          <div class="sub">Click to view profile or send message</div>
        </div>
      </div>

      <div class="actions" id="dm-top-actions">
        <button class="btn sec" id="dm-close-2">Close</button>
        <button class="btn pri" id="dm-view">View Profile</button>
        <button class="btn ok" id="dm-open">
          <i class="pi ${channelInfo.icon}" style="margin-right:6px;"></i>Message
        </button>
      </div>

      <div id="dm-composer" style="display:${openComposer ? 'block' : 'none'};">
        <div class="channel-indicator" style="display:flex;align-items:center;gap:8px;margin-bottom:8px;padding:8px 12px;background:#f8fafc;border-radius:6px;">
          <i class="pi ${channelInfo.icon}" style="color:${channelInfo.color};font-size:16px;"></i>
          <span style="font-size:13px;color:#64748b;">Sending via <strong style="color:#1e293b;">${escapeHtml(channelDisplay)}</strong></span>
        </div>
        <label class="sub">Message to <strong>${escapeHtml(loginId || '')}</strong></label>
        <textarea id="dm-text" placeholder="Type your message… (Ctrl/⌘+Enter to send)" maxlength="1000"></textarea>
        <div class="composer-bar">
          <span class="status" id="dm-count">0 / 1000</span>
          <div class="row" style="gap:8px;">
            <button class="btn sec" id="dm-cancel">Cancel</button>
            <button class="btn ok" id="dm-send" style="background:${channelInfo.color};">
              <i class="pi ${channelInfo.icon}" style="margin-right:4px;"></i>Send
            </button>
          </div>
        </div>
        <div class="status" id="dm-status"></div>
      </div>
    `;

    document.body.appendChild(box);

    // Get elements
    const composer = box.querySelector('#dm-composer');
    const textEl = box.querySelector('#dm-text');
    const countEl = box.querySelector('#dm-count');
    const statusEl = box.querySelector('#dm-status');
    const cancelBtn = box.querySelector('#dm-cancel');
    const sendBtn = box.querySelector('#dm-send');

    // Functions
    const open = () => {
      composer.style.display = 'block';
      setTimeout(() => textEl.focus(), 20);
    };

    const close = () => {
      if (typeof box._cleanup === 'function') box._cleanup();
      box.style.animation = 'dmFadeIn .15s ease-out reverse';
      setTimeout(() => box.remove(), 140);
    };

    // Wire up events
    box.querySelector('#dm-close-2').onclick = close;
    box.querySelector('#dm-view').onclick = () => {
      if (typeof viewUserProfile === 'function') {
        viewUserProfile(loginId);
      }
    };
    box.querySelector('#dm-open').onclick = open;

    // Prevent focus shift on buttons
    [cancelBtn, sendBtn].forEach(btn => {
      if (!btn) return;
      btn.addEventListener('mousedown', e => e.preventDefault(), { passive: false });
      btn.addEventListener('pointerdown', e => e.preventDefault(), { passive: false });
    });

    cancelBtn.addEventListener('click', () => {
      textEl.blur();
      textEl.value = '';
      composer.style.display = 'none';
      countEl.textContent = '0 / 1000';
      statusEl.textContent = '';
    });

    sendBtn.addEventListener('click', () => {
      const msg = (textEl.value || '').trim();
      if (!msg) {
        statusEl.style.color = '#ef4444';
        statusEl.textContent = 'Type a message first';
        textEl.focus();
        return;
      }
      // Use the channel-aware submit
      window.submitDmWithChannel(loginId, msg, channelData.channelType, statusEl, box);
    });

    // Typing helpers
    textEl.addEventListener('input', () => {
      countEl.textContent = `${textEl.value.length} / 1000`;
    });

    textEl.addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        sendBtn.click();
      } else if (e.key === 'Escape') {
        close();
      }
    });

    // Outside click to close
    const outsideClickHandler = ev => {
      if (!box.contains(ev.target) && ev.target !== anchor) {
        close();
      }
    };
    setTimeout(() => document.addEventListener('mousedown', outsideClickHandler), 0);

    // Cleanup function
    box._cleanup = () => {
      document.removeEventListener('mousedown', outsideClickHandler);
    };

    // Store global close function
    window.closeDmPopup = close;

    // Position near anchor
    positionNear(box, anchor);

    if (openComposer) {
      open();
    }
  };

  // Helper functions
  function initialsOf(name) {
    return (name || '')
      .replace(/^@/, '')
      .trim()
      .split(/\s+/)
      .map(s => s[0])
      .join('')
      .slice(0, 2)
      .toUpperCase() || '?';
  }

  function escapeHtml(s) {
    return (s || '').replace(/[&<>"']/g, m => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[m]));
  }

  function positionNear(pop, anchor) {
    const a = anchor || document.body;
    const ab = a.getBoundingClientRect();

    pop.style.visibility = 'hidden';
    pop.style.display = 'block';
    const pb = pop.getBoundingClientRect();

    const gap = 8;
    let top = ab.bottom + window.scrollY + gap;
    let left = ab.left + window.scrollX;

    // Clamp to viewport
    if (left + pb.width > window.scrollX + window.innerWidth - 12) {
      left = Math.max(12 + window.scrollX, (ab.right + window.scrollX) - pb.width);
    }
    if (top + pb.height > window.scrollY + window.innerHeight - 12) {
      top = ab.top + window.scrollY - pb.height - gap;
      if (top < 12 + window.scrollY) top = ab.top + window.scrollY;
    }
    left = Math.max(12 + window.scrollX, Math.min(left, window.scrollX + window.innerWidth - pb.width - 12));

    pop.style.top = `${top}px`;
    pop.style.left = `${left}px`;
    pop.style.visibility = 'visible';
  }

  // Make the enhanced popup the default
  window.openDmPopupUnified = window.openDmPopupWithChannel;

  // Also override sendMessageToUser to use the new popup
  const originalSendMessageToUser = window.sendMessageToUser;
  window.sendMessageToUser = function(recipientIdOrName, buttonEl) {
    const display = (recipientIdOrName || '').replace(/^@/, '');
    const loginId = (typeof resolveLoginId === 'function' ? resolveLoginId(recipientIdOrName, buttonEl) : null) || recipientIdOrName;
    window.openDmPopupWithChannel({
      anchor: buttonEl,
      loginId,
      displayName: display,
      openComposer: true
    });
  };

  // ========================================
  // USER NAME CLICK HANDLER
  // ========================================
  /**
   * Setup click handlers for user name links in data tables
   */
  function setupUserNameClickHandlers() {
    // Use event delegation on document body for dynamically loaded content
    document.addEventListener('click', function(event) {
      // Check if clicked element or parent is a user-name-link
      //const link = event.target.closest('.user-name-link, .clickable-user-name');
      const link = event.target.closest('.clickable-user-name');

      if (!link) return;

      // Prevent default link behavior
      event.preventDefault();
      event.stopPropagation();

      // Get login ID from data attribute
      const loginId = link.getAttribute('data-login-id') ||
          link.closest('[data-login-id]')?.getAttribute('data-login-id');

      // Get display name from the text content
      const displayName = link.textContent?.trim() ||
          link.querySelector('.clickable-user-name')?.textContent?.trim() ||
          loginId;

      console.log('[DM] User name clicked:', { loginId, displayName });

      if (!loginId && !displayName) {
        console.warn('[DM] No login ID or display name found for clicked element');
        return;
      }

      try {
        console.log('[DM] About to open popup for:', { loginId, displayName });

        window.openDmPopupWithChannel({
          anchor: link,
          loginId: loginId || displayName,
          displayName: displayName,
          openComposer: false
        }).catch(err => {
          console.error('[DM] Popup error:', err);
        });

      } catch (e) {
        console.error('[DM] Error opening popup:', e);
      }

    }, true);  // Use capture phase to intercept before PrimeFaces

    console.log('[DM] User name click handlers initialized');
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', setupUserNameClickHandlers);
  } else {
    setupUserNameClickHandlers();
  }

  // Also reinitialize after AJAX updates (for lazy-loaded tables)
  if (typeof PrimeFaces !== 'undefined' && PrimeFaces.ajax) {
    const originalAjaxSuccess = PrimeFaces.ajax.Response.handle;
    PrimeFaces.ajax.Response.handle = function(xhr) {
      const result = originalAjaxSuccess.apply(this, arguments);
      // No need to re-setup since we use event delegation
      return result;
    };
  }

  console.log('[DM] Multi-channel messaging module loaded');

})();