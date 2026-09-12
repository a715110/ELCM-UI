/**
 * Task Rail — tap/touch fallback for the hover-expand ECWS task panel.
 *
 * Desktop expand/collapse is handled entirely by CSS (:hover on #taskRail,
 * see task-rail.css). This script only covers what CSS :hover cannot:
 *   - touch devices, where there is no hover state, so a tap on the handle
 *     toggles a `.task-rail-open` class instead
 *   - closing the panel when the user taps/clicks elsewhere on the page
 *   - re-binding after PrimeFaces AJAX updates (the pin toggle re-renders
 *     the form via update="taskRailForm")
 */
(function ($) {
  'use strict';

  function isTouchViewport() {
    return window.matchMedia('(max-width: 768px)').matches;
  }

  function bind() {
    var $rail = $('#taskRail');
    if (!$rail.length) return;

    var $handle = $rail.find('.task-rail-handle');

    $handle.off('click.taskrail').on('click.taskrail', function (e) {
      if (!isTouchViewport()) return; // desktop relies on :hover
      e.stopPropagation();
      $rail.toggleClass('task-rail-open');
    });

    $(document).off('click.taskrail-outside').on('click.taskrail-outside', function (e) {
      if (!$rail.hasClass('task-rail-open')) return;
      if ($(e.target).closest('#taskRail').length) return;
      $rail.removeClass('task-rail-open');
    });
  }

  $(document).ready(bind);

  // Re-bind after the pin-toggle AJAX re-render (form id = taskRailForm)
  if (typeof PrimeFaces !== 'undefined') {
    $(document).on('pfAjaxComplete', function (e, xhr, settings) {
      if (settings && settings.source && $(settings.source).closest('#taskRail').length) {
        bind();
      }
    });
  }

})(jQuery);
