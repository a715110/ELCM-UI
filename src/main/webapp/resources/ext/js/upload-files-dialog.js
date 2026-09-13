/**
 * Upload Files dialog -- client-side glue that isn't handled by
 * p:fileUpload itself (see uploadfilesdialog.xhtml).
 *
 * The dropzone/file-list/upload transport is now a real p:fileUpload
 * component, so drag/drop, click-to-browse, and the queued-file list are
 * all PrimeFaces' own behavior -- nothing here drives that anymore (an
 * earlier version of this file did, via a hand-rolled <input type="file">;
 * that's gone now that the transport is real). What's left here is purely
 * cosmetic/local UI state PrimeFaces doesn't provide out of the box:
 * the selectable destination cards, and resetting the dialog's own state
 * each time it's reopened.
 */
(function ($) {
  'use strict';

  function bindDestinationCards() {
    $('.ufd-dest-card').off('click.ufd').on('click.ufd', function () {
      $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
      $(this).addClass('ufd-dest-selected').attr('aria-checked', 'true');
    });
  }

  function resetDialogState() {
    $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
    $('#ufdComments').val('');

    // Clear p:fileUpload's own queued-file list from a previous open (e.g.
    // after Cancel) so re-opening the dialog doesn't carry over files the
    // user never actually submitted. Guarded since the widget may not be
    // initialized yet on the very first call.
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

})(jQuery);
