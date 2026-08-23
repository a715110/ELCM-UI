/**
 * QuickMenu — Spotlight-style command palette
 * Trigger : Alt + M
 * Integrates with PrimeFaces Rain Theme layout
 *
 * Usage: include after layout.js and your page's <h:body> renders.
 *
 * Customise ACTION_SHORTCUTS below to match your app's routes/actions.
 */
(function ($) {
  'use strict';

  /* ============================================================
     CONFIG — edit this section to match your application
     ============================================================ */

  /**
   * Action shortcuts that always appear in the quick menu.
   * Each entry needs:
   *   label  : string  — display name
   *   icon   : string  — PrimeIcons class (pi pi-*)
   *   sub    : string  — short description shown under the label (optional)
   *
   * Provide ONE of:
   *   href   : string  — navigates to this URL on select
   *   action : fn      — called on select instead of navigating
   */
  var ACTION_SHORTCUTS = [
    {
      label : 'Dashboard',
      icon  : 'pi pi-home',
      sub   : 'Go to main dashboard',
      href  : '/dashboard'
    },
    {
      label : 'New Task',
      icon  : 'pi pi-plus-circle',
      sub   : 'Create a new task',
      href  : '/tasks/new'
    },
    {
      label : 'Settings',
      icon  : 'pi pi-cog',
      sub   : 'Open application settings',
      href  : '/settings'
    },
    {
      label : 'Save Page',
      icon  : 'pi pi-save',
      sub   : 'Trigger save on current page',
      action: function () {
        // Clicks the first visible button whose id contains "save" (case-insensitive)
        var saveBtn = $('[id*="save"]:visible, [id*="Save"]:visible').first();
        if (saveBtn.length) {
          saveBtn.click();
        } else {
          console.warn('QuickMenu: no save button found on this page.');
        }
      }
    },
    {
      label : 'Toggle Sidebar',
      icon  : 'pi pi-bars',
      sub   : 'Show / hide the left navigation',
      action: function () {
        $('.menu-button').trigger('click');
      }
    }
  ];

  /** Maximum number of "recently visited" entries stored */
  var MAX_RECENT = 5;

  /** sessionStorage key for recent-page history */
  var RECENT_KEY = 'qm_recent_pages';

  /* ============================================================
     INTERNAL STATE
     ============================================================ */
  var $overlay, $input, $results;
  var allNavItems    = [];   // scraped from sidebar
  var filteredItems  = [];   // currently displayed flat list
  var activeIndex    = -1;   // keyboard cursor

  /* ============================================================
     RECENT PAGE TRACKING
     ============================================================ */
  function getRecent() {
    try {
      return JSON.parse(sessionStorage.getItem(RECENT_KEY) || '[]');
    } catch (e) {
      return [];
    }
  }

  function saveRecent(list) {
    try {
      sessionStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
  }

  /**
   * Call this whenever the user navigates to a page via the quick menu
   * (or call it from your own navigation hooks to auto-track).
   */
  function trackVisit(label, href, icon) {
    if (!href || href === '#' || href === 'javascript:void(0)') return;
    var recent = getRecent().filter(function (r) { return r.href !== href; });
    recent.unshift({ label: label, href: href, icon: icon || 'pi pi-history' });
    if (recent.length > MAX_RECENT) recent = recent.slice(0, MAX_RECENT);
    saveRecent(recent);
  }

  /* ============================================================
     SCRAPE SIDEBAR MENU
     Reads the rendered Rain left-menu links so this file never
     needs manual duplication of your menu structure.
     ============================================================ */
  function scrapeNavItems() {
    var items = [];
    $('.layout-menu a').each(function () {
      var $a    = $(this);
      var href  = $a.attr('href') || '';
      var label = ($a.find('.menuitem-text').text() || $a.text()).trim();
      var $icon = $a.find('i').first();
      var icon  = ($icon.length && $icon.attr('class')) ? $icon.attr('class') : 'pi pi-circle';

      // Skip empty, anchor-only, or icon-only entries
      if (!label || href === '#' || href === '') return;
      // Skip duplicate hrefs
      if (items.some(function (i) { return i.href === href; })) return;

      // Build breadcrumb from parent <li> text if available
      var parentLabel = $a.closest('ul').closest('li')
      .children('a').find('.menuitem-text').text().trim();
      items.push({
        label      : label,
        href       : href,
        icon       : icon,
        parentLabel: parentLabel || ''
      });
    });
    return items;
  }

  /* ============================================================
     DOM BUILDER
     ============================================================ */
  function buildDOM() {
    // Remove any previous instance (e.g. after PF AJAX refresh)
    $('#qmOverlay').remove();

    $overlay = $([
      '<div id="qmOverlay" class="qm-overlay" role="dialog" aria-modal="true" aria-label="Quick Menu">',
      '  <div class="qm-container">',
      '    <div class="qm-search-row">',
      '      <i class="qm-search-icon pi pi-search"></i>',
      '      <input id="qmInput" class="qm-search-input" type="text"',
      '             placeholder="Search pages, actions…" autocomplete="off"',
      '             spellcheck="false" aria-label="Quick menu search" />',
      '      <span class="qm-kbd-hint">ESC</span>',
      '    </div>',
      '    <div id="qmResults" class="qm-results" role="listbox"></div>',
      '    <div class="qm-footer">',
      '      <span class="qm-footer-hint"><kbd>↑↓</kbd> navigate</span>',
      '      <span class="qm-footer-hint"><kbd>↵</kbd> open</span>',
      '      <span class="qm-footer-hint"><kbd>Alt M</kbd> close</span>',
      '    </div>',
      '  </div>',
      '</div>'
    ].join('\n'));

    $(document.body).append($overlay);
    $input   = $overlay.find('#qmInput');
    $results = $overlay.find('#qmResults');
  }

  /* ============================================================
     RENDERING
     ============================================================ */
  function highlight(text, query) {
    if (!query) return $('<span>').text(text).prop('outerHTML');
    var escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return text.replace(new RegExp('(' + escaped + ')', 'gi'),
        '<mark>$1</mark>');
  }

  function buildItemHTML(item, badgeClass, badgeText) {
    var iconClass = item.icon || 'pi pi-circle';
    // Ensure only valid pi classes pass through (strip accidental duplicates)
    if (iconClass.split(' ').filter(function(c){ return c.startsWith('pi-'); }).length === 0) {
      iconClass = 'pi pi-circle';
    }
    var labelHL  = highlight(item.label, $input.val().trim());
    var subText  = item.parentLabel ? item.parentLabel : (item.sub || '');

    return [
      '<button class="qm-item" role="option" data-href="' + (item.href || '') + '" data-action-idx="' + (item.actionIdx !== undefined ? item.actionIdx : '') + '">',
      '  <i class="qm-item-icon ' + iconClass + '"></i>',
      '  <span class="qm-item-body">',
      '    <span class="qm-item-label">' + labelHL + '</span>',
      (subText ? '    <span class="qm-item-sub">' + $('<span>').text(subText).html() + '</span>' : ''),
      '  </span>',
      '  <span class="qm-item-badge ' + badgeClass + '">' + badgeText + '</span>',
      '</button>'
    ].join('');
  }

  function renderResults(query) {
    $results.empty();
    activeIndex   = -1;
    filteredItems = [];

    var q         = (query || '').toLowerCase().trim();
    var recent    = getRecent();

    /* --- RECENT --- */
    if (!q) {
      // Show recents only when search is empty
      var visibleRecent = recent.slice(0, 5);
      if (visibleRecent.length) {
        $results.append('<div class="qm-section"><span class="qm-section-label">Recent</span></div>');
        visibleRecent.forEach(function (r) {
          var item = { label: r.label, href: r.href, icon: r.icon || 'pi pi-history', parentLabel: '' };
          filteredItems.push(item);
          $results.append(buildItemHTML(item, 'qm-badge-recent', 'Recent'));
        });
        $results.append('<div class="qm-separator"></div>');
      }
    }

    /* --- ACTIONS --- */
    var matchedActions = ACTION_SHORTCUTS.filter(function (a, idx) {
      a._idx = idx;  // keep original index for callback
      return !q || a.label.toLowerCase().indexOf(q) !== -1 ||
          (a.sub && a.sub.toLowerCase().indexOf(q) !== -1);
    });

    if (matchedActions.length) {
      $results.append('<div class="qm-section"><span class="qm-section-label">Actions</span></div>');
      matchedActions.forEach(function (a) {
        var item = { label: a.label, icon: a.icon, sub: a.sub || '', href: a.href || '', actionIdx: a._idx, parentLabel: '' };
        filteredItems.push(item);
        $results.append(buildItemHTML(item, 'qm-badge-action', 'Action'));
      });
      if (!q) $results.append('<div class="qm-separator"></div>');
    }

    /* --- NAVIGATION (from sidebar) --- */
    var matchedNav = q
        ? allNavItems.filter(function (n) {
          return n.label.toLowerCase().indexOf(q) !== -1 ||
              n.parentLabel.toLowerCase().indexOf(q) !== -1;
        })
        : allNavItems.slice(0, 8);   // show first 8 when no search term

    if (matchedNav.length) {
      $results.append('<div class="qm-section"><span class="qm-section-label">' + (q ? 'Pages' : 'Navigation') + '</span></div>');
      matchedNav.forEach(function (n) {
        filteredItems.push(n);
        $results.append(buildItemHTML(n, 'qm-badge-nav', 'Page'));
      });
    }

    /* --- EMPTY STATE --- */
    if (filteredItems.length === 0) {
      $results.append([
        '<div class="qm-empty">',
        '  <i class="pi pi-search-minus"></i>',
        '  No results for "' + $('<span>').text(query).html() + '"',
        '</div>'
      ].join(''));
    }

    bindItemClicks();
  }

  /* ============================================================
     ITEM CLICK
     ============================================================ */
  function bindItemClicks() {
    $results.find('.qm-item').on('click', function () {
      activateItem($(this));
    });
  }

  function activateItem($btn) {
    var href      = $btn.data('href');
    var actionIdx = $btn.data('action-idx');

    close();

    if (actionIdx !== '' && actionIdx !== undefined && actionIdx !== null) {
      var act = ACTION_SHORTCUTS[actionIdx];
      if (act) {
        if (typeof act.action === 'function') {
          act.action();
          return;
        }
        href = act.href || href;
      }
    }

    if (href) {
      var label = $btn.find('.qm-item-label').text();
      var icon  = $btn.find('.qm-item-icon').attr('class').replace('qm-item-icon ', '');
      trackVisit(label, href, icon);
      window.location.href = href;
    }
  }

  /* ============================================================
     KEYBOARD NAVIGATION
     ============================================================ */
  function moveCursor(direction) {
    var $items = $results.find('.qm-item');
    if (!$items.length) return;

    $items.removeClass('qm-active');
    activeIndex += direction;

    if (activeIndex < 0)              activeIndex = $items.length - 1;
    if (activeIndex >= $items.length) activeIndex = 0;

    var $active = $items.eq(activeIndex).addClass('qm-active');
    // Scroll into view if needed
    var rTop    = $results[0].scrollTop;
    var rBot    = rTop + $results[0].clientHeight;
    var iTop    = $active[0].offsetTop;
    var iBot    = iTop + $active[0].offsetHeight;
    if (iTop < rTop) $results[0].scrollTop = iTop;
    if (iBot > rBot) $results[0].scrollTop = iBot - $results[0].clientHeight;
  }

  /* ============================================================
     OPEN / CLOSE
     ============================================================ */
  function open() {
    allNavItems = scrapeNavItems();  // re-scrape each open (handles AJAX updates)
    renderResults('');
    $overlay.addClass('qm-visible');
    setTimeout(function () { $input.val('').focus(); }, 30);
  }

  function close() {
    $overlay.removeClass('qm-visible');
    $input.val('');
    activeIndex = -1;
  }

  function isOpen() {
    return $overlay && $overlay.hasClass('qm-visible');
  }

  /* ============================================================
     EVENT BINDING
     ============================================================ */
  function bindEvents() {
    /* Alt + M — open / close */
    $(document).on('keydown.quickmenu', function (e) {
      if (e.altKey && (e.key === 'm' || e.key === 'M' || e.keyCode === 77)) {
        e.preventDefault();
        isOpen() ? close() : open();
        return;
      }

      if (!isOpen()) return;

      switch (e.key) {
        case 'Escape':
          e.preventDefault();
          close();
          break;
        case 'ArrowDown':
          e.preventDefault();
          moveCursor(1);
          break;
        case 'ArrowUp':
          e.preventDefault();
          moveCursor(-1);
          break;
        case 'Enter':
          e.preventDefault();
          var $active = $results.find('.qm-item.qm-active');
          if ($active.length) activateItem($active);
          break;
      }
    });

    /* Search input */
    $input.on('input.quickmenu', function () {
      renderResults($(this).val());
    });

    /* Click outside modal to close */
    $overlay.on('click.quickmenu', function (e) {
      if ($(e.target).is($overlay)) close();
    });
  }

  /* ============================================================
     INIT
     ============================================================ */
  function init() {
    buildDOM();
    bindEvents();
  }

  /* Run after DOM is ready. Also re-init after PrimeFaces AJAX. */
  $(document).ready(function () {
    init();
  });

  /* Re-attach after any PF AJAX complete (navigation re-renders sidebar) */
  if (typeof PrimeFaces !== 'undefined') {
    $(document).on('pfAjaxComplete', function () {
      // Only rebuild DOM if overlay was removed by AJAX
      if ($('#qmOverlay').length === 0) {
        buildDOM();
        bindEvents();
      }
    });
  }

  /* Expose for programmatic use */
  window.QuickMenu = { open: open, close: close, trackVisit: trackVisit };

})(jQuery);