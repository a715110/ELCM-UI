/**
 * Enhanced Session Timeout Manager with Multi-Tab Support
 *
 * Features:
 * - Cross-tab communication using BroadcastChannel API
 * - localStorage fallback for older browsers
 * - Server-side session polling for validation
 * - Coordinated logout across all tabs
 * - Activity synchronization between tabs
 *
 * @version 2.0.0
 */
class SessionTimeoutManager {
  constructor(options = {}) {
    // Configuration
    this.config = {
      warningTime: options.warningTime || 25 * 60 * 1000,        // 25 minutes
      graceTime: options.graceTime || 5 * 60 * 1000,              // 5 minutes
      extensionTime: options.extensionTime || 30 * 60 * 1000,     // 30 minutes
      throttleDelay: options.throttleDelay || 30 * 1000,          // 30 seconds
      pollingInterval: options.pollingInterval || 60 * 1000,      // 60 seconds
      heartbeatInterval: options.heartbeatInterval || 3 * 1000,   // 2 seconds
      loginUrl: options.loginUrl || '/oauth2/authorization/sso',
      logoutUrl: options.logoutUrl || '/api/logout',
      extendSessionUrl: options.extendSessionUrl || '/api/extend-session',
      sessionStatusUrl: options.sessionStatusUrl || '/api/session-status',
      contextPath: options.contextPath || ''
    };

    // State management
    this.warningTimer = null;
    this.logoutTimer = null;
    this.pollingTimer = null;
    this.heartbeatTimer = null;
    this.lastActivityTime = Date.now();
    this.lastServerCall = 0;
    this.isWarningShown = false;
    this.isLoggingOut = false;
    this.activityThrottleTimer = null;
    this.countdownInterval = null;

    // Multi-tab support
    this.tabId = this.generateTabId();
    this.broadcastChannel = null;
    this.storageKey = 'session-heartbeat';
    this.storageListenerAttached = false;

    // Bind methods
    this.handleActivity = this.handleActivity.bind(this);
    this.handleStorageEvent = this.handleStorageEvent.bind(this);
    this.handleCrossTabMessage = this.handleCrossTabMessage.bind(this);
    this.showWarning = this.showWarning.bind(this);
    this.hideWarning = this.hideWarning.bind(this);
    this.continueSession = this.continueSession.bind(this);
    this.logout = this.logout.bind(this);
    this.pollServerSession = this.pollServerSession.bind(this);
    this.updateHeartbeat = this.updateHeartbeat.bind(this);

    this.init();
  }

  /**
   * Initialize the session manager
   */
  init() {
    console.log(`[Session Manager] Initializing tab: ${this.tabId}`);

    this.createWarningDialog();
    this.attachActivityListeners();
    this.setupCrossTabCommunication();
    this.startHeartbeat();
    this.startServerPolling();
    this.resetTimers();

    console.log('[Session Manager] Initialized successfully');
    this.logConfiguration();
  }

  /**
   * Generate unique tab identifier
   */
  generateTabId() {
    return `tab-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Log current configuration
   */
  logConfiguration() {
    console.log('[Session Manager] Configuration:', {
      warningTime: `${this.config.warningTime / 1000}s`,
      graceTime: `${this.config.graceTime / 1000}s`,
      throttleDelay: `${this.config.throttleDelay / 1000}s`,
      pollingInterval: `${this.config.pollingInterval / 1000}s`,
      tabId: this.tabId
    });
  }

  // ============ CROSS-TAB COMMUNICATION ============

  /**
   * Setup cross-tab communication using BroadcastChannel and localStorage
   */
  setupCrossTabCommunication() {
    // Try BroadcastChannel first (modern browsers)
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        this.broadcastChannel = new BroadcastChannel('session-sync');
        this.broadcastChannel.onmessage = (event) => {
          this.handleCrossTabMessage(event.data);
        };
        console.log('[Session Manager] BroadcastChannel initialized');
      } catch (error) {
        console.warn(
            '[Session Manager] BroadcastChannel failed, using localStorage fallback:',
            error);
        this.setupLocalStorageFallback();
      }
    } else {
      console.log(
          '[Session Manager] BroadcastChannel not supported, using localStorage fallback');
      this.setupLocalStorageFallback();
    }

    // Always setup localStorage as additional backup
    this.setupLocalStorageFallback();
  }

  /**
   * Setup localStorage-based cross-tab communication
   */
  setupLocalStorageFallback() {
    if (!this.storageListenerAttached) {
      window.addEventListener('storage', this.handleStorageEvent);
      this.storageListenerAttached = true;
      console.log('[Session Manager] localStorage sync enabled');
    }
  }

  /**
   * Handle messages from other tabs via BroadcastChannel
   */
  handleCrossTabMessage(data) {
    // Ignore messages from this tab
    if (data.tabId === this.tabId) {
      return;
    }

    console.log(`[Session Manager] Received message from ${data.tabId}:`,
        data.type);

    switch (data.type) {
      case 'activity':
        this.syncActivityFromOtherTab(data);
        break;

      case 'session-extended':
        this.syncSessionExtensionFromOtherTab(data);
        break;

      case 'warning-shown':
        this.syncWarningFromOtherTab(data);
        break;

      case 'warning-hidden':
        this.syncWarningHiddenFromOtherTab(data);
        break;

      case 'logout':
        this.syncLogoutFromOtherTab(data);
        break;

      case 'heartbeat':
        this.syncHeartbeatFromOtherTab(data);
        break;
    }
  }

  /**
   * Handle storage events from other tabs
   */
  handleStorageEvent(event) {
    if (event.key !== this.storageKey || !event.newValue) {
      return;
    }

    try {
      const data = JSON.parse(event.newValue);
      // Process as if it came through BroadcastChannel
      this.handleCrossTabMessage(data);
    } catch (error) {
      console.error('[Session Manager] Error parsing storage event:', error);
    }
  }

  /**
   * Broadcast message to all other tabs
   */
  broadcastMessage(data) {
    const message = {
      ...data,
      tabId: this.tabId,
      timestamp: Date.now()
    };

    // Send via BroadcastChannel
    if (this.broadcastChannel) {
      try {
        this.broadcastChannel.postMessage(message);
      } catch (error) {
        console.error('[Session Manager] BroadcastChannel error:', error);
      }
    }

    // Also send via localStorage for compatibility
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(message));
    } catch (error) {
      console.error('[Session Manager] localStorage error:', error);
    }
  }

  // ============ CROSS-TAB SYNC HANDLERS ============

  /**
   * Sync activity from another tab
   */
  syncActivityFromOtherTab(data) {
    console.log(`[Session Manager] Activity detected in ${data.tabId}`);
    this.lastActivityTime = data.timestamp;

    if (this.isWarningShown) {
      this.hideWarning();
    }

    this.resetTimers();
  }

  /**
   * Sync session extension from another tab
   */
  syncSessionExtensionFromOtherTab(data) {
    console.log(`[Session Manager] Session extended by ${data.tabId}`);
    this.lastActivityTime = data.timestamp;
    this.lastServerCall = data.timestamp;

    if (this.isWarningShown) {
      this.hideWarning();
    }

    this.resetTimers();
  }

  /**
   * Sync warning display from another tab
   */
  syncWarningFromOtherTab(data) {
    console.log(`[Session Manager] Warning shown in ${data.tabId}`);
    if (!this.isWarningShown) {
      this.showWarning();
    }
  }

  /**
   * Sync warning hidden from another tab
   */
  syncWarningHiddenFromOtherTab(data) {
    console.log(`[Session Manager] Warning hidden in ${data.tabId}`);
    if (this.isWarningShown) {
      this.hideWarning();
    }
  }

  /**
   * Sync logout from another tab
   */
  syncLogoutFromOtherTab(data) {
    console.log(`[Session Manager] Logout initiated by ${data.tabId}`);
    // Prevent multiple logout attempts
    if (this._logoutStarted) return;
    this._logoutStarted = true;

    this.isLoggingOut = true;
    this.clearTimers();
    this.hideWarning();
    this.pauseActivityListeners();

    // Set logout cookie
    document.cookie = 'noExtend=1; path=/; max-age=90; secure; SameSite=None';

    // Show notification
    this.showNotification('Logged out from another tab. Redirecting...', 'info');

    // REDIRECT THIS TAB TOO! ✅
    setTimeout(() => {
      window.location.href = 'https://localhost:8081/logout?post_logout_redirect_uri='
          +
          encodeURIComponent('https://localhost:443/');
    }, 500);
  }

  /**
   * Sync heartbeat from another tab
   */
  syncHeartbeatFromOtherTab(data) {
    // Update last activity time if other tab is more recent
    if (data.lastActivityTime > this.lastActivityTime) {
      this.lastActivityTime = data.lastActivityTime;

      // Reset timers if we're getting close to warning time
      const timeSinceActivity = Date.now() - this.lastActivityTime;
      if (timeSinceActivity < this.config.warningTime) {
        this.resetTimers();
      }
    }
  }

  // ============ HEARTBEAT MANAGEMENT ============

  /**
   * Start periodic heartbeat to sync with other tabs
   */
  startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      if (!this.isLoggingOut) {
        this.updateHeartbeat();
      }
    }, this.config.heartbeatInterval);

    console.log(
        `[Session Manager] Heartbeat started (${this.config.heartbeatInterval}ms interval)`);
  }

  /**
   * Update heartbeat - broadcast current state to other tabs
   */
  updateHeartbeat() {
    this.broadcastMessage({
      type: 'heartbeat',
      lastActivityTime: this.lastActivityTime,
      isWarningShown: this.isWarningShown,
      isLoggingOut: this.isLoggingOut
    });
  }

  // ============ SERVER POLLING ============

  /**
   * Start periodic server session polling
   */
  startServerPolling() {
    this.pollingTimer = setInterval(async () => {
      if (!this.isLoggingOut) {
        await this.pollServerSession();
      }
    }, this.config.pollingInterval);

    console.log(
        `[Session Manager] Server polling started (${this.config.pollingInterval}ms interval)`);
  }

  /**
   * Poll server to verify session is still valid
   */
  async pollServerSession() {
    try {
      const response = await fetch(this.config.sessionStatusUrl, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin'
      });

      if (response.ok) {
        const data = await response.json();

        if (!data.active) {
          console.warn('[Session Manager] Server reports session inactive');
          this.logout('server-expired');
          return {active: false, reason: 'server_inactive'};
        } else {
          console.log('[Session Manager] Server session validated:', {
            sessionId: data.sessionId,
            maxInactiveInterval: data.maxInactiveInterval
          });
          return {active: true, ...data};
        }
      } else if (response.status === 401) {
        console.warn('[Session Manager] Server session expired (401)');
        this.logout('server-expired');
        return {active: false, status: 401, reason: 'unauthorized'};
      } else {
        console.warn('[Session Manager] Session status check failed:',
            response.status);
        return {
          active: false,
          status: response.status,
          statusText: response.statusText,
          reason: 'status_check_failed'
        };
      }
    } catch (error) {
      console.error('[Session Manager] Error polling server session:', error);
      // Don't logout on network errors - could be temporary
      return {
        active: false,
        error: error.message,
        reason: 'network_error'
      };
    }
  }

  // ============ WARNING DIALOG ============

  /**
   * Create warning dialog HTML
   */
  createWarningDialog() {
    const dialogHtml = `
      <div id="sessionWarningDialog" style="display: none;">
        <div class="session-warning-overlay">
          <div class="session-warning-content">
            <div class="warning-header">
              <i class="pi pi-exclamation-triangle"></i>
              <h3>Session About to Expire</h3>
            </div>
            <div class="warning-body">
              <p>Your session will expire in <span id="countdownTimer">5:00</span></p>
              <p>Click "Continue" to extend your session or "Logout" to end it now.</p>
              <p class="warning-note">This message is shown in all open tabs.</p>
            </div>
            <div class="warning-actions">
              <button id="continueSessionBtn" class="btn-continue">
                <i class="pi pi-refresh"></i>
                Continue Session
              </button>
              <button id="logoutBtn" data-action="logout" class="btn-logout">
                <i class="pi pi-sign-out"></i>
                Logout
              </button>
            </div>
          </div>
        </div>
      </div>
    `;

    const styles = `
      <style>
        .session-warning-overlay {
          position: fixed; top: 0; left: 0; width: 100%; height: 100%;
          background: rgba(0, 0, 0, 0.7); z-index: 10000;
          display: flex; align-items: center; justify-content: center;
          backdrop-filter: blur(5px);
        }
        .session-warning-content {
          background: white; border-radius: 12px; padding: 2rem;
          max-width: 450px; width: 90%;
          box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
          animation: slideInUp 0.3s ease-out;
        }
        @keyframes slideInUp {
          from { opacity: 0; transform: translateY(30px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .warning-header { text-align: center; margin-bottom: 1.5rem; }
        .warning-header i { color: #f59e0b; font-size: 3rem; margin-bottom: 0.5rem; }
        .warning-header h3 { color: #374151; margin: 0; font-size: 1.5rem; font-weight: 600; }
        .warning-body { text-align: center; margin-bottom: 2rem; color: #6b7280; line-height: 1.6; }
        .warning-note { font-size: 0.85rem; color: #9ca3af; margin-top: 0.5rem; }
        #countdownTimer { font-weight: bold; color: #dc2626; font-size: 1.2rem; }
        .warning-actions { display: flex; gap: 1rem; justify-content: center; }
        .btn-continue, .btn-logout {
          padding: 0.75rem 1.5rem; border: none; border-radius: 6px;
          font-weight: 500; cursor: pointer; display: flex; align-items: center;
          gap: 0.5rem; transition: all 0.2s ease; font-size: 0.95rem;
        }
        .btn-continue { background: #3b82f6; color: white; }
        .btn-continue:hover { background: #2563eb; transform: translateY(-1px); }
        .btn-logout { background: #dc2626; color: white; }
        .btn-logout:hover { background: #b91c1c; transform: translateY(-1px); }
      </style>
    `;

    document.head.insertAdjacentHTML('beforeend', styles);
    document.body.insertAdjacentHTML('beforeend', dialogHtml);

    document.getElementById('continueSessionBtn').addEventListener('click',
        this.continueSession);

    const logoutBtn = document.getElementById('logoutBtn');
    logoutBtn.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      this.logout('manual');
    }, {capture: true});

    logoutBtn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
    }, {capture: true});
  }

  // ============ ACTIVITY TRACKING ============

  /**
   * Attach activity listeners
   */
  attachActivityListeners() {
    const events = ['mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    events.forEach(event => {
      document.addEventListener(event, this.handleActivity, false);
    });

    // PrimeFaces AJAX integration
    if (window.PrimeFaces && PrimeFaces.ajax) {
      const originalSend = PrimeFaces.ajax.Request.send;
      window._pfSendOriginal = originalSend;
      PrimeFaces.ajax.Request.send = (...args) => {
        if (!this.isLoggingOut) {
          this.handleActivity();
        }
        return originalSend.apply(this, args);
      };
    }

    console.log('[Session Manager] Activity listeners attached');
  }

  /**
   * Handle user activity
   */
  handleActivity() {
    if (this.isLoggingOut) {
      return;
    }

    const now = Date.now();
    this.lastActivityTime = now;

    // If warning is shown, hide it and reset timers
    if (this.isWarningShown) {
      this.hideWarning();
      this.resetTimers();

      // Notify other tabs
      this.broadcastMessage({
        type: 'activity',
        timestamp: now
      });
      return;
    }

    // Broadcast activity to other tabs
    this.broadcastMessage({
      type: 'activity',
      timestamp: now
    });

    // Throttle server calls
    if (now - this.lastServerCall > this.config.throttleDelay) {
      if (this.activityThrottleTimer) {
        clearTimeout(this.activityThrottleTimer);
      }

      this.activityThrottleTimer = setTimeout(() => {
        if (this.isLoggingOut) {
          return;
        }
        this.extendSessionOnServer();
        this.resetTimers();
      }, 1000);
    }
  }

  /**
   * Extend session on server
   */
  async extendSessionOnServer() {
    if (this.isLoggingOut) {
      return {blocked: true, reason: 'logout_in_progress'};
    }

    if (this.isWarningShown) {
      return {blocked: true, reason: 'warning_shown'};
    }

    if (document.cookie.includes('noExtend=1')) {
      return {blocked: true, reason: 'logout_cookie_set'};
    }

    const now = Date.now();

    if (now - this.lastServerCall < this.config.throttleDelay) {
      console.log('[Session Manager] Server call throttled');
      return {
        blocked: true,
        reason: 'throttled',
        timeSinceLastCall: now - this.lastServerCall,
        throttleDelay: this.config.throttleDelay,
        nextCallAvailable: this.lastServerCall + this.config.throttleDelay
      };
    }

    this.lastServerCall = now;

    try {
      const response = await fetch(this.config.extendSessionUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          tabId: this.tabId
        })
      });

      if (response.ok) {
        const data = await response.json();
        console.log('[Session Manager] Session extended successfully:', data);

        // Notify other tabs about successful extension
        this.broadcastMessage({
          type: 'session-extended',
          timestamp: now
        });

        return data;
      } else {
        console.error('[Session Manager] Session extension failed. Status:',
            response.status);

        const errorResult = {
          success: false,
          status: response.status,
          statusText: response.statusText
        };

        if (response.status === 401) {
          console.error('[Session Manager] Unauthorized - session expired');
          this.logout('server-expired');
          errorResult.reason = 'unauthorized';
        } else if (response.status === 409) {
          console.log(
              '[Session Manager] Logout in progress - extension blocked');
          errorResult.reason = 'conflict';
        } else {
          const errorText = await response.text();
          console.error('[Session Manager] Session extension error:',
              errorText);
          errorResult.reason = 'server_error';
          errorResult.error = errorText;
        }

        return errorResult;
      }
    } catch (error) {
      console.error('[Session Manager] Error extending session:', error);
      return {
        success: false,
        reason: 'network_error',
        error: error.message
      };
    }
  }

  // ============ TIMER MANAGEMENT ============

  /**
   * Reset warning and logout timers
   */
  resetTimers() {
    this.clearTimers();

    this.warningTimer = setTimeout(() => {
      this.showWarning();
    }, this.config.warningTime);

    console.log(
        `[Session Manager] Timers reset. Warning in: ${this.config.warningTime
        / 1000}s`);
  }

  /**
   * Clear all timers
   */
  clearTimers() {
    if (this.warningTimer) {
      clearTimeout(this.warningTimer);
      this.warningTimer = null;
    }
    if (this.logoutTimer) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    if (this.activityThrottleTimer) {
      clearTimeout(this.activityThrottleTimer);
      this.activityThrottleTimer = null;
    }
  }

  // ============ WARNING DISPLAY ============

  /**
   * Show warning dialog
   */
  showWarning() {
    if (this.isWarningShown) {
      return;
    }

    this.isWarningShown = true;
    document.getElementById('sessionWarningDialog').style.display = 'block';
    this.startCountdown();

    this.logoutTimer = setTimeout(() => {
      this.logout('timeout');
    }, this.config.graceTime);

    // Notify other tabs
    this.broadcastMessage({
      type: 'warning-shown'
    });

    console.log('[Session Manager] Warning displayed');
  }

  /**
   * Hide warning dialog
   */
  hideWarning() {
    if (!this.isWarningShown) {
      return;
    }

    this.isWarningShown = false;
    document.getElementById('sessionWarningDialog').style.display = 'none';

    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
      this.countdownInterval = null;
    }

    // Notify other tabs
    this.broadcastMessage({
      type: 'warning-hidden'
    });

    console.log('[Session Manager] Warning hidden');
  }

  /**
   * Start countdown timer
   */
  startCountdown() {
    const countdownElement = document.getElementById('countdownTimer');
    let remainingTime = this.config.graceTime;

    this.countdownInterval = setInterval(() => {
      remainingTime -= 1000;
      if (remainingTime <= 0) {
        clearInterval(this.countdownInterval);
        this.logout('timeout');
        return;
      }
      const minutes = Math.floor(remainingTime / 60000);
      const seconds = Math.floor((remainingTime % 60000) / 1000);
      countdownElement.textContent = `${minutes}:${seconds.toString().padStart(
          2, '0')}`;
    }, 1000);
  }

  /**
   * Continue session (user clicked Continue button)
   */
  continueSession() {
    console.log('[Session Manager] User requested session continuation');

    this.hideWarning();
    this.extendSessionOnServer();
    this.resetTimers();
    this.showNotification('Session extended successfully', 'success');

    // Notify other tabs
    this.broadcastMessage({
      type: 'session-extended',
      timestamp: Date.now()
    });
  }

  // ============ LOGOUT ============

  /**
   * Logout user
   */
  logout(reason = 'timeout') {
    if (this._logoutStarted) {
      return;
    }

    this._logoutStarted = true;
    this.isLoggingOut = true;
    this.lastServerCall = Number.MAX_SAFE_INTEGER;

    console.log(`[Session Manager] Logout initiated. Reason: ${reason}`);

    // Notify other tabs immediately
    this.broadcastMessage({
      type: 'logout',
      reason: reason
    });

    if (reason === 'manual') {
      document.cookie = 'noExtend=1; path=/; max-age=60; secure; SameSite=None';
    }

    // Prevent any queued extend from firing
    if (this.activityThrottleTimer) {
      clearTimeout(this.activityThrottleTimer);
      this.activityThrottleTimer = null;
    }

    this.clearAllTimers();
    this.hideWarning();
    this.pauseActivityListeners();

    document.cookie = 'noExtend=1; path=/; max-age=90; secure; SameSite=None';

    if (reason === 'timeout' || reason === 'server-expired') {
      this.setTimeoutCookie();
      this.showNotification('Session expired. Redirecting to login...', 'info');
    } else if (reason === 'manual') {
      this.showNotification('Logging out...', 'info');
    }

    // Build the SSO logout URL
    const ssoLogoutUrl = 'https://localhost:8081/logout?post_logout_redirect_uri='
        +
        encodeURIComponent('https://localhost:443/');

    const logoutEndpoint = this.config.contextPath + this.config.logoutUrl;

    fetch(logoutEndpoint, {
      method: 'POST',
      credentials: 'same-origin',
      keepalive: true
    }).finally(() => {
      // Go to SSO logout to clear IdP session
      window.location.href = ssoLogoutUrl;
    });
  }

  /**
   * Clear all timers including polling and heartbeat
   */
  clearAllTimers() {
    this.clearTimers();

    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }

    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * Pause activity listeners
   */
  pauseActivityListeners() {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll',
      'touchstart', 'click'];
    events.forEach(event => {
      document.removeEventListener(event, this.handleActivity, false);
    });
  }

  /**
   * Set timeout cookie for OAuth flow
   */
  setTimeoutCookie() {
    document.cookie = 'loginReason=timeout; path=/; max-age=300; secure; SameSite=None';
    console.log('[Session Manager] Timeout cookie set for OAuth flow');
  }

  /**
   * Show notification
   */
  showNotification(message, type = 'info') {
    if (window.PF && PF('msgs')) {
      PF('msgs').show([{
        severity: type,
        summary: 'Session Manager',
        detail: message
      }]);
    } else {
      console.log(`[${type.toUpperCase()}] ${message}`);
    }
  }

  // ============ CLEANUP ============

  /**
   * Destroy session manager and cleanup resources
   */
  destroy() {
    console.log('[Session Manager] Destroying instance');

    this.clearAllTimers();
    this.hideWarning();

    const events = ['mousedown', 'mousemove', 'keypress', 'scroll',
      'touchstart', 'click'];
    events.forEach(event => {
      document.removeEventListener(event, this.handleActivity, false);
    });

    if (this.storageListenerAttached) {
      window.removeEventListener('storage', this.handleStorageEvent);
      this.storageListenerAttached = false;
    }

    if (this.broadcastChannel) {
      this.broadcastChannel.close();
      this.broadcastChannel = null;
    }

    const dialog = document.getElementById('sessionWarningDialog');
    if (dialog) {
      dialog.remove();
    }

    console.log('[Session Manager] Destroyed successfully');
  }

  // ============ DEBUG AND TEST METHODS ============

  /**
   * Get current session manager state for debugging
   */
  getDebugInfo() {
    const now = Date.now();
    return {
      tabId: this.tabId,
      config: this.config,
      state: {
        isWarningShown: this.isWarningShown,
        isLoggingOut: this.isLoggingOut,
        lastActivityTime: new Date(this.lastActivityTime).toISOString(),
        lastServerCall: new Date(this.lastServerCall).toISOString(),
        timeSinceLastActivity: now - this.lastActivityTime,
        timeSinceLastServerCall: now - this.lastServerCall
      },
      timers: {
        warningTimer: this.warningTimer !== null,
        logoutTimer: this.logoutTimer !== null,
        pollingTimer: this.pollingTimer !== null,
        heartbeatTimer: this.heartbeatTimer !== null,
        activityThrottleTimer: this.activityThrottleTimer !== null,
        countdownInterval: this.countdownInterval !== null
      },
      crossTab: {
        broadcastChannelEnabled: this.broadcastChannel !== null,
        storageListenerAttached: this.storageListenerAttached
      },
      timeUntilWarning: this.warningTimer ?
          Math.max(0, this.config.warningTime - (now - this.lastActivityTime))
          : 0,
      sessionStatus: 'Session manager active'
    };
  }

  /**
   * Force show warning dialog for testing
   */
  testShowWarning() {
    console.log('[DEBUG] Forcing warning dialog to show');
    this.showWarning();
  }

  /**
   * Force hide warning dialog for testing
   */
  testHideWarning() {
    console.log('[DEBUG] Forcing warning dialog to hide');
    this.hideWarning();
  }

  /**
   * Test server communication
   */
  async testServerConnection() {
    console.log('[DEBUG] Testing server connection...');
    try {
      const result = await this.forceExtendSession();
      console.log('[DEBUG] Server connection test result:', result);
      return result;
    } catch (error) {
      console.error('[DEBUG] Server connection test failed:', error);
      throw error;
    }
  }

  /**
   * Force session extension bypassing throttle (for testing only)
   */
  async forceExtendSession() {
    console.log('[DEBUG] Forcing session extension (bypassing throttle)');

    // Temporarily clear last server call to bypass throttle
    const originalLastCall = this.lastServerCall;
    this.lastServerCall = 0;

    try {
      const result = await this.extendSessionOnServer();
      return result;
    } finally {
      // Only restore if it wasn't updated by the call
      if (this.lastServerCall === 0) {
        this.lastServerCall = originalLastCall;
      }
    }
  }

  /**
   * Simulate user activity for testing
   */
  simulateActivity() {
    console.log('[DEBUG] Simulating user activity');
    this.handleActivity();
  }

  /**
   * Force session expiration for testing
   */
  forceExpiration() {
    console.log('[DEBUG] Forcing session expiration');
    this.logout('test');
  }

  /**
   * Set custom timers for testing (in milliseconds)
   */
  setTestTimers(warningTime, graceTime) {
    console.log(
        `[DEBUG] Setting test timers - Warning: ${warningTime}ms, Grace: ${graceTime}ms`);
    this.config.warningTime = warningTime;
    this.config.graceTime = graceTime;
    this.resetTimers();
  }

  /**
   * Log current status to console
   */
  logStatus() {
    const info = this.getDebugInfo();
    console.group('[SESSION MANAGER STATUS]');
    console.log('Tab ID:', info.tabId);
    console.log('Configuration:', info.config);
    console.log('State:', info.state);
    console.log('Active Timers:', info.timers);
    console.log('Cross-Tab:', info.crossTab);
    console.log('Time Until Warning:', Math.round(info.timeUntilWarning / 1000),
        'seconds');
    console.groupEnd();
  }

  /**
   * Test session status endpoint
   */
  async testSessionStatus() {
    console.log('[DEBUG] Testing session status endpoint...');
    return await this.pollServerSession();
  }

  /**
   * Create debug panel in UI for testing
   */
  createDebugPanel() {
    if (document.getElementById('sessionDebugPanel')) {
      return;
    }

    const debugPanelHtml = `
      <div id="sessionDebugPanel" style="
        position: fixed; top: 10px; right: 10px; z-index: 9999;
        background: #f8f9fa; border: 1px solid #dee2e6; border-radius: 6px;
        padding: 15px; font-family: monospace; font-size: 12px;
        box-shadow: 0 4px 6px rgba(0,0,0,0.1); max-width: 320px;
      ">
        <div style="font-weight: bold; margin-bottom: 10px; border-bottom: 1px solid #ccc; padding-bottom: 5px;">
          Session Manager Debug
        </div>
        <div id="debugStatus" style="margin-bottom: 10px; font-size: 11px;"></div>
        <div style="display: flex; flex-wrap: wrap; gap: 5px;">
          <button onclick="window.sessionManager.testShowWarning()" style="padding: 4px 8px; font-size: 10px;">Show Warning</button>
          <button onclick="window.sessionManager.testHideWarning()" style="padding: 4px 8px; font-size: 10px;">Hide Warning</button>
          <button onclick="window.sessionManager.simulateActivity()" style="padding: 4px 8px; font-size: 10px;">Activity</button>
          <button onclick="window.sessionManager.testServerConnection()" style="padding: 4px 8px; font-size: 10px;">Test Server</button>
          <button onclick="window.sessionManager.logStatus()" style="padding: 4px 8px; font-size: 10px;">Log Status</button>
          <button onclick="window.sessionManager.setTestTimers(10000, 5000)" style="padding: 4px 8px; font-size: 10px;">Fast Test</button>
        </div>
      </div>
    `;

    document.body.insertAdjacentHTML('beforeend', debugPanelHtml);

    setInterval(() => {
      const statusElement = document.getElementById('debugStatus');
      if (statusElement && window.sessionManager) {
        const info = window.sessionManager.getDebugInfo();
        statusElement.innerHTML = `
          <div>Tab: ${info.tabId.substring(0, 12)}...</div>
          <div>Warning: ${info.state.isWarningShown ? 'SHOWN' : 'Hidden'}</div>
          <div>Logging Out: ${info.state.isLoggingOut ? 'YES' : 'No'}</div>
          <div>Next Warning: ${Math.round(info.timeUntilWarning / 1000)}s</div>
          <div>Last Activity: ${Math.round(
            info.state.timeSinceLastActivity / 1000)}s ago</div>
          <div>Broadcast: ${info.crossTab.broadcastChannelEnabled ? 'Yes'
            : 'No'}</div>
        `;
      }
    }, 2000);
  }
}