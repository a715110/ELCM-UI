// Simple mode drag and drop implementation
// CORRECTED: Fixed tab detection to find the actual <li> header element
var dragDropInitialized = false;
var isUploading = false;
window.__uploadMetaQueue = window.__uploadMetaQueue || [];

// ===== OFFICE THUMBNAIL POLLING =====
const OFFICE_EXTENSIONS = /\.(docx?|xlsx?|pptx?|odt|ods|odp|rtf)$/i;
const THUMBNAIL_POLL_INTERVAL = 3000;  // 3 seconds
const THUMBNAIL_MAX_ATTEMPTS = 120;    // Stop after 6 minutes

// Track active polls
window.__thumbnailPolls = window.__thumbnailPolls || {};
let placeholderThumbName = ""; //added by BK

function scheduleOfficeThumbnailRefresh(attachmentId, thumbnailId, fileName) {
  if (!OFFICE_EXTENSIONS.test(fileName)) {
    return; // Not an Office file
  }

  // Derive real thumbnail name from placeholder name
  // placeholder_thumb_xxx.jpg → thumb_xxx.jpg
  const realThumbName = placeholderThumbName.replace(/^placeholder_/, '');

  console.log('[ThumbnailRefresh] Scheduling refresh for:', fileName);
  console.log('[ThumbnailRefresh] Placeholder:', placeholderThumbName);
  console.log('[ThumbnailRefresh] Real thumb:', realThumbName);

  // Clear any existing poll
  stopThumbnailPolling(attachmentId);

  // Create poll state
  const pollState = {
    attachmentId: attachmentId,
    placeholderThumbName: placeholderThumbName,
    realThumbName: realThumbName,
    fileName: fileName,
    attempts: 0,
    intervalId: null,
    stopped: false
  };

  window.__thumbnailPolls[attachmentId] = pollState;

  // Start polling
  pollState.intervalId = setInterval(() => {
    checkForRealThumbnail(attachmentId);
  }, THUMBNAIL_POLL_INTERVAL);

  console.log('[ThumbnailRefresh] Polling started');
}

function stopThumbnailPolling(attachmentId) {
  const pollState = window.__thumbnailPolls[attachmentId];
  if (pollState) {
    console.log('[ThumbnailRefresh] Stopping poll for:', attachmentId);
    pollState.stopped = true;
    if (pollState.intervalId) {
      clearInterval(pollState.intervalId);
    }
    delete window.__thumbnailPolls[attachmentId];

    // Remove loading shimmer
    const img = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
    if (img) {
      img.classList.remove('thumbnail-loading');
    }
  }
}

function checkForRealThumbnail(attachmentId) {
  const pollState = window.__thumbnailPolls[attachmentId];

  if (!pollState || pollState.stopped) {
    return;
  }

  pollState.attempts++;
  console.log('[ThumbnailRefresh] Attempt', pollState.attempts, '- checking for:', pollState.realThumbName);

  // Max attempts reached?
  if (pollState.attempts > THUMBNAIL_MAX_ATTEMPTS) {
    console.log('[ThumbnailRefresh] Max attempts reached, giving up');
    stopThumbnailPolling(attachmentId);
    return;
  }

  // Check if image element still exists
  const img = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
  if (!img) {
    console.log('[ThumbnailRefresh] Image element gone, stopping');
    stopThumbnailPolling(attachmentId);
    return;
  }

  // Already showing real thumbnail?
  if (!img.classList.contains('thumbnail-loading')) {
    console.log('[ThumbnailRefresh] Already loaded, stopping');
    stopThumbnailPolling(attachmentId);
    return;
  }

  // Build URL for real thumbnail (not placeholder)
  const realThumbUrl = `/api/files/thumbnail?path=${encodeURIComponent(pollState.realThumbName)}&t=${Date.now()}`;

  // Try to load the real thumbnail
  const testImg = new Image();

  testImg.onload = function() {
    // Real thumbnail exists! Check if poll is still active
    const currentPollState = window.__thumbnailPolls[attachmentId];
    if (!currentPollState || currentPollState.stopped) {
      return;
    }

    console.log('[ThumbnailRefresh] ✓ Real thumbnail found!');

    // Get fresh reference to image element
    const imgElement = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
    if (imgElement) {
      // Swap to real thumbnail
      imgElement.src = realThumbUrl;
      imgElement.classList.remove('thumbnail-loading');
      console.log('[ThumbnailRefresh] ✓ Swapped to real thumbnail');
    }

    // Stop polling
    stopThumbnailPolling(attachmentId);
  };

  testImg.onerror = function() {
    // Real thumbnail doesn't exist yet, keep polling
    console.log('[ThumbnailRefresh] Real thumbnail not ready, will retry in 15s');
  };

  testImg.src = realThumbUrl;
}
// ===== END OFFICE THUMBNAIL POLLING =====

function initializeDragAndDrop() {
  if (dragDropInitialized) {
    console.log('Drag and drop already initialized');
    return;
  }
  console.log('Initializing drag and drop for simple mode...');
  setTimeout(function() {
    const editorContainer = document.getElementById('collaborationTaskTabView:comment') ||
        document.querySelector('.ql-container') ||
        document.querySelector('[id$="comment"]')?.closest('div');
    const dropOverlay = document.getElementById('dropOverlay');
    if (!editorContainer) {
      console.warn('Editor container not found, retrying...');
      setTimeout(initializeDragAndDrop, 500);
      return;
    }
    console.log('Found editor container for drag and drop');
    disableQuillImageHandling(editorContainer);
    let dragCounter = 0;
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
      editorContainer.addEventListener(eventName, function(e) {
        e.preventDefault();
        e.stopPropagation();
      }, false);
    });
    ['dragenter', 'dragover'].forEach(eventName => {
      editorContainer.addEventListener(eventName, function() {
        dragCounter++;
        if (dropOverlay) {
          dropOverlay.style.display = 'flex';
          dropOverlay.classList.add('active');
        }
      }, false);
    });
    editorContainer.addEventListener('dragleave', function() {
      dragCounter--;
      if (dragCounter === 0 && dropOverlay) {
        dropOverlay.style.display = 'none';
        dropOverlay.classList.remove('active');
      }
    }, false);
    editorContainer.addEventListener('drop', function(e) {
      console.log('Files dropped!');
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      dragCounter = 0;
      if (dropOverlay) {
        dropOverlay.style.display = 'none';
        dropOverlay.classList.remove('active');
      }
      const files = e.dataTransfer.files;
      if (files.length > 0) handleDroppedFiles(files);
      return false;
    }, true);
    dragDropInitialized = true;
    console.log('Drag and drop initialized successfully');
  }, 100);
}

function disableQuillImageHandling(editorContainer) {
  const quillEditor = editorContainer.querySelector('.ql-editor');
  if (!quillEditor) {
    console.warn('Quill editor element not found');
    return;
  }
  console.log('Disabling Quill image handling');
  quillEditor.addEventListener('drop', function(e) {
    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      for (let i = 0; i < files.length; i++) {
        if (files[i].type.startsWith('image/')) {
          console.log('Blocking image drop into Quill editor');
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          return false;
        }
      }
    }
  }, true);
  quillEditor.addEventListener('paste', function(e) {
    const clipboardData = e.clipboardData || window.clipboardData;
    const items = clipboardData ? clipboardData.items : null;
    if (items) {
      for (let i = 0; i < items.length; i++) {
        if (items[i].type.indexOf('image') !== -1) {
          console.log('Blocking image paste');
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          return false;
        }
      }
    }
  }, true);
  quillEditor.addEventListener('input', function(e) {
    const images = quillEditor.querySelectorAll('img');
    if (images.length > 0) {
      console.log('Removing', images.length, 'image(s)');
      images.forEach(img => img.remove());
    }
  }, false);
  console.log('Quill image handling disabled');
}

function handleDroppedFiles(files) {
  console.log('Processing', files.length, 'dropped files');
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    if (file.size > 15728640) {
      alert('File "' + file.name + '" is too large. Maximum size is 15MB.');
      continue;
    }
    console.log('Processing file:', file.name);
    uploadFileInSimpleMode(file);
    break;
  }
}

function uploadFileInSimpleMode(file) {
  if (isUploading) {
    console.log('Upload already in progress');
    return;
  }
  try {
    isUploading = true;
    console.log('Starting simple mode upload for:', file.name);
    const fileInput = document.querySelector('[id$="fileUpload_input"]') ||
        document.querySelector('#fileUpload_input') ||
        document.querySelector('input[type="file"]');
    if (!fileInput) {
      console.error('File input not found');
      alert('Upload component not available');
      isUploading = false;
      return;
    }
    console.log('Found file input:', fileInput.id || fileInput.name);
    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(file);
    fileInput.files = dataTransfer.files;
    console.log('Files set on input, file count:', fileInput.files.length);
    window.currentUploadingFile = { name: file.name, size: file.size, type: file.type };
    const changeEvent = new Event('change', { bubbles: true, cancelable: true });
    console.log('Triggering change event...');
    fileInput.dispatchEvent(changeEvent);
    setTimeout(function() {
      const inputEvent = new Event('input', { bubbles: true, cancelable: true });
      fileInput.dispatchEvent(inputEvent);
    }, 50);
    console.log('Upload events triggered');
  } catch (error) {
    console.error('Error in simple mode upload:', error);
    alert('Error uploading file: ' + file.name);
    isUploading = false;
  }
}

function handleUploadComplete(a, b, c) {
  console.log('=== Upload Complete (main) ===');
  let xhr, status, args;
  if (a && typeof a === 'object' && ('xhr' in a || 'status' in a || 'args' in a)) {
    ({ xhr, status, args } = a);
  } else {
    xhr = a; status = b; args = c;
  }
  isUploading = false;
  let meta = null;
  if (Array.isArray(window.__uploadMetaQueue) && window.__uploadMetaQueue.length > 0) {
    meta = window.__uploadMetaQueue.shift();
  }
  if (!meta && args) {
    meta = {
      name: args.originalName, size: args.size, type: args.contentType,
      uniqueName: args.uniqueName,
      thumbnailFileName: args.thumbnailFileName || args.thumbnailPath
    };
  }
  const cf = window.currentUploadingFile || {};
  if (!meta || (!meta.uniqueName && !meta.name)) {
    meta = { name: cf.name || 'file', size: cf.size || 0, type: cf.type || 'application/octet-stream' };
  }
  console.log('Final meta used for UI:', meta);
  addFileToAttachmentsDisplay(meta);
  window.currentUploadingFile = null;
  const fileInput = document.querySelector('[id$="fileUpload_input"]') || document.querySelector('input[type="file"]');
  if (fileInput) fileInput.value = '';
}

function addFileToAttachmentsDisplay(fileInfo) {
  console.log('Adding file to display:', fileInfo);
  const attachmentsContainer = document.getElementById('attachmentsContainer') ||
      document.querySelector('[id$=":attachmentsContainer"]') ||
      document.querySelector('[id*="attachmentsContainer"]');
  const attachmentsList = document.getElementById('attachmentsList') ||
      document.querySelector('[id$=":attachmentsList"]') ||
      document.querySelector('[id*="attachmentsList"]');
  if (!attachmentsList) {
    console.error('Cannot find attachments list!');
    return;
  }
  attachmentsContainer.classList.add('has-files');
  attachmentsContainer.style.display = 'block';
  const displayName = fileInfo.name || fileInfo.originalName || 'file';
  const displaySize = typeof fileInfo.size === 'number' ? fileInfo.size : 0;
  const displayType = fileInfo.type || 'application/octet-stream';
  const thumbName = fileInfo.thumbnailFileName || fileInfo.thumbnailPath;
  const hasThumbnail = !!thumbName && !String(thumbName).startsWith('TEXT_PREVIEW:');
  const attachmentId = 'attachment_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);

  // Check if this is an Office file (needs async thumbnail refresh)
  const isOfficeFile = OFFICE_EXTENSIONS.test(displayName);

  // Check if this is a placeholder thumbnail (Office files)
  const isPlaceholder = thumbName && thumbName.startsWith('placeholder_');
  //added by BK
  if(isPlaceholder) {
    placeholderThumbName = thumbName;
  }

  const cacheBuster = hasThumbnail ? '&t=' + Date.now() : '';
  const hrefOpen = fileInfo.uniqueName ? `<a href="/api/files/download?file=${encodeURIComponent(fileInfo.uniqueName)}" class="attachment-link" download>` : '<div class="attachment-link">';
  const hrefClose = fileInfo.uniqueName ? '</a>' : '</div>';
  const iconHtml = getFileIcon(displayType, displayName);
  const thumbSrc = hasThumbnail ? `/api/files/thumbnail?path=${encodeURIComponent(thumbName)}${cacheBuster}` : '';

  // Add loading class for Office files with placeholder thumbnails
  const thumbnailClass = (isOfficeFile && isPlaceholder) ? 'attachment-thumbnail thumbnail-loading' : 'attachment-thumbnail';

  const html = `${hrefOpen}<div class="attachment-preview">${hasThumbnail ? `<img src="${thumbSrc}" alt="thumbnail" class="${thumbnailClass}"/>` : `<div class="attachment-icon">${iconHtml}</div>`}</div><div class="attachment-info"><div class="attachment-name">${escapeHtml(displayName)}</div><div class="attachment-size">${formatFileSize(displaySize)}</div></div>${hrefClose}<button class="attachment-remove" onclick="removeAttachment('${attachmentId}', '${escapeHtml(displayName)}')" title="Remove file"><i class="pi pi-times"></i></button>`;
  const attachmentItem = document.createElement('div');
  attachmentItem.className = 'attachment-item';
  attachmentItem.id = attachmentId;
  attachmentItem.innerHTML = html;

  attachmentItem.dataset.name = displayName;
  attachmentItem.dataset.size = String(displaySize);
  attachmentItem.dataset.type = displayType;
  attachmentItem.dataset.uniqueName = fileInfo.uniqueName || '';
  attachmentItem.dataset.thumbnailFileName = thumbName || '';

  attachmentsList.appendChild(attachmentItem);
  updateAttachmentsInfo();

  // Schedule thumbnail refresh for Office files with placeholder
  if (isOfficeFile && isPlaceholder && hasThumbnail) {
    scheduleOfficeThumbnailRefresh(attachmentId, thumbName, displayName);
  }
}

function updateAttachmentsInfo() {
  const attachmentsList = document.getElementById('attachmentsList');
  const attachmentsCount = document.getElementById('attachmentsCount');
  const fileCount = document.getElementById('fileCount');
  const totalSize = document.getElementById('totalSize');
  if (!attachmentsList) return;
  const attachments = attachmentsList.children;
  const count = attachments.length;
  if (attachmentsCount) attachmentsCount.textContent = count;
  let totalBytes = 0;
  for (let i = 0; i < attachments.length; i++) {
    const sizeText = attachments[i].querySelector('.attachment-size');
    if (sizeText) totalBytes += parseSizeToBytes(sizeText.textContent);
  }
  if (fileCount) fileCount.textContent = count + ' file' + (count !== 1 ? 's' : '') + ' attached';
  if (totalSize) totalSize.textContent = formatFileSize(totalBytes);
}

function getFileIcon(contentType, fileName) {
  const ext = (fileName || '').toLowerCase().split('.').pop();
  if (contentType && contentType.startsWith('image/')) return '<i class="pi pi-image"></i>';
  if (contentType && contentType.startsWith('video/')) return '<i class="pi pi-video"></i>';
  if (contentType && contentType.startsWith('audio/')) return '<i class="pi pi-volume-up"></i>';
  if (contentType && contentType.includes('pdf')) return '<i class="pi pi-file-pdf"></i>';
  if (contentType && (contentType.includes('word') || ext === 'docx' || ext === 'doc')) return '<i class="pi pi-file-word"></i>';
  if (contentType && (contentType.includes('excel') || ext === 'xlsx' || ext === 'xls')) return '<i class="pi pi-file-excel"></i>';
  if (ext === 'zip' || ext === 'rar' || ext === '7z') return '<i class="pi pi-file-archive"></i>';
  return '<i class="pi pi-file"></i>';
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text ?? '';
  return div.innerHTML;
}

function parseSizeToBytes(sizeStr) {
  const units = { 'B': 1, 'KB': 1024, 'MB': 1024*1024, 'GB': 1024*1024*1024 };
  const match = (sizeStr || '').match(/^([\d.]+)\s*(\w+)$/);
  if (match) {
    const size = parseFloat(match[1]);
    const unit = match[2];
    return size * (units[unit] || 1);
  }
  return 0;
}

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function removeAttachment(attachmentId, fileName) {
  // Stop any active polling
  stopThumbnailPolling(attachmentId);
  const attachmentItem = document.getElementById(attachmentId);
  if (attachmentItem) attachmentItem.remove();
  updateAttachmentsInfo();
  const attachmentsList = document.getElementById('attachmentsList');
  const attachmentsContainer = document.getElementById('attachmentsContainer');
  if (attachmentsList && attachmentsList.children.length === 0) {
    if (attachmentsContainer) {
      attachmentsContainer.classList.remove('has-files');
      attachmentsContainer.style.display = 'none';
    }
  }
  prepareCommentForSubmission();
}

function clearAttachmentUI() {
  // Stop all active polls
  Object.keys(window.__thumbnailPolls || {}).forEach(id => stopThumbnailPolling(id));

  window.__uploadMetaQueue = [];
  const attachmentsContainer = document.getElementById('attachmentsContainer');
  if (attachmentsContainer) attachmentsContainer.style.display = 'none';
  const attachmentsList = document.getElementById('attachmentsList');
  if (attachmentsList) attachmentsList.innerHTML = '';
  const fileCount = document.getElementById('fileCount');
  const totalSize = document.getElementById('totalSize');
  if (fileCount) fileCount.textContent = '';
  if (totalSize) totalSize.textContent = '';
  const attachmentDataField = document.querySelector('[id$="attachmentDataField"]');
  if (attachmentDataField) attachmentDataField.value = '';
}

// ===== CORRECTED TAB DETECTION =====
function handleTabChange(event) {
  console.log('=== Tab change detected ===');
  setTimeout(function() {
    // CORRECTED: Find the <li> tab header, not just any element
    // PrimeFaces structure: <li class="ui-tabs-header ui-state-active"><a id="...commentTab...">
    let commentTabHeader = null;

    // Method 1: Find link with "commentTab" in ID, get parent <li>
    const commentLink = document.querySelector('a[id*="commentTab"]');
    if (commentLink) {
      commentTabHeader = commentLink.closest('li.ui-tabs-header');
    }

    // Method 2: Find tab header by text content
    if (!commentTabHeader) {
      const allHeaders = document.querySelectorAll('li.ui-tabs-header');
      for (let header of allHeaders) {
        if (header.textContent.includes('Comment')) {
          commentTabHeader = header;
          break;
        }
      }
    }

    console.log('[TAB] Comment header found:', !!commentTabHeader);

    if (commentTabHeader) {
      const isActive = commentTabHeader.classList.contains('ui-state-active');
      console.log('[TAB] Header classes:', commentTabHeader.className);
      console.log('[TAB] Is active?', isActive);

      if (isActive) {
        console.log('[TAB] Comment tab IS active - reinitializing');
        dragDropInitialized = false;
        initializeDragAndDrop();
      } else {
        console.log('[TAB] Comment tab not active');
      }
    } else {
      console.error('[TAB] Could not find Comment tab header!');
    }
  }, 300);
}

// Clear the comment editor (Quill)
function clearComment() {
  try {
    const quill = PF('commentWgVar')?.jq?.data('quill');
    if (quill) {
      quill.setText('');  // Clear the editor content
      console.log('Comment editor cleared');
    } else {
      console.warn('Could not find Quill editor to clear');
    }
  } catch (error) {
    console.error('Error clearing comment:', error);
  }
}

function prepareCommentForSubmission() {
  const attachmentsList = document.getElementById('attachmentsList')
      || document.querySelector('[id$=":attachmentsList"]')
      || document.querySelector('[id*="attachmentsList"]');

  const field = document.querySelector('[id$="attachmentDataField"]');

  if (!field) {
    console.warn('attachmentDataField not found; attachmentData will be empty on submit.');
    return;
  }

  const items = attachmentsList ? attachmentsList.querySelectorAll('.attachment-item') : [];
  const meta = Array.from(items).map(item => ({
    name: item.dataset.name || '',
    size: Number(item.dataset.size || 0),
    type: item.dataset.type || '',
    uniqueName: item.dataset.uniqueName || '',
    thumbnailFileName: item.dataset.thumbnailFileName || ''
  }));

  field.value = JSON.stringify(meta);
  field.dispatchEvent(new Event('input', { bubbles: true }));
  field.dispatchEvent(new Event('change', { bubbles: true }));
}
window.prepareCommentForSubmission = prepareCommentForSubmission;
window.handleTabChange = handleTabChange;

document.addEventListener('DOMContentLoaded', function() {
  console.log('DOM loaded, initializing drag and drop...');
  initializeDragAndDrop();
});

if (typeof jsf !== 'undefined') {
  jsf.ajax.addOnEvent(function(data) {
    if (data.status === 'success' && !dragDropInitialized) {
      console.log('JSF AJAX success, initializing drag and drop...');
      setTimeout(initializeDragAndDrop, 200);
    }
  });
}