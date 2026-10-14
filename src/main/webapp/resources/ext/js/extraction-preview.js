/*
 * Extraction review: shows the original document beside the extracted fields (ADDED 2026-10-14).
 * Works on the client only, so the fields panel can refresh (poll, save) without reloading the
 * document. A View document button passes its data-file-upload-id and data-name here.
 */
(function () {
  'use strict';

  var IMAGE = /\.(png|jpe?g|gif|bmp|webp)$/i;

  function split() {
    return document.getElementById('extractionSplit');
  }

  function widenDialog(wide) {
    try {
      var dialog = PF('extractionDialog');
      if (dialog && dialog.jq) {
        dialog.jq.css('width', wide ? '96vw' : '');
        dialog.initPosition && dialog.initPosition();
      }
    } catch (e) {
      /* the dialog widget is not ready; the preview still works at the normal width */
    }
  }

  window.showDocPreview = function (button) {
    var host = split();
    if (!host || !button) {
      return;
    }
    var id = button.getAttribute('data-file-upload-id');
    var name = button.getAttribute('data-name') || '';
    var url = button.getAttribute('data-base') + id + '/preview';
    var body = document.getElementById('extractionPreviewBody');
    document.getElementById('extractionPreviewName').textContent = name;
    document.getElementById('extractionPreviewOpen').setAttribute('href', url);
    body.textContent = '';
    var node;
    if (IMAGE.test(name)) {
      node = document.createElement('img');
      node.className = 'ex-preview-image';
      node.alt = name;
    } else {
      node = document.createElement('iframe');
      node.className = 'ex-preview-frame';
      node.title = name;
    }
    node.src = IMAGE.test(name) ? url : url + '#zoom=page-width';
    body.appendChild(node);
    host.classList.add('ex-preview-open');
    widenDialog(true);
  };

  window.hideDocPreview = function () {
    var host = split();
    if (!host) {
      return;
    }
    host.classList.remove('ex-preview-open');
    var body = document.getElementById('extractionPreviewBody');
    if (body) {
      body.textContent = '';
    }
    widenDialog(false);
  };
})();
