/**
 * Upload Files dialog -- screen design only (see uploadfilesdialog.xhtml).
 *
 * Everything here is client-side state for the mockup: a running list of
 * File objects picked via drag/drop or the hidden <input type="file">, and
 * which destination card is selected. Nothing is uploaded or sent to a
 * bean -- this only makes the dialog *look and feel* like the target design
 * (file list, live "Add to Pipeline (N)" counter, selectable cards) ahead
 * of the real upload/bean wiring pass.
 */
(function ($) {
  'use strict';

  var pickedFiles = [];

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function renderFileList() {
    var $list = $('#ufdFileList');
    $list.empty();

    if (pickedFiles.length === 0) {
      $list.append('<div class="ufd-empty-state">No files added yet</div>');
    } else {
      pickedFiles.forEach(function (file, idx) {
        var $row = $(
          '<div class="ufd-file-row">' +
            '<i class="pi pi-file"/>' +
            '<span class="ufd-file-name"></span>' +
            '<span class="ufd-file-size" style="color:#8B8A82;font-size:0.78rem;"></span>' +
            '<i class="pi pi-times ufd-file-remove" data-idx="' + idx + '"/>' +
          '</div>'
        );
        $row.find('.ufd-file-name').text(file.name);
        $row.find('.ufd-file-size').text(formatSize(file.size));
        $list.append($row);
      });
    }

    var count = pickedFiles.length;
    $('#ufdAddToPipelineBtn')
      .find('.ui-button-text').text('Add to Pipeline (' + count + ')');
    // p:commandButton renders its label in a .ui-button-text span; fall back
    // to the button's own text node if that selector doesn't match this
    // PrimeFaces version's markup.
    if ($('#ufdAddToPipelineBtn .ui-button-text').length === 0) {
      $('#ufdAddToPipelineBtn').text('Add to Pipeline (' + count + ')');
    }
    $('#ufdAddToPipelineBtn').prop('disabled', count === 0)
      .toggleClass('ui-state-disabled', count === 0);
  }

  function addFiles(fileList) {
    Array.prototype.forEach.call(fileList, function (f) {
      pickedFiles.push(f);
    });
    renderFileList();
  }

  function bindDropzone() {
    var $zone = $('#ufdDropzone');
    var $input = $('#ufdFileInput');

    $zone.off('click.ufd').on('click.ufd', function () {
      $input.trigger('click');
    });

    $input.off('change.ufd').on('change.ufd', function () {
      addFiles(this.files);
      $input.val(''); // allow re-picking the same file name later
    });

    $zone.off('dragover.ufd dragleave.ufd drop.ufd')
      .on('dragover.ufd', function (e) {
        e.preventDefault();
        $zone.addClass('ufd-dropzone-dragover');
      })
      .on('dragleave.ufd', function () {
        $zone.removeClass('ufd-dropzone-dragover');
      })
      .on('drop.ufd', function (e) {
        e.preventDefault();
        $zone.removeClass('ufd-dropzone-dragover');
        var dt = e.originalEvent.dataTransfer;
        if (dt && dt.files && dt.files.length) {
          addFiles(dt.files);
        }
      });

    $('#ufdFileList').off('click.ufd').on('click.ufd', '.ufd-file-remove', function () {
      var idx = parseInt($(this).attr('data-idx'), 10);
      pickedFiles.splice(idx, 1);
      renderFileList();
    });
  }

  function bindDestinationCards() {
    $('.ufd-dest-card').off('click.ufd').on('click.ufd', function () {
      $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
      $(this).addClass('ufd-dest-selected').attr('aria-checked', 'true');
    });
  }

  function resetDialogState() {
    pickedFiles = [];
    renderFileList();
    $('.ufd-dest-card').removeClass('ufd-dest-selected').attr('aria-checked', 'false');
    $('#ufdComments').val('');
  }

  function bind() {
    bindDropzone();
    bindDestinationCards();
  }

  $(document).ready(bind);

  // Exposed so p:dialog's onShow="ufdOnDialogShow()" (see
  // uploadfilesdialog.xhtml) can reset the mockup's client-side state each
  // time the dialog is (re)opened -- e.g. after a prior Cancel.
  window.ufdOnDialogShow = resetDialogState;

})(jQuery);
