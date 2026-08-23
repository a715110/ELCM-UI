// ========================================
// ENHANCED UNIVERSAL USER NAME DETECTION
// ========================================
function setupGenericUserNameClicks() {
  console.log('🎯 Setting up generic user name clicks for getDisplayNameForUser...');

  // Remove existing listener
  //Byoung if (window.genericUserNameListener) {
  //  document.removeEventListener('click', window.genericUserNameListener, true);
  //}

  // Target the commandLink by its CSS class
  // Byoung: document.querySelectorAll('.user-name-link').forEach(link => {
  //   if(link.genericUserNameListener) {
  //     link.removeEventListener('click', window.genericUserNameListener, true);
  //   }
  // });

  window.genericUserNameListener = function(event) {
    try {
      const clickedElement = event.target;

      // Enhanced detection for getDisplayNameForUser pattern
      //Byoung: const userNameInfo = detectGenericUserNameClick(clickedElement);

      // Check if clicked element or its parent is a user-name-link
      const userLink = clickedElement.closest('.user-name-link');

      if (!userLink) {
        return; // Not a user name link, ignore this click
      }

      // Enhanced detection for getDisplayNameForUser pattern
      const userNameInfo = detectGenericUserNameClick(clickedElement);

      if (userNameInfo.isMatch) {
        console.log('✅ Generic user name clicked:', userNameInfo.displayName, 'LoginId:', userNameInfo.loginId);

        // Prevent default behavior
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();

        // Show popup matching your screenshot
        showUserProfilePopupMatchingScreenshot(userNameInfo, clickedElement);
      }

    } catch (error) {
      console.error('Error in generic user name click handler:', error);
    }
  };

  function detectGenericUserNameClick(element) {
    // Method 1: Direct data-login-id attribute
    if (element.dataset && element.dataset.loginId) {
      return {
        isMatch: true,
        displayName: element.textContent.trim(),
        loginId: element.dataset.loginId,
        context: 'direct-attribute'
      };
    }

    // Method 2: Element with clickable-user-name class
    if (element.classList.contains('clickable-user-name') ||
        element.classList.contains('user-name') ||
        element.classList.contains('username')) {
      const loginId = findLoginIdForElement(element);
      if (loginId) {
        return {
          isMatch: true,
          displayName: element.textContent.trim(),
          loginId: loginId,
          context: 'css-class'
        };
      }
    }

    // Method 3: Check if element is in a container with data-login-id
    const containerWithLoginId = element.closest('[data-login-id]');
    if (containerWithLoginId && isLikelyUserNameText(element.textContent)) {
      return {
        isMatch: true,
        displayName: element.textContent.trim(),
        loginId: containerWithLoginId.dataset.loginId,
        context: 'container-attribute'
      };
    }

    // Method 4: Look for pattern "LastName, FirstName" with nearby login ID
    const text = element.textContent?.trim();
    if (text && text.includes(',') && text.split(',').length === 2) {
      const loginId = findLoginIdNearElement(element);
      if (loginId) {
        return {
          isMatch: true,
          displayName: text,
          loginId: loginId,
          context: 'name-pattern'
        };
      }
    }

    // Method 5: Check parent elements for user name context
    let parent = element.parentElement;
    let depth = 0;
    while (parent && depth < 3) {
      // Look for avatar containers or comment structures
      if (parent.querySelector('.ui-avatar, .avatar, img[src*="avatar"]') ||
          parent.classList.contains('ui-g-6') ||
          parent.dataset.loginId) {

        const loginId = findLoginIdInContainer(parent);
        if (loginId && isLikelyUserNameText(text)) {
          return {
            isMatch: true,
            displayName: text,
            loginId: loginId,
            context: 'parent-context'
          };
        }
      }
      parent = parent.parentElement;
      depth++;
    }

    return { isMatch: false };
  }

  function isLikelyUserNameText(text) {
    if (!text || text.length < 2) return false;

    // Exclude obviously non-user text
    const excludePatterns = [
      /^\d+$/, // Only numbers
      /ago$/, // Time indicators
      /^(http|https)/, // URLs
      /[<>{}[\]]/, // Markup
      /@(?![\w\s]+$)/ // Email (but allow @mentions)
    ];

    for (const pattern of excludePatterns) {
      if (pattern.test(text)) return false;
    }

    // Include likely user name patterns
    return text.length <= 50 &&
        (text.includes(',') || // "Last, First"
            text.split(/\s+/).length <= 3); // Up to 3 words
  }

  function findLoginIdForElement(element) {
    // Check element itself
    if (element.dataset?.loginId) return element.dataset.loginId;

    // Check parent containers
    const container = element.closest('[data-login-id]');
    if (container) return container.dataset.loginId;

    // Check nearby hidden inputs
    return findLoginIdNearElement(element);
  }

  function findLoginIdNearElement(element) {
    // Look in the same container
    const container = element.closest('.ui-g, .card, .panel, tr, form') || element.parentElement;
    return findLoginIdInContainer(container);
  }

  function findLoginIdInContainer(container) {
    if (!container) return null;

    // Check data attributes
    if (container.dataset?.loginId) return container.dataset.loginId;

    // Check hidden inputs
    const hiddenInputs = container.querySelectorAll('input[type="hidden"]');
    for (const input of hiddenInputs) {
      if (input.name && (
          input.name.includes('loginId') ||
          input.name.includes('createdBy') ||
          input.name.includes('assignedTo')
      ) && input.value?.trim()) {
        return input.value.trim();
      }
    }

    // Check other elements with login data
    const loginElements = container.querySelectorAll('[data-login-id], [data-created-by]');
    for (const el of loginElements) {
      const loginId = el.dataset.loginId || el.dataset.createdBy;
      if (loginId?.trim()) return loginId.trim();
    }

    return null;
  }

  // Add the listener
  //document.addEventListener('click', window.genericUserNameListener, true);

  // Byoung Target the commandLink by its CSS class
  // const userNameLinks = document.querySelectorAll('.user-name-link');
  // userNameLinks.forEach(link => {
  //   link.addEventListener('click', window.genericUserNameListener, true);
  // });

  // Add the listener using event delegation (works for all pages)
  document.addEventListener('click', window.genericUserNameListener, true);

  console.log('✅ Generic user name click system ready');
}

/**
 * Get user initials from display name
 * This function was missing and causing the error
 */
function getInitials(displayName) {
  if (!displayName || typeof displayName !== 'string') {
    return '?';
  }

  const name = displayName.trim();
  if (!name) return '?';

  // Handle "LastName, FirstName" format
  if (name.includes(',')) {
    const parts = name.split(',').map(p => p.trim());
    if (parts.length >= 2) {
      const lastName = parts[0];
      const firstName = parts[1];
      const lastInitial = lastName.charAt(0).toUpperCase();
      const firstInitial = firstName.charAt(0).toUpperCase();
      return firstInitial + lastInitial; // First name initial + Last name initial
    }
  }

  // Handle "FirstName LastName" or single name format
  const words = name.split(/\s+/).filter(word => word.length > 0);
  if (words.length >= 2) {
    // Multiple words: take first letter of first and last word
    const firstInitial = words[0].charAt(0).toUpperCase();
    const lastInitial = words[words.length - 1].charAt(0).toUpperCase();
    return firstInitial + lastInitial;
  } else if (words.length === 1) {
    // Single word: take first two letters
    const word = words[0];
    if (word.length >= 2) {
      return word.substring(0, 2).toUpperCase();
    } else {
      return word.charAt(0).toUpperCase() + '?';
    }
  }

  return '?';
}

// ========================================
// INITIALIZATION
// ========================================

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
  setTimeout(function() {
    setupGenericUserNameClicks();
    console.log('🚀 Generic user name system initialized');
  }, 1000);
});

// Re-initialize after AJAX updates
function reinitializeGenericUserNames() {
  setTimeout(function() {
    setupGenericUserNameClicks();
    console.log('🔄 Generic user name system reinitialized');
  }, 300);
}

// Export for manual use
window.setupGenericUserNameClicks = setupGenericUserNameClicks;
window.reinitializeGenericUserNames = reinitializeGenericUserNames;

// Also integrate with existing mention system
if (window.reinitializeMentionSystem) {
  const originalReinit = window.reinitializeMentionSystem;
  window.reinitializeMentionSystem = function() {
    originalReinit();
    reinitializeGenericUserNames();
  };
}
//End of user profile.js