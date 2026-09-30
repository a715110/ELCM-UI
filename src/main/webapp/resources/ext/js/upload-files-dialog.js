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
 * destination cards (and the fields they reveal), resetting the dialog's
 * own client-side state each time it's reopened, and tracking how many
 * uploads are currently in flight so "Add to Pipeline" can't fire against
 * an incomplete uploadedFiles list (see ufdOnUploadStart/ufdOnUploadSettled
 * below -- needed specifically because auto mode decouples the button's
 * click from any single file's own completion, unlike the earlier manual-
 * upload design where they were the same event).
 *
 * DESTINATION CARDS (REWRITTEN 2026-09-29): each .ufd-dest-card used to be
 * a <label> wrapping a real (visually-hidden) <p:radioButton>, reacting to
 * that radio's native 'change' event. That never actually worked: PrimeFaces'
 * <p:radioButton styleClass="..."> puts the class on the component's own
 * composite wrapper div (native <input> + PrimeFaces' own clickable
 * ".ui-radiobutton-box"), not on the bare <input> -- shrinking that whole
 * wrapper to a 1x1px hidden box (the old CSS trick) also shrank away the
 * box PrimeFaces' widget JS listens on, so clicking a card never actually
 * checked its radio. See uploadfilesdialog.xhtml's DESTINATION CARDS
 * comment for the full diagnosis.
 *
 * Fixed by dropping the PrimeFaces radio components entirely: each card is
 * now a plain <div role="radio" data-dest-value="..."> and this script
 * writes the selection straight into a single <h:inputHidden
 * id="ufdDestinationChoice"> that UploadFilesBean.destinationChoice binds
 * to directly (JSF's implicit enum converter decodes it -- data-dest-value
 * must stay an exact DestinationChoiceEnum constant name). This sidesteps
 * PrimeFaces' composite-widget click-target quirks completely: a hidden
 * input's value is fully this script's own responsibility, with nothing
 * else fighting it for control of the click target.
 *
 * data-dest (short key: "new"/"existing"/"unsure") is kept as a separate
 * attribute from data-dest-value (the real enum constant name) purely so
 * this script's own selectors/data stay readable and decoupled from the
 * enum's exact constant spelling -- applyDestinationSelection()/
 * currentlySelectedDest() below work in terms of data-dest, while only the
 * click handler needs to know about data-dest-value at all.
 */
(function ($) {
  'use strict';

  var activeUploadCount = 0;

  // Absolute id of the hidden field that carries destinationChoice --
  // matches uploadFilesDialogForm:ufdDestinationChoice. Built from the
  // form/field ids rather than hardcoded as one string so a future id
  // change only needs to happen in the two id literals below, not in
  // every jQuery selector that reads/writes this field.
  var DEST_FORM_ID = 'uploadFilesDialogForm';
  var DEST_FIELD_ID = 'ufdDestinationChoice';

  function destFieldSelector() {
    return '#' + DEST_FORM_ID + '\\:' + DEST_FIELD_ID;
  }

  function applyDestinationSelection(dest) {
    if (dest) {
      $('.ufd-dest-card').each(function () {
        var $card = $(this);
        var isSelected = $card.data('dest') === dest;
        $card.toggleClass('ufd-dest-selected', isSelected);
        $card.attr('aria-checked', isSelected ? 'true' : 'false');
        $card.toggle(isSelected);
      });
      $('.ufd-dest-subpanel').each(function () {
        $(this).toggle($(this).data('dest-panel') === dest);
      });
    } else {
      $('.ufd-dest-card').show().removeClass('ufd-dest-selected').attr('aria-checked', 'false');
      $('.ufd-dest-subpanel').hide();
    }
  }

  // Reads the hidden field's current value (the enum constant name, e.g.
  // "NEW_RECORD") and maps it back to the matching card's short data-dest
  // key -- the inverse of the click handler's write below. Returns null
  // when nothing is selected yet (empty string) or the value doesn't match
  // any rendered card.
  function currentlySelectedDest() {
    var value = $(destFieldSelector()).val();
    if (!value) {
      return null;
    }
    var $card = $('.ufd-dest-card[data-dest-value="' + value + '"]');
    return $card.length ? $card.data('dest') : null;
  }

  function selectDestination(destValue, dest) {
    $(destFieldSelector()).val(destValue || '');
    applyDestinationSelection(dest || null);
  }

  function bindDestinationCards() {
    // Delegated on document, NOT bound directly to '.ufd-dest-card'
    // elements -- this whole form is ajax-replaced on every
    // commitToPipeline/resetDialog remote command
    // (update=":uploadFilesDialogForm" in uploadfilesdialog.xhtml), which
    // destroys and recreates these cards and the hidden field. A direct
    // .on() bound once at page load would silently stop firing after the
    // first such re-render; delegation on document survives it since
    // document itself is never replaced.
    $(document).off('click.ufd', '.ufd-dest-card')
    .on('click.ufd', '.ufd-dest-card', function () {
      var $card = $(this);
      selectDestination($card.data('dest-value'), $card.data('dest'));
    });

    // Keyboard support: role="radio" on a plain <div> gets none of the
    // native radio group's arrow-key/Enter/Space handling for free (unlike
    // the real <p:radioButton> this replaced), so Enter/Space activate the
    // focused card the same way a click would.
    $(document).off('keydown.ufd', '.ufd-dest-card')
    .on('keydown.ufd', '.ufd-dest-card', function (e) {
      if (e.key === 'Enter' || e.key === ' ' || e.keyCode === 13 || e.keyCode === 32) {
        e.preventDefault();
        $(this).trigger('click');
      }
    });

    // preventDefault so the bare href="#" doesn't jump/scroll.
    $(document).off('click.ufdChangeSelection', '.ufd-change-selection')
    .on('click.ufdChangeSelection', '.ufd-change-selection', function (e) {
      e.preventDefault();
      selectDestination(null, null);
    });

    // Reflects whatever's actually held in the hidden field right now
    // (e.g. after a failed "Add to Pipeline" submit re-renders this form
    // and PrimeFaces restores the previously-submitted value) rather than
    // always starting collapsed regardless of server state.
    applyDestinationSelection(currentlySelectedDest());
  }

  // Same ajax-replaces-the-form concern as above, for the initial-state
  // sync rather than the event binding: PrimeFaces fires this document-
  // level event after every ajax request completes, regardless of which
  // remote command triggered it, so this re-applies the correct
  // card/subpanel visibility against whatever the re-rendered markup's
  // hidden field actually holds (e.g. after a failed submit's re-render).
  $(document).on('pfAjaxComplete', function () {
    applyDestinationSelection(currentlySelectedDest());
  });

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
    selectDestination(null, null);
    $('#ufdComments').val('');
    $('#ufdNewRecordName, #ufdNewCounterparty, #ufdNewPropertyAddress, #ufdExistingRecordSearch').val('');

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