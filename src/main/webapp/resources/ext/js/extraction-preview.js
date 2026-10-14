/*
 * Extraction review: shows the original document beside the extracted fields (ADDED 2026-10-14).
 * Works on the client only, so the fields panel can refresh (poll, save) without reloading the
 * document. A button passes data-file-upload-id, data-name and data-base, and a page button also
 * passes data-page, to showDocPreview().
 *
 * What the pane shows, by file type:
 *   pdf                      the file itself, opened on the page when one is given
 *   png jpg gif bmp webp     the image
 *   tif tiff                 one PNG per page, made by elcm-ui (a browser cannot show TIFF)
 *   doc docx xls xlsx ppt pptx  the PDF that common-service converts from it; the pane waits for
 *                            the conversion and shows its state meanwhile
 *   anything else            a message and a Download link
 */
(function () {
  'use strict';

  var IMAGE = /\.(png|jpe?g|gif|bmp|webp)$/i;
  var PDF = /\.pdf$/i;
  var TIFF = /\.tiff?$/i;
  var OFFICE = /\.(docx?|xlsx?|pptx?)$/i;
  var POLL_MS = 3000;
  var POLL_MAX = 60;

  var pollTimer = null;

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

  function stopPolling() {
    if (pollTimer) {
      window.clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  function frame(src, title) {
    var node = document.createElement('iframe');
    node.className = 'ex-preview-frame';
    node.title = title;
    node.src = src;
    return node;
  }

  function note(icon, title, text, downloadUrl) {
    var box = document.createElement('div');
    box.className = 'ex-preview-note';
    var head = document.createElement('div');
    head.className = 'ex-preview-note-title';
    head.textContent = title;
    box.appendChild(head);
    if (text) {
      var sub = document.createElement('div');
      sub.textContent = text;
      box.appendChild(sub);
    }
    if (downloadUrl) {
      var link = document.createElement('a');
      link.href = downloadUrl;
      link.textContent = 'Download the original';
      box.appendChild(link);
    }
    box.setAttribute('data-icon', icon);
    return box;
  }

  function show(body, node) {
    body.textContent = '';
    body.appendChild(node);
  }

  function setOpenLink(href) {
    document.getElementById('extractionPreviewOpen').setAttribute('href', href);
  }

  function waitForConversion(body, base, id, name, page, attempt) {
    var statusUrl = base + id + '/conversion-status';
    var download = base + id + '/download';
    var xhr = new XMLHttpRequest();
    xhr.open('GET', statusUrl, true);
    xhr.onload = function () {
      var status = 'NONE';
      try {
        status = JSON.parse(xhr.responseText).status;
      } catch (e) {
        status = 'NONE';
      }
      if (status === 'COMPLETED') {
        var url = base + id + '/converted-preview';
        setOpenLink(url);
        show(body, frame(url + (page > 0 ? '#page=' + page + '&zoom=page-width' : '#zoom=page-width'), name));
      } else if (status === 'PENDING' || status === 'PROCESSING') {
        if (attempt >= POLL_MAX) {
          show(body, note('wait', 'Still converting', 'The preview is taking longer than usual. Close this and try again in a moment, or download the original.', download));
        } else {
          pollTimer = window.setTimeout(function () {
            waitForConversion(body, base, id, name, page, attempt + 1);
          }, POLL_MS);
        }
      } else if (status === 'FAILED') {
        show(body, note('error', 'The preview could not be created', 'The document could not be converted for preview.', download));
      } else {
        show(body, note('none', 'Preview not available', 'This file has no converted preview.', download));
      }
    };
    xhr.onerror = function () {
      show(body, note('error', 'Preview not available', 'The conversion status could not be read.', download));
    };
    xhr.send();
  }

  window.showDocPreview = function (button) {
    var host = split();
    if (!host || !button) {
      return;
    }
    stopPolling();
    var id = button.getAttribute('data-file-upload-id');
    var name = button.getAttribute('data-name') || '';
    var base = button.getAttribute('data-base');
    var page = parseInt(button.getAttribute('data-page'), 10);
    var body = document.getElementById('extractionPreviewBody');
    document.getElementById('extractionPreviewName').textContent = name;
    var original = base + id + '/preview';
    setOpenLink(original);

    if (PDF.test(name)) {
      show(body, frame(original + (page > 0 ? '#page=' + page + '&zoom=page-width' : '#zoom=page-width'), name));
    } else if (IMAGE.test(name)) {
      var img = document.createElement('img');
      img.className = 'ex-preview-image';
      img.alt = name;
      img.src = original;
      show(body, img);
    } else if (TIFF.test(name)) {
      var tiff = base + id + '/tiff-preview';
      setOpenLink(tiff);
      show(body, frame(tiff + (page > 0 ? '#p' + page : ''), name));
    } else if (OFFICE.test(name)) {
      setOpenLink(base + id + '/download');
      show(body, note('wait', 'Preparing the preview', 'The document is converted to PDF first. This usually takes a few seconds.', null));
      waitForConversion(body, base, id, name, page, 0);
    } else {
      setOpenLink(base + id + '/download');
      show(body, note('none', 'Preview not available', 'This file type cannot be shown in the browser.', base + id + '/download'));
    }
    host.classList.add('ex-preview-open');
    widenDialog(true);
  };

  window.hideDocPreview = function () {
    stopPolling();
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
