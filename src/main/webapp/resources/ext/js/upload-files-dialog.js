/**
 * Upload Files dialog -- client-side glue that isn't handled by
 * p:fileUpload itself (see uploadfilesdialog.xhtml).
 *
 * The dropzone/file-list/upload transport is a real p:fileUpload
 * component (auto="true" -- each drop/selection transmits immediately,
 * which is also what drives its native per-file progress bar right at
 * the moment the user expects to see it), so drag/drop, click-to-browse,
 * and the queued/uploading-file list are all PrimeFaces' own behavior --
 * nothing here drives that. What's left here is: the selectable
 * destination cards, resetting the dialog's own client-side state each
 * time it's reopened, and tracking how many uploads are currently in
 * flight so "Add to Pipeline" can't fire against an incomplete
 * uploadedFiles list (see ufdOnUploadStart/ufdOnUploadSettled below --
 * needed specifically because auto mode decouples the button's click
 * from any single file's own completion, unlike the earlier manual-
 * upload design where they were the same event).
 */
(function ($) {
  'use strict';

  var activeUploadCount = 0;

  function bindDestinationCards() {
    $('.ufd-dest-card').off('click.ufd').on('click.ufd', function () {
      $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
      $(this).addClass('ufd-dest-selected').attr('aria-checked', 'true');
    });
  }

  function updateAddToPipelineDisabled() {
    var $btn = $('#ufdAddToPipelineBtn');
    if (activeUploadCount > 0) {
      $btn.addClass('ui-state-disabled').prop('disabled', true);
    } else {
      $btn.removeClass('ui-state-disabled').prop('disabled', false);
    }
  }

  // A counter, not a plain flag -- two overlapping drops (a second drop
  // starting before the first one's upload has settled) would otherwise
  // let the first one's completion incorrectly re-enable the button while
  // the second is still transferring.
  function onUploadStart() {
    activeUploadCount++;
    updateAddToPipelineDisabled();
  }

  function onUploadSettled() {
    activeUploadCount = Math.max(0, activeUploadCount - 1);
    updateAddToPipelineDisabled();
  }

  function resetDialogState() {
    $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
    $('#ufdComments').val('');

    // In case the dialog is reopened while the counter was somehow left
    // nonzero (e.g. a prior onerror callback that didn't fire for some
    // reason) -- the ajax re-render on open also recreates the
    // p:fileUpload widget fresh, so there's nothing genuinely in flight
    // to preserve here.
    activeUploadCount = 0;
    updateAddToPipelineDisabled();

    // Clear p:fileUpload's own queued/uploaded-file list from a previous
    // open (e.g. after Cancel) so re-opening the dialog doesn't carry
    // over files the user never actually submitted. Guarded since the
    // widget may not be initialized yet on the very first call.
    if (typeof PF !== 'undefined' && PF('ufdFileUploadWidget')) {
      try {
        PF('ufdFileUploadWidget').clear();
      } catch (e) {
        // no-op -- clear() not available on this PrimeFaces version; not
        // worth failing dialog reset over
      }
    }
  }

  function bind() {
    bindDestinationCards();
  }

  $(document).ready(bind);

  // Exposed so p:dialog's onShow="ufdOnDialogShow()" (see
  // uploadfilesdialog.xhtml) can reset this dialog's client-side state
  // each time it's (re)opened.
  window.ufdOnDialogShow = resetDialogState;

  // Exposed for p:fileUpload's onstart/oncomplete/onerror (see
  // uploadfilesdialog.xhtml).
  window.ufdOnUploadStart = onUploadStart;
  window.ufdOnUploadSettled = onUploadSettled;

})(jQuery);
