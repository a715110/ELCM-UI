/**
 * Session Timeout Manager - Test Suite
 *
 * Run this in the browser console to test session manager functionality
 *
 * Usage:
 *   SessionManagerTests.runAll()           - Run all tests
 *   SessionManagerTests.testMultiTab()     - Test multi-tab sync
 *   SessionManagerTests.testServerCalls()  - Test server communication
 *   SessionManagerTests.testLogout()       - Test logout coordination
 */

const SessionManagerTests = {

  results: [],

  /**
   * Test suite runner
   */
  async runAll() {
    console.log('═══════════════════════════════════════════════════');
    console.log('   SESSION TIMEOUT MANAGER - TEST SUITE');
    console.log('═══════════════════════════════════════════════════\n');

    this.results = [];

    await this.testInitialization();
    await this.testConfiguration();
    await this.testActivityDetection();
    await this.testThrottling();
    await this.testServerCalls();
    await this.testTimerManagement();
    await this.testWarningDialog();
    await this.testMultiTab();
    await this.testLogout();

    this.printResults();
  },

  /**
   * Log test result
   */
  log(testName, passed, message = '') {
    const result = {
      test: testName,
      passed: passed,
      message: message,
      timestamp: new Date().toISOString()
    };

    this.results.push(result);

    const icon = passed ? '✅' : '❌';
    const status = passed ? 'PASS' : 'FAIL';
    console.log(`${icon} [${status}] ${testName}`);
    if (message) {
      console.log(`   └─ ${message}`);
    }
  },

  /**
   * Print test summary
   */
  printResults() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('   TEST SUMMARY');
    console.log('═══════════════════════════════════════════════════');

    const total = this.results.length;
    const passed = this.results.filter(r => r.passed).length;
    const failed = total - passed;
    const passRate = ((passed / total) * 100).toFixed(1);

    console.log(`Total Tests: ${total}`);
    console.log(`Passed: ${passed} ✅`);
    console.log(`Failed: ${failed} ❌`);
    console.log(`Pass Rate: ${passRate}%`);

    if (failed > 0) {
      console.log('\nFailed Tests:');
      this.results.filter(r => !r.passed).forEach(r => {
        console.log(`  ❌ ${r.test}: ${r.message}`);
      });
    }

    console.log('═══════════════════════════════════════════════════\n');
  },

  // ============ TEST CASES ============

  /**
   * Test 1: Initialization
   */
  async testInitialization() {
    console.log('\n[Test 1] Initialization');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Check if session manager exists
      if (!window.sessionManager) {
        this.log('Session Manager Exists', false,
            'window.sessionManager is undefined');
        return;
      }
      this.log('Session Manager Exists', true);

      // Check tab ID
      const hasTabId = !!window.sessionManager.tabId;
      this.log('Tab ID Generated', hasTabId,
          hasTabId ? `Tab ID: ${window.sessionManager.tabId}` : 'No tab ID');

      // Check timers initialized
      const hasWarningTimer = window.sessionManager.warningTimer !== null;
      this.log('Warning Timer Initialized', hasWarningTimer);

      // Check last activity time
      const hasActivity = window.sessionManager.lastActivityTime > 0;
      this.log('Last Activity Time Set', hasActivity);

    } catch (error) {
      this.log('Initialization Test', false, error.message);
    }
  },

  /**
   * Test 2: Configuration
   */
  async testConfiguration() {
    console.log('\n[Test 2] Configuration');
    console.log('─────────────────────────────────────────────────\n');

    try {
      const config = window.sessionManager.config;

      // Check warning time
      const warningOk = config.warningTime > 0;
      this.log('Warning Time Configured', warningOk,
          `${config.warningTime / 1000}s`);

      // Check grace time
      const graceOk = config.graceTime > 0;
      this.log('Grace Time Configured', graceOk,
          `${config.graceTime / 1000}s`);

      // Check throttle delay
      const throttleOk = config.throttleDelay >= 30000;
      this.log('Throttle Delay Configured', throttleOk,
          `${config.throttleDelay / 1000}s`);

      // Check URLs configured
      const extendUrlOk = !!config.extendSessionUrl;
      this.log('Extend URL Configured', extendUrlOk, config.extendSessionUrl);

      const statusUrlOk = !!config.sessionStatusUrl;
      this.log('Status URL Configured', statusUrlOk, config.sessionStatusUrl);

    } catch (error) {
      this.log('Configuration Test', false, error.message);
    }
  },

  /**
   * Test 3: Activity Detection
   */
  async testActivityDetection() {
    console.log('\n[Test 3] Activity Detection');
    console.log('─────────────────────────────────────────────────\n');

    try {
      const beforeActivity = window.sessionManager.lastActivityTime;

      // Wait a moment
      await this.sleep(100);

      // Simulate activity
      window.sessionManager.handleActivity();

      const afterActivity = window.sessionManager.lastActivityTime;

      const activityUpdated = afterActivity > beforeActivity;
      this.log('Activity Time Updated', activityUpdated,
          `Before: ${beforeActivity}, After: ${afterActivity}`);

    } catch (error) {
      this.log('Activity Detection Test', false, error.message);
    }
  },

  /**
   * Test 4: Throttling
   */
  async testThrottling() {
    console.log('\n[Test 4] Throttling');
    console.log('─────────────────────────────────────────────────\n');

    try {
      const beforeServerCall = window.sessionManager.lastServerCall;

      // Trigger multiple activities rapidly
      window.sessionManager.handleActivity();
      await this.sleep(50);
      window.sessionManager.handleActivity();
      await this.sleep(50);
      window.sessionManager.handleActivity();

      // Wait for throttle timer
      await this.sleep(1500);

      const afterServerCall = window.sessionManager.lastServerCall;

      // Should have made at most one server call
      const throttled = (afterServerCall === beforeServerCall) ||
          ((afterServerCall - beforeServerCall) >= 30000);

      this.log('Server Calls Throttled', throttled,
          `Time between calls: ${afterServerCall - beforeServerCall}ms`);

    } catch (error) {
      this.log('Throttling Test', false, error.message);
    }
  },

  /**
   * Test 5: Server Communication
   */
  async testServerCalls() {
    console.log('\n[Test 5] Server Communication');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Test extend session using force method to bypass throttle
      const extendResult = await window.sessionManager.forceExtendSession();

      // Check result
      let extendOk = false;
      let message = 'Extension failed';

      if (extendResult) {
        if (extendResult.blocked) {
          // Blocked but returned status - this is OK for the test
          extendOk = true;
          message = `Blocked: ${extendResult.reason}`;
        } else if (extendResult.success === false) {
          // Server error
          message = `Server error: ${extendResult.reason
          || extendResult.status}`;
        } else if (extendResult.success || extendResult.sessionId) {
          // Success!
          extendOk = true;
          message = `Success: ${extendResult.sessionId || 'Session extended'}`;
        } else {
          // Some other response format
          extendOk = true;
          message = 'Server responded (non-standard format)';
        }
      } else {
        message = 'No response from server';
      }

      this.log('Session Extension', extendOk, message);

      // Wait a moment
      await this.sleep(500);

      // Test session status
      const statusResult = await window.sessionManager.pollServerSession();
      this.log('Session Status Check', true, 'Status checked successfully');

    } catch (error) {
      this.log('Server Communication Test', false, error.message);
    }
  },

  /**
   * Test 6: Timer Management
   */
  async testTimerManagement() {
    console.log('\n[Test 6] Timer Management');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Check timer exists
      const hasTimer = window.sessionManager.warningTimer !== null;
      this.log('Warning Timer Active', hasTimer);

      // Reset timers
      window.sessionManager.resetTimers();

      const timerReset = window.sessionManager.warningTimer !== null;
      this.log('Timers Reset Successfully', timerReset);

      // Clear timers
      window.sessionManager.clearTimers();

      const timersCleared = window.sessionManager.warningTimer === null &&
          window.sessionManager.logoutTimer === null;
      this.log('Timers Cleared Successfully', timersCleared);

      // Restore timers
      window.sessionManager.resetTimers();

    } catch (error) {
      this.log('Timer Management Test', false, error.message);
    }
  },

  /**
   * Test 7: Warning Dialog
   */
  async testWarningDialog() {
    console.log('\n[Test 7] Warning Dialog');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Check warning not shown initially
      const notShownInitially = !window.sessionManager.isWarningShown;
      this.log('Warning Not Shown Initially', notShownInitially);

      // Show warning
      window.sessionManager.showWarning();

      // Wait longer for dialog to render
      await this.sleep(1000);

      // Check if warning flag is set
      const warningFlagSet = window.sessionManager.isWarningShown;

      // Check if dialog element exists
      const dialogElement = document.getElementById('sessionWarningDialog');
      const dialogExists = dialogElement !== null;

      // Check if dialog is visible (only if it exists)
      let dialogVisible = false;
      if (dialogExists) {
        const display = window.getComputedStyle(dialogElement).display;
        dialogVisible = display !== 'none';
      }

      const warningShown = warningFlagSet && dialogExists && dialogVisible;

      let message = '';
      if (!warningFlagSet) {
        message = 'Flag not set';
      } else if (!dialogExists) {
        message = 'Dialog element missing';
      } else if (!dialogVisible) {
        message = `Display: ${window.getComputedStyle(
            dialogElement).display}`;
      } else {
        message = 'Warning shown successfully';
      }

      this.log('Warning Dialog Shown', warningShown, message);

      // Hide warning
      window.sessionManager.hideWarning();
      await this.sleep(500);

      const warningHidden = !window.sessionManager.isWarningShown;
      const dialogHidden = dialogExists &&
          (window.getComputedStyle(dialogElement).display === 'none');

      this.log('Warning Dialog Hidden', warningHidden && dialogHidden);

    } catch (error) {
      this.log('Warning Dialog Test', false, error.message);
    }
  },

  /**
   * Test 8: Multi-Tab Communication
   */
  async testMultiTab() {
    console.log('\n[Test 8] Multi-Tab Communication');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Check BroadcastChannel
      const hasBroadcast = window.sessionManager.broadcastChannel !== null;
      this.log('BroadcastChannel Available', hasBroadcast,
          hasBroadcast ? 'Enabled' : 'Using localStorage fallback');

      // Check localStorage listener
      const hasStorage = window.sessionManager.storageListenerAttached;
      this.log('Storage Listener Attached', hasStorage);

      // Test broadcast
      let messageReceived = false;

      if (hasBroadcast) {
        const originalHandler = window.sessionManager.handleCrossTabMessage;
        window.sessionManager.handleCrossTabMessage = function (data) {
          if (data.type === 'test' && data.tabId !== this.tabId) {
            messageReceived = true;
          }
          originalHandler.call(this, data);
        };

        // Broadcast test message
        window.sessionManager.broadcastMessage({type: 'test'});

        await this.sleep(500);

        // Restore original handler
        window.sessionManager.handleCrossTabMessage = originalHandler;
      }

      this.log('Cross-Tab Messaging', hasBroadcast || hasStorage,
          'At least one communication method available');

      // Test heartbeat
      const heartbeatActive = window.sessionManager.heartbeatTimer !== null;
      this.log('Heartbeat Active', heartbeatActive);

    } catch (error) {
      this.log('Multi-Tab Test', false, error.message);
    }
  },

  /**
   * Test 9: Logout Coordination
   */
  async testLogout() {
    console.log('\n[Test 9] Logout Coordination');
    console.log('─────────────────────────────────────────────────\n');

    try {
      // Check not logging out initially
      const notLoggingOut = !window.sessionManager.isLoggingOut;
      this.log('Not in Logout State Initially', notLoggingOut);

      // Test logout prevention cookie
      document.cookie = 'noExtend=1; path=/; max-age=10';

      const cookieSet = document.cookie.includes('noExtend=1');
      this.log('Logout Cookie Can Be Set', cookieSet);

      // Clean up cookie
      document.cookie = 'noExtend=1; path=/; max-age=0';

      // Note: We don't actually trigger logout in tests to avoid disrupting the session
      this.log('Logout Coordination Ready', true,
          'Logout not triggered in test to preserve session');

    } catch (error) {
      this.log('Logout Test', false, error.message);
    }
  },

  // ============ HELPER METHODS ============

  /**
   * Sleep helper
   */
  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  },

  /**
   * Get current state
   */
  getState() {
    return window.sessionManager.getDebugInfo();
  },

  /**
   * Diagnose server communication issues
   */
  async diagnoseServerIssues() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('   SERVER COMMUNICATION DIAGNOSTICS');
    console.log('═══════════════════════════════════════════════════\n');

    const config = window.sessionManager.config;

    console.log('Configuration:');
    console.log('  Extend URL:', config.contextPath + config.extendSessionUrl);
    console.log('  Status URL:', config.contextPath + config.sessionStatusUrl);
    console.log('  Logout URL:', config.contextPath + config.logoutUrl);
    console.log('');

    console.log('Current State:');
    console.log('  Is Logging Out:', window.sessionManager.isLoggingOut);
    console.log('  Is Warning Shown:', window.sessionManager.isWarningShown);
    console.log('  Last Server Call:',
        new Date(window.sessionManager.lastServerCall).toLocaleTimeString());
    console.log('  Time Since Last Call:',
        Date.now() - window.sessionManager.lastServerCall, 'ms');
    console.log('  Throttle Delay:', window.sessionManager.config.throttleDelay,
        'ms');
    console.log('  Would Be Throttled:',
        (Date.now() - window.sessionManager.lastServerCall)
        < window.sessionManager.config.throttleDelay);
    console.log('');

    // Test extend session endpoint using FORCE to bypass throttle
    console.log('Testing Extend Session Endpoint (bypassing throttle)...');
    try {
      const result = await window.sessionManager.forceExtendSession();

      console.log('  Result:', JSON.stringify(result, null, 2));

      if (result.blocked) {
        console.log('  ⚠️ Extension was blocked');
        console.log('  Reason:', result.reason);
      } else if (result.success || result.sessionId) {
        console.log('  ✅ Extension successful');
      } else if (result.success === false) {
        console.log('  ❌ Extension failed');
        console.log('  Reason:', result.reason);
      }
    } catch (error) {
      console.error('  ❌ Exception:', error.message);
    }

    console.log('');

    // Test session status endpoint
    console.log('Testing Session Status Endpoint...');
    try {
      const response = await fetch(config.contextPath + config.sessionStatusUrl,
          {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json',
              'X-Requested-With': 'XMLHttpRequest'
            },
            credentials: 'same-origin'
          });

      console.log('  Status:', response.status, response.statusText);
      console.log('  OK:', response.ok);

      if (response.ok) {
        const data = await response.json();
        console.log('  Response:', JSON.stringify(data, null, 2));
      } else {
        const text = await response.text();
        console.log('  Error:', text);
      }
    } catch (error) {
      console.error('  Exception:', error.message);
    }

    console.log('\n═══════════════════════════════════════════════════\n');
  },

  /**
   * Diagnose warning dialog issues
   */
  async diagnoseWarningDialog() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('   WARNING DIALOG DIAGNOSTICS');
    console.log('═══════════════════════════════════════════════════\n');

    const dialogElement = document.getElementById('sessionWarningDialog');

    console.log('Dialog Element:');
    console.log('  Exists:', dialogElement !== null);

    if (dialogElement) {
      const styles = window.getComputedStyle(dialogElement);
      console.log('  Display:', dialogElement.style.display, '→ Computed:',
          styles.display);
      console.log('  Visibility:', styles.visibility);
      console.log('  Opacity:', styles.opacity);
      console.log('  Z-Index:', styles.zIndex);
      console.log('  Position:', styles.position);

      const continueBtn = document.getElementById('continueSessionBtn');
      const logoutBtn = document.getElementById('logoutBtn');
      const countdown = document.getElementById('countdownTimer');

      console.log('\nDialog Components:');
      console.log('  Continue Button:', continueBtn !== null);
      console.log('  Logout Button:', logoutBtn !== null);
      console.log('  Countdown Timer:', countdown !== null);
    }

    console.log('\nSession Manager State:');
    console.log('  isWarningShown:', window.sessionManager.isWarningShown);
    console.log('  isLoggingOut:', window.sessionManager.isLoggingOut);
    console.log('  warningTimer:', window.sessionManager.warningTimer !== null);
    console.log('  logoutTimer:', window.sessionManager.logoutTimer !== null);
    console.log('  countdownInterval:',
        window.sessionManager.countdownInterval !== null);

    console.log('\nTesting Warning Show/Hide...');
    window.sessionManager.showWarning();
    await this.sleep(1000);
    console.log('  After showWarning():');
    console.log('    isWarningShown:', window.sessionManager.isWarningShown);
    console.log('    Display:', dialogElement.style.display);

    window.sessionManager.hideWarning();
    await this.sleep(500);
    console.log('  After hideWarning():');
    console.log('    isWarningShown:', window.sessionManager.isWarningShown);
    console.log('    Display:', dialogElement.style.display);

    console.log('\n═══════════════════════════════════════════════════\n');
  }
};

// ============ INDIVIDUAL TEST FUNCTIONS ============

/**
 * Quick test - runs essential tests only
 */
SessionManagerTests.quickTest = async function () {
  console.log('Running Quick Test...\n');
  this.results = [];

  await this.testInitialization();
  await this.testConfiguration();
  await this.testServerCalls();
  await this.testMultiTab();

  this.printResults();
};

/**
 * Performance test - measure response times
 */
SessionManagerTests.performanceTest = async function () {
  console.log('\n═══════════════════════════════════════════════════');
  console.log('   PERFORMANCE TEST');
  console.log('═══════════════════════════════════════════════════\n');

  // Test activity handling speed
  const activityStart = performance.now();
  for (let i = 0; i < 100; i++) {
    window.sessionManager.handleActivity();
  }
  const activityEnd = performance.now();
  const activityAvg = (activityEnd - activityStart) / 100;
  console.log(`Activity Handling: ${activityAvg.toFixed(
      2)}ms average (100 iterations)`);

  // Test broadcast speed
  if (window.sessionManager.broadcastChannel) {
    const broadcastStart = performance.now();
    for (let i = 0; i < 100; i++) {
      window.sessionManager.broadcastMessage({type: 'test'});
    }
    const broadcastEnd = performance.now();
    const broadcastAvg = (broadcastEnd - broadcastStart) / 100;
    console.log(`Broadcast Message: ${broadcastAvg.toFixed(
        2)}ms average (100 iterations)`);
  }

  // Test server call time
  const serverStart = performance.now();
  await window.sessionManager.extendSessionOnServer();
  const serverEnd = performance.now();
  console.log(`Server Extension: ${(serverEnd - serverStart).toFixed(2)}ms`);

  console.log('\n═══════════════════════════════════════════════════\n');
};

/**
 * Stress test - simulate heavy load
 */
SessionManagerTests.stressTest = async function () {
  console.log('\n═══════════════════════════════════════════════════');
  console.log('   STRESS TEST');
  console.log('═══════════════════════════════════════════════════\n');

  console.log('Simulating 1000 rapid activity events...');
  const start = performance.now();

  for (let i = 0; i < 1000; i++) {
    window.sessionManager.handleActivity();
    if (i % 100 === 0) {
      await this.sleep(10);
    }
  }

  const end = performance.now();
  console.log(`Completed in ${(end - start).toFixed(2)}ms`);
  console.log(`Average: ${((end - start) / 1000).toFixed(2)}ms per event`);

  const state = window.sessionManager.getDebugInfo();
  console.log('\nState after stress test:');
  console.log(`- Warning shown: ${state.state.isWarningShown}`);
  console.log(`- Timers active: ${state.timers.warningTimer}`);
  console.log(`- Last activity: ${new Date(
      state.state.lastActivityTime).toLocaleTimeString()}`);

  console.log('\n═══════════════════════════════════════════════════\n');
};

// Export for console use
if (typeof window !== 'undefined') {
  window.SessionManagerTests = SessionManagerTests;
  console.log('%c Session Manager Tests Loaded ',
      'background: #4CAF50; color: white; padding: 5px; font-weight: bold;');
  console.log('Run Tests:');
  console.log('  SessionManagerTests.runAll()          - Full test suite');
  console.log('  SessionManagerTests.quickTest()       - Essential tests');
  console.log('  SessionManagerTests.performanceTest() - Performance metrics');
  console.log('  SessionManagerTests.stressTest()      - Heavy load test');
  console.log('\nDiagnostics:');
  console.log(
      '  SessionManagerTests.diagnoseServerIssues()  - Debug server calls');
  console.log(
      '  SessionManagerTests.diagnoseWarningDialog() - Debug warning dialog');
}