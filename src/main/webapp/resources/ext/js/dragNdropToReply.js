// Reply dialog drag and drop implementation - mirrors main comment functionality
// WITH OFFICE THUMBNAIL ASYNC REFRESH
var replyDragDropInitialized = false;
var isReplyUploading = false;

// ===== OFFICE THUMBNAIL POLLING FOR REPLIES =====
const REPLY_OFFICE_EXTENSIONS = /\.(docx?|xlsx?|pptx?|odt|ods|odp|rtf)$/i;
const REPLY_THUMBNAIL_POLL_INTERVAL = 3000; // 3 seconds
const REPLY_THUMBNAIL_MAX_ATTEMPTS = 120;    // Stop after 6 minutes

// Track active polls
window.__replyThumbnailPolls = window.__replyThumbnailPolls || {};
let replyPlaceholderThumbName = ""; // Track placeholder for reply

function scheduleReplyThumbnailRefresh(attachmentId, thumbnailId, fileName) {
    if (!REPLY_OFFICE_EXTENSIONS.test(fileName)) {
        return; // Not an Office file
    }

    // Derive real thumbnail name from placeholder name
    // placeholder_thumb_xxx.jpg → thumb_xxx.jpg
    const realThumbName = replyPlaceholderThumbName.replace(/^placeholder_/, '');

    console.log('[ReplyThumbnailRefresh] Scheduling refresh for:', fileName);
    console.log('[ReplyThumbnailRefresh] Placeholder:', replyPlaceholderThumbName);
    console.log('[ReplyThumbnailRefresh] Real thumb:', realThumbName);

    // Clear any existing poll
    stopReplyThumbnailPolling(attachmentId);

    // Create poll state
    const pollState = {
        attachmentId: attachmentId,
        placeholderThumbName: replyPlaceholderThumbName,
        realThumbName: realThumbName,
        fileName: fileName,
        attempts: 0,
        intervalId: null,
        stopped: false
    };

    window.__replyThumbnailPolls[attachmentId] = pollState;

    // Start polling
    pollState.intervalId = setInterval(() => {
        checkForReplyRealThumbnail(attachmentId);
    }, REPLY_THUMBNAIL_POLL_INTERVAL);

    console.log('[ReplyThumbnailRefresh] Polling started');
}

function stopReplyThumbnailPolling(attachmentId) {
    const pollState = window.__replyThumbnailPolls[attachmentId];
    if (pollState) {
        console.log('[ReplyThumbnailRefresh] Stopping poll for:', attachmentId);
        pollState.stopped = true;
        if (pollState.intervalId) {
            clearInterval(pollState.intervalId);
        }
        delete window.__replyThumbnailPolls[attachmentId];

        // Remove loading shimmer
        const img = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
        if (img) {
            img.classList.remove('thumbnail-loading');
        }
    }
}

function checkForReplyRealThumbnail(attachmentId) {
    const pollState = window.__replyThumbnailPolls[attachmentId];

    if (!pollState || pollState.stopped) {
        return;
    }

    pollState.attempts++;
    console.log('[ReplyThumbnailRefresh] Attempt', pollState.attempts, '- checking for:', pollState.realThumbName);

    // Max attempts reached?
    if (pollState.attempts > REPLY_THUMBNAIL_MAX_ATTEMPTS) {
        console.log('[ReplyThumbnailRefresh] Max attempts reached, giving up');
        stopReplyThumbnailPolling(attachmentId);
        return;
    }

    // Check if image element still exists
    const img = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
    if (!img) {
        console.log('[ReplyThumbnailRefresh] Image element gone, stopping');
        stopReplyThumbnailPolling(attachmentId);
        return;
    }

    // Already showing real thumbnail?
    if (!img.classList.contains('thumbnail-loading')) {
        console.log('[ReplyThumbnailRefresh] Already loaded, stopping');
        stopReplyThumbnailPolling(attachmentId);
        return;
    }

    // Build URL for real thumbnail (not placeholder)
    const realThumbUrl = `/api/files/thumbnail?path=${encodeURIComponent(pollState.realThumbName)}&t=${Date.now()}`;

    // Try to load the real thumbnail
    const testImg = new Image();

    testImg.onload = function() {
        // Real thumbnail exists! Check if poll is still active
        const currentPollState = window.__replyThumbnailPolls[attachmentId];
        if (!currentPollState || currentPollState.stopped) {
            return;
        }

        console.log('[ReplyThumbnailRefresh] ✓ Real thumbnail found!');

        // Get fresh reference to image element
        const imgElement = document.querySelector(`#${attachmentId} .attachment-thumbnail`);
        if (imgElement) {
            // Swap to real thumbnail
            imgElement.src = realThumbUrl;
            imgElement.classList.remove('thumbnail-loading');
            console.log('[ReplyThumbnailRefresh] ✓ Swapped to real thumbnail');
        }

        // Stop polling
        stopReplyThumbnailPolling(attachmentId);
    };

    testImg.onerror = function() {
        // Real thumbnail doesn't exist yet, keep polling
        console.log('[ReplyThumbnailRefresh] Real thumbnail not ready, will retry in 6s');
    };

    testImg.src = realThumbUrl;
}
// ===== END OFFICE THUMBNAIL POLLING FOR REPLIES =====

function initializeReplyDialogDragDrop() {
    if (replyDragDropInitialized) {
        console.log('Reply drag and drop already initialized');
        return;
    }

    console.log('Initializing drag and drop for reply dialog...');

    setTimeout(function() {
        // Find the reply editor container - same structure as main comment
        const replyEditorContainer = document.getElementById('replyCommentContainer');
        const replyDropOverlay = document.getElementById('replyDropOverlay');

        if (!replyEditorContainer) {
            console.warn('Reply editor container not found, retrying...');
            setTimeout(initializeReplyDialogDragDrop, 500);
            return;
        }

        console.log('Found reply editor container for drag and drop');

        // === NEW: Block Quill's built-in image handling ===
        disableReplyQuillImageHandling(replyEditorContainer);

        let dragCounter = 0;

        // Prevent default behaviors - same as main comment
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            replyEditorContainer.addEventListener(eventName, function(e) {
                e.preventDefault();
                e.stopPropagation();
            }, false);
        });

        // Show overlay on drag enter/over - same as main comment
        ['dragenter', 'dragover'].forEach(eventName => {
            replyEditorContainer.addEventListener(eventName, function(e) {
                dragCounter++;
                if (replyDropOverlay) {
                    replyDropOverlay.style.display = 'flex';
                    replyDropOverlay.classList.add('active');
                }
            }, false);
        });

        // Hide overlay on drag leave - same as main comment
        replyEditorContainer.addEventListener('dragleave', function(e) {
            dragCounter--;
            if (dragCounter === 0 && replyDropOverlay) {
                replyDropOverlay.style.display = 'none';
                replyDropOverlay.classList.remove('active');
            }
        }, false);

        // Handle file drop - same as main comment
        replyEditorContainer.addEventListener('drop', function(e) {
            console.log('Files dropped in reply dialog!');

            // Prevent Quill from handling this - be aggressive
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();

            dragCounter = 0;

            if (replyDropOverlay) {
                replyDropOverlay.style.display = 'none';
                replyDropOverlay.classList.remove('active');
            }

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                handleReplyDroppedFiles(files);
            }
            return false;
        }, true);

        replyDragDropInitialized = true;
        console.log('Reply drag and drop initialized successfully');
    }, 100);
}

/**
 * Disable Quill's built-in image drop/paste handling for reply
 */
function disableReplyQuillImageHandling(editorContainer) {
    // Find the Quill editor instance
    const quillEditor = editorContainer.querySelector('.ql-editor');

    if (!quillEditor) {
        console.warn('Quill editor element not found in reply container');
        return;
    }

    console.log('Disabling Quill image handling for reply:', quillEditor);

    // Block drag and drop of images into Quill - USE CAPTURE PHASE
    quillEditor.addEventListener('drop', function(e) {
        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            // Check if any file is an image
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
    }, true); // Use capture phase to intercept before Quill

    // Block paste of images into Quill - USE CAPTURE PHASE
    quillEditor.addEventListener('paste', function(e) {
        const clipboardData = e.clipboardData || window.clipboardData;
        const items = clipboardData ? clipboardData.items : null;

        if (items) {
            for (let i = 0; i < items.length; i++) {
                if (items[i].type.indexOf('image') !== -1) {
                    console.log('Blocking image paste into Quill editor');
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    return false;
                }
            }
        }
    }, true); // Use capture phase

    // Also block input of images via other means
    quillEditor.addEventListener('input', function(e) {
        // Check if any img tags were added
        const images = quillEditor.querySelectorAll('img');
        if (images.length > 0) {
            console.log('Removing', images.length, 'image(s) from Quill editor');
            images.forEach(img => img.remove());
        }
    }, false);

    console.log('Quill image handling disabled successfully for reply');
}

function handleReplyDroppedFiles(files) {
    console.log('Processing', files.length, 'dropped files in reply');

    // Process one file at a time (same as main comment)
    for (let i = 0; i < files.length; i++) {
        const file = files[i];

        // Basic validation - same as main comment
        if (file.size > 15728640) { // 15MB
            alert('File "' + file.name + '" is too large. Maximum size is 15MB.');
            continue;
        }

        console.log('Processing reply file:', file.name);
        uploadReplyFileInSimpleMode(file);
        break; // Only handle one file at a time
    }
}

function uploadReplyFileInSimpleMode(file) {
    if (isReplyUploading) {
        console.log('Reply upload already in progress');
        return;
    }

    try {
        isReplyUploading = true;
        console.log('Starting reply simple mode upload for:', file.name);

        // Find the reply file input - same pattern as main comment
        const replyFileInput = document.querySelector('[id$="replyFileUpload_input"]') ||
            document.querySelector('#replyFileUpload_input') ||
            document.querySelector('#replyCommentDlg input[type="file"]');

        if (!replyFileInput) {
            console.error('Reply file input not found');
            alert('Upload component not available in reply dialog');
            isReplyUploading = false;
            return;
        }

        console.log('Found reply file input:', replyFileInput.id || replyFileInput.name);

        // Create DataTransfer to set files - same as main comment
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);

        // Set files on the input
        replyFileInput.files = dataTransfer.files;

        console.log('Files set on reply input, file count:', replyFileInput.files.length);

        // Store file info for completion handler - same as main comment
        window.currentReplyUploadingFile = {
            name: file.name,
            size: file.size,
            type: file.type
        };

        // Trigger the upload by firing change event - same as main comment
        const changeEvent = new Event('change', {
            bubbles: true,
            cancelable: true
        });

        console.log('Triggering change event for reply upload...');
        replyFileInput.dispatchEvent(changeEvent);

        // Also trigger input event for good measure
        setTimeout(function() {
            const inputEvent = new Event('input', {
                bubbles: true,
                cancelable: true
            });
            replyFileInput.dispatchEvent(inputEvent);
        }, 50);

        console.log('Reply upload events triggered');

    } catch (error) {
        console.error('Error in reply simple mode upload:', error);
        alert('Error uploading file in reply: ' + file.name);
        isReplyUploading = false;
    }
}

// Handle reply upload completion - same pattern as main comment
function handleReplyUploadComplete(a, b, c) {
    let xhr, status, args;
    if (a && typeof a === 'object' && ('xhr' in a || 'status' in a)) {
        ({ xhr, status, args } = a);
    } else {
        xhr = a; status = b; args = c;
    }

    isReplyUploading = false;

    let meta = null;
    if (Array.isArray(window.__replyUploadMetaQueue) && window.__replyUploadMetaQueue.length > 0) {
        meta = window.__replyUploadMetaQueue.shift();
    }

    if (meta && meta.uniqueName) {
        addFileToReplyAttachmentsDisplay(meta);
    } else {
        alert('Upload failed: missing file data');
    }

    window.currentReplyUploadingFile = null;
    const input = document.querySelector('[id$="replyFileUpload_input"]');
    if (input) input.value = '';
}

function addFileToReplyAttachmentsDisplay(fileInfo) {
    const container = document.getElementById('replyAttachmentsContainer');
    const list = document.getElementById('replyAttachmentsList');

    if (!container || !list) return;

    container.classList.add('has-files');
    container.style.display = 'block';

    const displayName = fileInfo.name || fileInfo.originalName || 'file';
    const attachmentId = 'reply_attachment_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);

    // Check if this is an Office file (needs async thumbnail refresh)
    const isOfficeFile = REPLY_OFFICE_EXTENSIONS.test(displayName);

    const thumbName = fileInfo.thumbnailFileName || fileInfo.thumbnailPath;
    const hasThumbnail = fileInfo.thumbnailFileName && !fileInfo.thumbnailFileName.startsWith('TEXT_PREVIEW:');

    // Check if this is a placeholder thumbnail (Office files)
    const isPlaceholder = thumbName && thumbName.startsWith('placeholder_');

    // Track placeholder for polling
    if (isPlaceholder) {
        replyPlaceholderThumbName = thumbName;
    }

    // Use & for cache buster since URL already has ?path=
    const cacheBuster = '&t=' + Date.now();

    // Add loading class for Office files
    const thumbnailClass = isOfficeFile && hasThumbnail ? 'attachment-thumbnail thumbnail-loading' : 'attachment-thumbnail';

    const item = document.createElement('div');
    item.className = 'attachment-item';
    item.id = attachmentId;

    item.innerHTML = `
        <a href="/api/files/download?file=${encodeURIComponent(fileInfo.uniqueName)}" class="attachment-link" download>
            <div class="attachment-preview">
                ${hasThumbnail ?
        `<img src="/api/files/thumbnail?path=${encodeURIComponent(fileInfo.thumbnailFileName)}${cacheBuster}" 
                         alt="thumbnail" class="${thumbnailClass}"/>` :
        `<div class="attachment-icon">${getFileIcon(fileInfo.type, displayName)}</div>`
    }
            </div>
            <div class="attachment-info">
                <div class="attachment-name">${escapeHtml(displayName)}</div>
                <div class="attachment-size">${formatFileSize(fileInfo.size)}</div>
            </div>
        </a>
        <button class="attachment-remove" onclick="removeReplyAttachment('${attachmentId}')" title="Remove file">
            <i class="pi pi-times"></i>
        </button>
    `;

    // Store data attributes for submission
    item.dataset.name = displayName;
    item.dataset.size = String(fileInfo.size || 0);
    item.dataset.type = fileInfo.type || 'application/octet-stream';
    item.dataset.uniqueName = fileInfo.uniqueName || '';
    item.dataset.thumbnailFileName = thumbName || '';

    list.appendChild(item);

    // Schedule thumbnail refresh for Office files
    if (isOfficeFile && isPlaceholder && hasThumbnail) {
        scheduleReplyThumbnailRefresh(attachmentId, fileInfo.thumbnailFileName, displayName);
    }
}

// Remove reply attachment - same pattern as main comment
function removeReplyAttachment(attachmentId, fileName) {
    console.log('Removing reply attachment:', fileName);

    // Stop any active polling
    stopReplyThumbnailPolling(attachmentId);

    const attachmentItem = document.getElementById(attachmentId);
    if (attachmentItem) {
        attachmentItem.remove();
        console.log('Reply attachment removed from display');
    }

    // Hide container if no more files - same as main comment
    const replyAttachmentsList = document.getElementById('replyAttachmentsList');
    const replyAttachmentsContainer = document.getElementById('replyAttachmentsContainer');

    if (replyAttachmentsList && replyAttachmentsList.children.length === 0) {
        if (replyAttachmentsContainer) {
            replyAttachmentsContainer.classList.remove('has-files');
            replyAttachmentsContainer.style.display = 'none';
        }
    }

    // Update submission data
    prepareReplyCommentForSubmission();
}

// Clear reply comment and attachments - same pattern as main comment
function clearReplyComment() {
    console.log('Clearing reply comment and attachments');

    // Stop all active polls
    Object.keys(window.__replyThumbnailPolls || {}).forEach(id => stopReplyThumbnailPolling(id));

    replyDragDropInitialized = false;

    // Clear the editor
    if (window.PrimeFaces && PrimeFaces.widgets['replyCommentWgVar']) {
        PrimeFaces.widgets['replyCommentWgVar'].setValue('');
    }

    // Clear attachments display
    const replyAttachmentsList = document.getElementById('replyAttachmentsList');
    const replyAttachmentsContainer = document.getElementById('replyAttachmentsContainer');

    if (replyAttachmentsList) {
        replyAttachmentsList.innerHTML = '';
    }

    if (replyAttachmentsContainer) {
        replyAttachmentsContainer.classList.remove('has-files');
        replyAttachmentsContainer.style.display = 'none';
    }

    // Clear any stored attachment data
    const replyAttachmentDataField = document.getElementById('collaborationTaskTabView:replyAttachmentDataField');
    if (replyAttachmentDataField) {
        replyAttachmentDataField.value = '';
    }
}

// Prepare reply comment for submission - same pattern as main comment
function prepareReplyCommentForSubmission() {
    console.log('Preparing reply comment for submission');

    const replyAttachmentsList = document.getElementById('replyAttachmentsList')
        || document.querySelector('[id$=":replyAttachmentsList"]')
        || document.querySelector('[id*="replyAttachmentsList"]');

    const field = document.getElementById('replyAttachmentDataField')
        || document.querySelector('[id$="replyAttachmentDataField"]');

    if (!field) {
        console.warn('replyAttachmentDataField not found; attachmentData will be empty on submit.');
        return;
    }

    const items = replyAttachmentsList ? replyAttachmentsList.querySelectorAll('.attachment-item') : [];
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

    console.log('Reply attachment data prepared:', meta);
}

// Debug function for reply attachments
function debugReplyAttachments() {
    console.log('=== REPLY ATTACHMENTS DEBUG ===');

    const replyAttachmentsList = document.getElementById('replyAttachmentsList');
    const replyAttachmentDataField = document.getElementById('collaborationTaskTabView:replyAttachmentDataField');

    console.log('Reply attachments list children:', replyAttachmentsList ? replyAttachmentsList.children.length : 'not found');
    console.log('Reply attachment data field value:', replyAttachmentDataField ? replyAttachmentDataField.value : 'not found');

    if (replyAttachmentsList) {
        Array.from(replyAttachmentsList.children).forEach((item, index) => {
            const nameElement = item.querySelector('.attachment-name');
            const sizeElement = item.querySelector('.attachment-size');
            console.log(`Reply attachment ${index}:`, {
                id: item.id,
                name: nameElement ? nameElement.textContent : 'no name',
                size: sizeElement ? sizeElement.textContent : 'no size'
            });
        });
    }
}

// Expose functions globally
window.prepareReplyCommentForSubmission = prepareReplyCommentForSubmission;

// Reuse utility functions from main comment implementation if not already defined
if (typeof getFileIcon === 'undefined') {
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
}

if (typeof escapeHtml === 'undefined') {
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
    }
}

if (typeof formatFileSize === 'undefined') {
    function formatFileSize(bytes) {
        if (!bytes || bytes <= 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }
}

if (typeof parseSizeToBytes === 'undefined') {
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
}

// Initialize when reply dialog opens - hook into existing dialog show mechanism
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM loaded, reply dialog drag and drop ready...');

    // The initialization will be called by the dialog onShow event
    // No need for additional DOM ready initialization
});