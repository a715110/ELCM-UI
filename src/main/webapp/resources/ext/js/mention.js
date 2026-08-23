/**
 * mention.js - Enhanced Mention Support for ECWS
 *
 * This file handles @mention functionality in Quill editors.
 * DM (Direct Message) functionality has been moved to dm-multichannel.js
 * which supports multiple messaging channels.
 *
 * Load order in template.xhtml:
 *   1. mention.js (this file)
 *   2. dm-multichannel.js (DM functionality)
 */

// ========================================
// DEBUG UTILITIES
// ========================================
const MENTION_DEBUG = {
  enabled: false,
  log: function(...args) {
    if (MENTION_DEBUG.enabled) console.log('[MENTION]', ...args);
  },
  warn: function(...args) {
    if (MENTION_DEBUG.enabled) console.warn('[MENTION]', ...args);
  },
  error: function(...args) {
    if (MENTION_DEBUG.enabled) console.error('[MENTION]', ...args);
  },
  toggle: function() {
    MENTION_DEBUG.enabled = !MENTION_DEBUG.enabled;
    console.log('[MENTION] Debug logging ' + (MENTION_DEBUG.enabled ? 'ENABLED' : 'DISABLED'));
    return MENTION_DEBUG.enabled;
  },
  enable: function() {
    MENTION_DEBUG.enabled = true;
    console.log('[MENTION] Debug logging ENABLED');
  },
  disable: function() {
    MENTION_DEBUG.enabled = false;
    console.log('[MENTION] Debug logging DISABLED');
  }
};

MENTION_DEBUG.disable();
window.mentionDebug = MENTION_DEBUG;

// ========================================
// ADD MENTION STYLES TO PAGE
// ========================================
function addMentionStyles() {
  if (document.getElementById('mention-styles')) return;

  const style = document.createElement('style');
  style.id = 'mention-styles';
  style.textContent = `
    /* Target the exact spans that Quill creates with working selectUser function */
    .ql-editor span[style*="background: rgb(227, 242, 253)"],
    .ql-editor span[style*="background-color: rgb(227, 242, 253)"],
    .ql-editor span[style*="background: #e3f2fd"],
    .ql-editor span[style*="background-color: #e3f2fd"],
    .ql-editor span[style*="background-color:#e3f2fd"] {
      /* Pill styling that makes mentions look like Teams/YouTube */
      padding: 3px 8px !important;
      border-radius: 12px !important;
      margin: 0 2px !important;
      display: inline-block !important;
      border: 1px solid #bbdefb !important;
      cursor: pointer !important;
      white-space: nowrap !important;
      user-select: none !important;
      transition: all 0.2s ease !important;
      text-decoration: none !important;
      font-weight: 600 !important;
    }

    /* Hover effect for pill mentions */
    .ql-editor span[style*="background: rgb(227, 242, 253)"]:hover,
    .ql-editor span[style*="background-color: rgb(227, 242, 253)"]:hover,
    .ql-editor span[style*="background: #e3f2fd"]:hover,
    .ql-editor span[style*="background-color: #e3f2fd"]:hover,
    .ql-editor span[style*="background-color:#e3f2fd"]:hover {
      background-color: #bbdefb !important;
      transform: translateY(-1px) !important;
      box-shadow: 0 2px 6px rgba(25, 118, 210, 0.3) !important;
    }

    /* Prevent accidental text selection/editing of mentions */
    .ql-editor span[style*="background"] {
      -webkit-user-select: none !important;
      -moz-user-select: none !important;
      -ms-user-select: none !important;
      user-select: none !important;
    }

    /* Optional: Teams style variant (gray) */
    .ql-editor .mention-teams {
      background-color: #f3f2f1 !important;
      color: #323130 !important;
      border: 1px solid #c8c6c4 !important;
      border-radius: 3px !important;
    }

    /* Optional: YouTube style variant (dark blue) */
    .ql-editor .mention-youtube {
      background-color: #065fd4 !important;
      color: white !important;
      border: none !important;
      border-radius: 4px !important;
    }

    /* Dropdown styles */
    .mention-dropdown {
      position: fixed;
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      max-height: 200px;
      overflow-y: auto;
      z-index: 999999;
      min-width: 280px;
      display: none;
      font-family: system-ui, sans-serif;
    }

    .mention-dropdown-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      cursor: pointer;
      transition: background-color 0.2s;
    }

    .mention-dropdown-item:hover {
      background-color: #f8fafc;
    }

    .mention-dropdown-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: 600;
      font-size: 12px;
    }

    .mention-dropdown-text {
      flex: 1;
    }

    .mention-dropdown-name {
      font-weight: 500;
      color: #1e293b;
      font-size: 14px;
    }

    .mention-dropdown-email {
      color: #64748b;
      font-size: 12px;
    }
  `;
  document.head.appendChild(style);
}

// ========================================
// GLOBAL VARIABLES
// ========================================
let cachedUserProfiles = [];
let isLoadingUserProfiles = false;

// Make cachedUserProfiles available globally for dm-multichannel.js
window.cachedUserProfiles = cachedUserProfiles;

// ========================================
// USER PROFILE HANDLING
// ========================================
function handleUserProfilesLoaded(args) {
  MENTION_DEBUG.log('handleUserProfilesLoaded called with args:', args);

  try {
    let hiddenInput = null;
    const possibleIds = [
      'collaborationTaskTabView:hiddenUserProfilesData',
      'hiddenUserProfilesData',
      'collaborationTaskForm:hiddenUserProfilesData'
    ];

    for (const id of possibleIds) {
      hiddenInput = document.getElementById(id);
      if (hiddenInput) {
        MENTION_DEBUG.log('Found hidden input with ID:', id);
        break;
      }
    }

    if (!hiddenInput) {
      const inputs = document.querySelectorAll('input[type="hidden"]');
      for (const input of inputs) {
        if (input.name && input.name.includes('hiddenUserProfilesData')) {
          hiddenInput = input;
          MENTION_DEBUG.log('Found hidden input by name attribute:', input.name);
          break;
        }
      }
    }

    if (hiddenInput && hiddenInput.value && hiddenInput.value.trim() !== '') {
      const rawUserProfiles = JSON.parse(hiddenInput.value);
      cachedUserProfiles = rawUserProfiles.map(convertUserProfileDTOToMentionFormat);
      // Update global reference
      window.cachedUserProfiles = cachedUserProfiles;
      MENTION_DEBUG.log('User profiles loaded successfully:', cachedUserProfiles.length);

      if (window.currentUserProfilesPromise) {
        window.currentUserProfilesPromise.resolve(cachedUserProfiles);
        window.currentUserProfilesPromise = null;
      }
    } else {
      MENTION_DEBUG.warn('No user profiles data received');
      if (window.currentUserProfilesPromise) {
        window.currentUserProfilesPromise.resolve([]);
        window.currentUserProfilesPromise = null;
      }
    }
  } catch (error) {
    MENTION_DEBUG.error('Error parsing user profiles:', error);
    if (window.currentUserProfilesPromise) {
      window.currentUserProfilesPromise.reject(error);
      window.currentUserProfilesPromise = null;
    }
  }
}

function convertUserProfileDTOToMentionFormat(userProfileDTO) {
  const loginId = userProfileDTO.userExtDTO ? userProfileDTO.userExtDTO.loginId : '';
  let initials = '';
  if (userProfileDTO.firstName) initials += userProfileDTO.firstName.charAt(0);
  if (userProfileDTO.lastName) initials += userProfileDTO.lastName.charAt(0);

  return {
    id: userProfileDTO.id,
    firstName: userProfileDTO.firstName,
    lastName: userProfileDTO.lastName,
    displayName: userProfileDTO.displayName ||
        ((userProfileDTO.firstName || '') + ' ' + (userProfileDTO.lastName || '')).trim(),
    userExtDTO: userProfileDTO.userExtDTO,
    email: loginId,
    loginId: loginId,
    employeeCode: userProfileDTO.employeeCode,
    initials: initials.toUpperCase()
  };
}

// ========================================
// QUILL MENTION IMPLEMENTATION
// ========================================

function selectUser(user, currentMentionRange, quillEditor, editorWidget) {
  if (!currentMentionRange) return;

  const mentionText = user.displayName || (user.firstName + ' ' + user.lastName).trim();

  try {
    // Step 1: Delete the @ and query
    quillEditor.deleteText(currentMentionRange.index, currentMentionRange.length);

    // Step 2: Insert plain text first
    quillEditor.insertText(currentMentionRange.index, '@' + mentionText);

    // Step 3: Select the text we just inserted
    quillEditor.setSelection(currentMentionRange.index, mentionText.length + 1);

    // Step 4: Apply consistent formatting with exact color values
    quillEditor.format('bold', true);
    quillEditor.format('color', 'rgb(25, 118, 210)');
    quillEditor.format('background', 'rgb(227, 242, 253)');

    // Step 5: Move cursor to end of mention
    const mentionEndPosition = currentMentionRange.index + mentionText.length + 1;
    quillEditor.setSelection(mentionEndPosition);

    // Step 6: Insert space with clean formatting
    quillEditor.insertText(mentionEndPosition, ' ');

    // Step 7: Set cursor after space and clear ALL formatting
    const finalCursorPosition = mentionEndPosition + 1;
    quillEditor.setSelection(finalCursorPosition);

    // Step 8: Clear all formatting completely
    quillEditor.format('bold', false);
    quillEditor.format('color', false);
    quillEditor.format('background', false);
    quillEditor.format('italic', false);
    quillEditor.format('underline', false);

    // Step 9: Force clean state by removing any inherited formatting
    quillEditor.removeFormat(finalCursorPosition, 0);

    MENTION_DEBUG.log('Successfully inserted mention with consistent formatting:', mentionText);

  } catch (error) {
    MENTION_DEBUG.error('Error inserting mention:', error);
  }
}

// ========================================
// ENHANCED EDITOR INITIALIZATION
// ========================================
function initMentionForEditor(editorWidgetVar) {
  const editorWidget = PrimeFaces.widgets[editorWidgetVar];
  if (!editorWidget) {
    MENTION_DEBUG.warn('Editor widget not found:', editorWidgetVar);
    return;
  }

  // Get Quill editor
  let quillEditor = null;
  if (editorWidget.editor) {
    quillEditor = editorWidget.editor;
  } else if (editorWidget.jq && editorWidget.jq.data('quill')) {
    quillEditor = editorWidget.jq.data('quill');
  } else {
    const quillContainer = editorWidget.jq.find('.ql-container')[0];
    if (quillContainer && quillContainer.__quill) {
      quillEditor = quillContainer.__quill;
    }
  }

  const editorElement = editorWidget.jq.find('.ql-editor')[0];

  if (!quillEditor || !editorElement) {
    MENTION_DEBUG.warn('Could not access Quill editor');
    return;
  }

  // Load user profiles function
  function loadUserProfiles() {
    if (cachedUserProfiles.length > 0) {
      return Promise.resolve(cachedUserProfiles);
    }

    if (isLoadingUserProfiles) {
      return new Promise(resolve => {
        const checkLoading = setInterval(() => {
          if (!isLoadingUserProfiles) {
            clearInterval(checkLoading);
            resolve(cachedUserProfiles);
          }
        }, 100);
      });
    }

    MENTION_DEBUG.log('Loading user profiles...');
    isLoadingUserProfiles = true;

    return new Promise((resolve, reject) => {
      window.currentUserProfilesPromise = { resolve, reject };
      try {
        loadUserProfilesRemoteCmd();
      } catch (error) {
        MENTION_DEBUG.error('Error calling RemoteCommand:', error);
        isLoadingUserProfiles = false;
        reject(error);
      }
    });
  }

  // Filter users based on query
  async function getUsers(query) {
    try {
      const profiles = await loadUserProfiles();
      if (!query) return profiles.slice(0, 8);

      const queryLower = query.toLowerCase();
      return profiles.filter(user => {
        return (user.displayName && user.displayName.toLowerCase().includes(queryLower)) ||
            (user.firstName && user.firstName.toLowerCase().includes(queryLower)) ||
            (user.lastName && user.lastName.toLowerCase().includes(queryLower)) ||
            (user.email && user.email.toLowerCase().includes(queryLower)) ||
            (user.loginId && user.loginId.toLowerCase().includes(queryLower)) ||
            (user.employeeCode && user.employeeCode.toLowerCase().includes(queryLower));
      }).slice(0, 8);
    } catch (error) {
      MENTION_DEBUG.error('Error getting users:', error);
      return [];
    }
  }

  // Mention state
  let mentionDropdown = null;
  let currentMentionRange = null;

  // Create dropdown
  function createDropdown() {
    if (mentionDropdown) return;

    mentionDropdown = document.createElement('div');
    mentionDropdown.style.cssText = `
      position: fixed;
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      max-height: 200px;
      overflow-y: auto;
      z-index: 999999;
      min-width: 280px;
      display: none;
      font-family: system-ui, sans-serif;
    `;
    document.body.appendChild(mentionDropdown);
  }

  // Position dropdown
  function positionDropdown() {
    if (!mentionDropdown || !currentMentionRange) return;

    const selection = quillEditor.getSelection();
    if (!selection) return;

    const bounds = quillEditor.getBounds(selection.index);
    const editorRect = editorElement.getBoundingClientRect();

    mentionDropdown.style.top = (editorRect.top + bounds.top + bounds.height + 5) + 'px';
    mentionDropdown.style.left = (editorRect.left + bounds.left) + 'px';
    mentionDropdown.style.display = 'block';
  }

  // Create user item
  function createUserItem(user) {
    const item = document.createElement('div');
    item.style.cssText = `
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      cursor: pointer;
      transition: background-color 0.2s;
    `;

    item.addEventListener('mouseenter', () => {
      item.style.backgroundColor = '#f8fafc';
    });
    item.addEventListener('mouseleave', () => {
      item.style.backgroundColor = 'transparent';
    });

    // Avatar
    const avatar = document.createElement('div');
    avatar.style.cssText = `
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: 600;
      font-size: 12px;
    `;
    avatar.textContent = user.initials || '?';

    // Text container
    const textContainer = document.createElement('div');
    textContainer.style.cssText = 'flex: 1;';

    const name = document.createElement('div');
    name.style.cssText = 'font-weight: 500; color: #1e293b; font-size: 14px;';
    name.textContent = user.displayName || (user.firstName + ' ' + user.lastName).trim();

    const email = document.createElement('div');
    email.style.cssText = 'color: #64748b; font-size: 12px;';
    email.textContent = user.email || user.loginId || '';

    textContainer.appendChild(name);
    textContainer.appendChild(email);
    item.appendChild(avatar);
    item.appendChild(textContainer);

    item.addEventListener('click', () => {
      selectUser(user, currentMentionRange, quillEditor, editorWidget);
      hideDropdown();
    });

    return item;
  }

  // Show dropdown
  async function showDropdown(query) {
    createDropdown();
    mentionDropdown.innerHTML = '<div style="padding: 12px 16px; color: #64748b; font-size: 14px;">Loading users...</div>';
    positionDropdown();

    try {
      const users = await getUsers(query);
      mentionDropdown.innerHTML = '';

      if (!users || users.length === 0) {
        const noResults = document.createElement('div');
        noResults.style.cssText = 'padding: 12px 16px; color: #64748b; font-size: 14px;';
        noResults.textContent = 'No users found';
        mentionDropdown.appendChild(noResults);
      } else {
        users.forEach(user => {
          mentionDropdown.appendChild(createUserItem(user));
        });
      }
      positionDropdown();
    } catch (error) {
      MENTION_DEBUG.error('Error showing dropdown:', error);
      mentionDropdown.innerHTML = '<div style="padding: 12px 16px; color: #ef4444; font-size: 14px;">Error loading users</div>';
    }
  }

  // Hide dropdown
  function hideDropdown() {
    if (mentionDropdown) {
      mentionDropdown.style.display = 'none';
    }
    currentMentionRange = null;
  }

  // Check for mentions
  function checkMentions() {
    const selection = quillEditor.getSelection();
    if (!selection) return;

    const text = quillEditor.getText(0, selection.index);
    const atIndex = text.lastIndexOf('@');

    if (atIndex !== -1) {
      const query = text.substring(atIndex + 1);

      if (!query.includes(' ') && !query.includes('\n')) {
        currentMentionRange = {
          index: atIndex,
          length: query.length + 1
        };

        MENTION_DEBUG.log('Mention detected, query:', query);
        showDropdown(query);
      } else {
        hideDropdown();
      }
    } else {
      hideDropdown();
    }
  }

  // Event listeners
  quillEditor.on('text-change', (delta, oldDelta, source) => {
    if (source === 'user') {
      setTimeout(checkMentions, 10);
    }
  });

  document.addEventListener('click', (e) => {
    if (mentionDropdown &&
        !mentionDropdown.contains(e.target) &&
        !editorElement.contains(e.target)) {
      hideDropdown();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && mentionDropdown && mentionDropdown.style.display === 'block') {
      hideDropdown();
    }
  });

  // Preload user profiles
  loadUserProfiles().then(() => {
    MENTION_DEBUG.log('User profiles preloaded successfully');
  }).catch(error => {
    MENTION_DEBUG.error('Error preloading user profiles:', error);
  });

  MENTION_DEBUG.log('Enhanced mention support initialized for', editorWidgetVar);
}

// ========================================
// MAIN INITIALIZATION
// ========================================
function initEnhancedMentionSupport() {
  // Add CSS styles for mentions
  addMentionStyles();

  // Find editor widgets
  const possibleWidgetVars = ['commentWgVar', 'descriptionWgVar', 'replyCommentWgVar'];

  for (const widgetVar of possibleWidgetVars) {
    if (PrimeFaces.widgets[widgetVar]) {
      MENTION_DEBUG.log('Found editor widget:', widgetVar);
      initMentionForEditor(widgetVar);
      break;
    }
  }

  // Fallback: look for any text editor
  if (!possibleWidgetVars.some(wv => PrimeFaces.widgets[wv])) {
    MENTION_DEBUG.warn('No standard editor widgets found, searching for any text editor...');
    for (const widgetVar in PrimeFaces.widgets) {
      const widget = PrimeFaces.widgets[widgetVar];
      if (widget && widget.jq && widget.jq.hasClass('ui-editor')) {
        MENTION_DEBUG.log('Found text editor widget:', widgetVar);
        initMentionForEditor(widgetVar);
        break;
      }
    }
  }
}

// ========================================
// INITIALIZATION AND UTILITY FUNCTIONS
// ========================================

// Utility functions
function manualInitMentions() {
  initEnhancedMentionSupport();
}

function forceInitMentions() {
  initEnhancedMentionSupport();
}

function keyPressedInCommentEditor() {
  MENTION_DEBUG.log('Using enhanced mention system with styling');
}

// ========================================
// MENTION CLICK DETECTION
// ========================================

function setupEnhancedMentionClicks() {
  // Remove any existing listeners to prevent duplicates
  if (window.mentionClickListener) {
    document.removeEventListener('click', window.mentionClickListener, true);
  }

  // Create new comprehensive click handler
  window.mentionClickListener = function(event) {
    try {
      const clickedElement = event.target;

      // Multiple detection methods for consistent mentions
      const isMention = checkIfElementIsMentionEnhanced(clickedElement);

      if (isMention.isMatch) {
        MENTION_DEBUG.log('Mention clicked:', isMention.text, 'Method:', isMention.method);

        // Prevent editor interference
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();

        // Show popup - uses dm-multichannel.js if available, otherwise fallback
        showEnhancedMentionPopup(isMention.text, isMention.element || clickedElement);
      }

    } catch (error) {
      console.error('Error in enhanced mention click handler:', error);
    }
  };

  function checkIfElementIsMentionEnhanced(element) {
    const text = element.textContent || '';
    const style = window.getComputedStyle(element);

    // Method 1: Check for our consistent mention styling
    const hasConsistentBackground =
        style.backgroundColor.includes('227, 242, 253') ||
        style.backgroundColor.includes('rgb(227, 242, 253)') ||
        style.backgroundColor === 'rgb(227, 242, 253)';

    const hasConsistentColor =
        style.color.includes('25, 118, 210') ||
        style.color.includes('rgb(25, 118, 210)') ||
        style.color === 'rgb(25, 118, 210)';

    const startsWithAt = text.startsWith('@');

    if (hasConsistentBackground && hasConsistentColor && startsWithAt) {
      return { isMatch: true, text: text, method: 'consistent-styling', element: element };
    }

    // Method 2: Check element tag and styling combination
    if ((element.tagName === 'SPAN' || element.tagName === 'STRONG') &&
        startsWithAt && (hasConsistentBackground || hasConsistentColor)) {
      return { isMatch: true, text: text, method: 'tag-styling', element: element };
    }

    // Method 3: Check parent elements (in case click is on child)
    let parent = element.parentElement;
    let depth = 0;
    while (parent && parent !== document.body && depth < 3) {
      const parentStyle = window.getComputedStyle(parent);
      const parentText = parent.textContent || '';

      const parentHasBackground =
          parentStyle.backgroundColor.includes('227, 242, 253') ||
          parentStyle.backgroundColor === 'rgb(227, 242, 253)';

      const parentHasColor =
          parentStyle.color.includes('25, 118, 210') ||
          parentStyle.color === 'rgb(25, 118, 210)';

      const parentStartsWithAt = parentText.startsWith('@');

      if (parentHasBackground && parentHasColor && parentStartsWithAt) {
        return { isMatch: true, text: parentText, method: 'parent-styling', element: parent };
      }

      parent = parent.parentElement;
      depth++;
    }

    // Method 4: Fallback - check for any styled element with @ that looks like a mention
    if (startsWithAt && text.length > 1 &&
        (style.backgroundColor !== 'rgba(0, 0, 0, 0)' && style.backgroundColor !== 'transparent')) {
      return { isMatch: true, text: text, method: 'fallback-styling', element: element };
    }

    return { isMatch: false };
  }

  // Add listener with capture phase
  document.addEventListener('click', window.mentionClickListener, true);
}

// Helper function to find the Quill editor
function findQuillEditor() {
  try {
    const possibleWidgetVars = ['commentWgVar', 'descriptionWgVar', 'replyCommentWgVar'];

    for (const widgetVar of possibleWidgetVars) {
      if (window.PrimeFaces && PrimeFaces.widgets && PrimeFaces.widgets[widgetVar]) {
        const widget = PrimeFaces.widgets[widgetVar];
        if (widget.editor) {
          return widget.editor;
        } else if (widget.jq) {
          const container = widget.jq.find('.ql-container')[0];
          if (container && container.__quill) {
            return container.__quill;
          }
        }
      }
    }

    // Fallback: try to find any Quill editor in the DOM
    const containers = document.querySelectorAll('.ql-container');
    for (const container of containers) {
      if (container.__quill) {
        return container.__quill;
      }
    }

    return null;
  } catch (error) {
    console.error('Error finding Quill editor:', error);
    return null;
  }
}

// ========================================
// MENTION POPUP (delegates to dm-multichannel.js)
// ========================================

function showEnhancedMentionPopup(mentionText, element) {
  const upn = resolveRecipientUpnFromMention(mentionText);
  const displayName = (mentionText || '').replace(/^@/, '').trim();

  // Use dm-multichannel.js if available (preferred)
  if (typeof window.openDmPopupWithChannel === 'function') {
    window.openDmPopupWithChannel({
      anchor: element,
      loginId: upn,
      displayName: displayName,
      openComposer: false
    });
    return;
  }

  // Fallback to openDmPopupUnified if available
  if (typeof window.openDmPopupUnified === 'function') {
    window.openDmPopupUnified({
      anchor: element,
      loginId: upn,
      displayName: displayName,
      openComposer: false
    });
    return;
  }

  // Ultimate fallback: basic popup
  console.warn('[MENTION] DM popup functions not available. Make sure dm-multichannel.js is loaded.');
  alert('User: ' + displayName + '\nEmail: ' + upn);
}

function closeMentionPopup() {
  // Try dm-multichannel.js function first
  if (typeof window.closeDmPopup === 'function') {
    window.closeDmPopup();
    return;
  }

  // Fallback: close mention-clicked-popup
  const popup = document.getElementById('mention-clicked-popup');
  if (popup) {
    popup.style.animation = 'mentionPopupFadeIn 0.2s ease-out reverse';
    setTimeout(() => popup.remove(), 200);
  }
}

// ========================================
// PRIMEFACES AJAX INTEGRATION
// ========================================

function reinitializeMentionSystem() {
  MENTION_DEBUG.log('Reinitializing mention system...');

  // Clear any existing state
  if (window.mentionDropdown) {
    window.mentionDropdown.remove();
    window.mentionDropdown = null;
  }

  // Reset global variables
  isLoadingUserProfiles = false;
  if (window.currentUserProfilesPromise) {
    window.currentUserProfilesPromise = null;
  }

  // Wait for DOM to settle, then reinitialize
  setTimeout(function() {
    // Re-add mention styles (in case they were lost)
    addMentionStyles();

    // Find and reinitialize all editors
    const editorWidgetVars = ['commentWgVar', 'replyCommentWgVar', 'descriptionWgVar'];

    let editorsFound = 0;
    editorWidgetVars.forEach(function(widgetVar) {
      if (window.PrimeFaces && PrimeFaces.widgets && PrimeFaces.widgets[widgetVar]) {
        MENTION_DEBUG.log('Reinitializing editor:', widgetVar);

        try {
          initMentionForEditor(widgetVar);
          editorsFound++;
        } catch (error) {
          console.error('Failed to reinitialize:', widgetVar, error);
        }
      }
    });

    // Reinitialize mention clicks
    setTimeout(function() {
      setupEnhancedMentionClicks();
    }, 200);

    MENTION_DEBUG.log('Mention system reinitialized for', editorsFound, 'editors');

  }, 100);
}

// Enhanced initialization using the correct PrimeFaces approach
function initEnhancedMentionSupportWithAjax() {
  // Initial setup
  initEnhancedMentionSupport();

  // Use the correct PrimeFaces AJAX integration
  if (window.PrimeFaces && PrimeFaces.ajax) {

    // Override the default AJAX success handler
    const originalAjaxSuccess = PrimeFaces.ajax.Response.handle;

    PrimeFaces.ajax.Response.handle = function(xhr) {
      // Call the original handler first
      const result = originalAjaxSuccess.apply(this, arguments);

      // Check if this was a comment-related AJAX call
      try {
        const responseText = xhr.responseText || '';
        if (responseText.includes('comment') ||
            responseText.includes('reply') ||
            responseText.includes('commentsPanel')) {

          MENTION_DEBUG.log('Comment-related AJAX detected, reinitializing mentions...');
          setTimeout(function() {
            reinitializeMentionSystem();
          }, 300);
        }
      } catch (e) {
        // If we can't parse the response, reinitialize anyway to be safe
        setTimeout(function() {
          reinitializeMentionSystem();
        }, 500);
      }

      return result;
    };
  }
}

// MutationObserver for DOM changes
function setupMutationObserver() {
  if (window.mentionMutationObserver) {
    window.mentionMutationObserver.disconnect();
  }

  window.mentionMutationObserver = new MutationObserver(function(mutations) {
    let shouldReinit = false;

    mutations.forEach(function(mutation) {
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach(function(node) {
          if (node.nodeType === Node.ELEMENT_NODE) {
            if (node.querySelector && (
                node.querySelector('.ui-texteditor') ||
                node.querySelector('.ql-editor') ||
                node.querySelector('[id*="comment"]') ||
                node.classList.contains('ui-texteditor') ||
                node.classList.contains('ql-editor')
            )) {
              shouldReinit = true;
            }
          }
        });
      }
    });

    if (shouldReinit) {
      MENTION_DEBUG.log('DOM changes detected, reinitializing mentions...');
      setTimeout(function() {
        reinitializeMentionSystem();
      }, 400);
    }
  });

  // Observe the comments panel and dialog containers
  const observeTargets = [
    document.getElementById('collaborationTaskTabView:commentsPanel'),
    document.body
  ];

  observeTargets.forEach(function(target) {
    if (target) {
      window.mentionMutationObserver.observe(target, {
        childList: true,
        subtree: true
      });
    }
  });
}

// Manual function to force reinit (for debugging)
function forceReinitMentions() {
  MENTION_DEBUG.log('Force reinitializing mentions...');
  reinitializeMentionSystem();
}

// ========================================
// USER PROFILE POPUP (delegates to dm-multichannel.js)
// ========================================

function showUserProfilePopupMatchingScreenshot(userNameInfo, element) {
  const displayName =
      (userNameInfo?.displayName || userNameInfo?.name ||
          (element?.textContent || '')).trim();

  const loginId =
      userNameInfo?.loginId ||
      userNameInfo?.email ||
      userNameInfo?.userExtDTO?.loginId ||
      displayName;

  // Use dm-multichannel.js if available
  if (typeof window.openDmPopupWithChannel === 'function') {
    window.openDmPopupWithChannel({
      anchor: element,
      loginId,
      displayName,
      openComposer: false
    });
    return;
  }

  // Fallback
  if (typeof window.openDmPopupUnified === 'function') {
    window.openDmPopupUnified({
      anchor: element,
      loginId,
      displayName,
      openComposer: false
    });
  }
}

function closeUserProfilePopupScreenshot() {
  if (typeof window.closeDmPopup === 'function') {
    window.closeDmPopup();
    return;
  }

  const popup = document.getElementById('user-profile-popup-screenshot');
  if (popup) {
    popup.style.animation = 'popupSlideIn 0.2s ease-out reverse';
    setTimeout(() => popup.remove(), 200);
  }
}

// ========================================
// HELPER FUNCTIONS
// ========================================

/**
 * Resolve UPN/email from mention text using cached profiles
 */
function resolveRecipientUpnFromMention(mentionText) {
  const clean = (mentionText || '').replace(/^@/, '').trim().toLowerCase();
  if (!clean) return '';

  // Try to find in cached profiles
  let hit = (cachedUserProfiles || []).find(u =>
      (u.displayName && u.displayName.toLowerCase() === clean)
      || ((u.firstName || '') + ' ' + (u.lastName || '')).trim().toLowerCase() === clean
      || (u.loginId && u.loginId.toLowerCase() === clean)
      || (u.email && u.email.toLowerCase() === clean)
  );

  if (hit) return hit.loginId || hit.email || clean;

  // Fallback: if user typed an email-like thing, use it
  if (clean.includes('@')) return clean;

  // Default domain fallback
  const DEFAULT_DOMAIN = 'dodaso.com';
  return `${clean}@${DEFAULT_DOMAIN}`;
}

/**
 * Resolve login ID from text or element (used by dm-multichannel.js)
 */
function resolveLoginId(textOrId, el) {
  const v = (textOrId || '').trim();

  // If it looks like an email or GUID already, use it
  if (/@/.test(v) || /^[0-9a-fA-F-]{36}$/.test(v)) return v;

  // If the clicked mention had data attributes set earlier
  const mentionEl = el && el.closest && (el.closest('[data-mention-email],[data-mention-id]') || el);
  if (mentionEl) {
    const email = mentionEl.getAttribute('data-mention-email');
    const id = mentionEl.getAttribute('data-mention-id');
    if (email) return email;
    if (id) return id;
  }

  // Try from cached profiles
  try {
    if (cachedUserProfiles && Array.isArray(cachedUserProfiles)) {
      const n = v.replace(/^@/,'').toLowerCase();
      const hit = cachedUserProfiles.find(u =>
          (u.loginId && u.loginId.toLowerCase() === n) ||
          (u.email && u.email.toLowerCase() === n) ||
          (u.displayName && u.displayName.toLowerCase() === n)
      );
      if (hit) return hit.loginId || hit.email || hit.id;
    }
  } catch (_) {}

  return null;
}

/**
 * View user profile - can be customized per implementation
 */
function viewUserProfile(loginId) {
  // TODO: Implement user profile view navigation
  console.log('View profile for:', loginId);
  // Example: window.location.href = '/user-profile?loginId=' + encodeURIComponent(loginId);
}

// ========================================
// INITIALIZATION
// ========================================

// Enhanced DOMContentLoaded listener
document.addEventListener('DOMContentLoaded', function() {
  setTimeout(function() {
    // Initialize the mention system
    initEnhancedMentionSupportWithAjax();
    setupEnhancedMentionClicks();

    // Setup DOM observation as backup
    setupMutationObserver();

    window.mentionSystemInitialized = true;
    MENTION_DEBUG.log('Mention system fully initialized');

  }, 1500);
});

// Backup initialization
setTimeout(function() {
  if (!window.mentionSystemInitialized) {
    MENTION_DEBUG.log('Backup initialization triggered...');
    initEnhancedMentionSupportWithAjax();
    setupMutationObserver();
    window.mentionSystemInitialized = true;
  }
}, 4000);

// ========================================
// GLOBAL EXPORTS
// ========================================

// Make functions globally available
window.reinitializeMentionSystem = reinitializeMentionSystem;
window.forceReinitMentions = forceReinitMentions;
window.setupEnhancedMentionClicks = setupEnhancedMentionClicks;
window.resolveLoginId = resolveLoginId;
window.resolveRecipientUpnFromMention = resolveRecipientUpnFromMention;
window.viewUserProfile = viewUserProfile;
window.closeMentionPopup = closeMentionPopup;
window.handleUserProfilesLoaded = handleUserProfilesLoaded;

// Export for user profile popup
window.showUserProfilePopupMatchingScreenshot = showUserProfilePopupMatchingScreenshot;
window.closeUserProfilePopupScreenshot = closeUserProfilePopupScreenshot;

// Export mention system object
window.mentionSystem = {
  init: initEnhancedMentionSupport,
  debug: MENTION_DEBUG,
  forceInit: forceInitMentions,
  reinit: reinitializeMentionSystem
};

// End of mention.js