/**
 * Background Removal Page JavaScript
 */

// Global variables
let currentProcessedData = null;
let currentFilename = null;

// DOM Elements
const form = document.getElementById('backgroundRemovalForm');
const fileInput = document.getElementById('imageUpload');
const processBtn = document.getElementById('processBtn');
const imagePreview = document.getElementById('imagePreview');
const imagePreviewContainer = document.getElementById('imagePreviewContainer');
const imageInfo = document.getElementById('imageInfo');
const downloadBtn = document.getElementById('downloadBtn');

// State containers
const initialState = document.getElementById('initialState');
const loadingState = document.getElementById('loadingState');
const errorState = document.getElementById('errorState');
const resultsContainer = document.getElementById('resultsContainer');

// Initialize the page
document.addEventListener('DOMContentLoaded', function() {
    console.log('Background Removal page initialized');
    
    // Debug: Check if elements exist
    console.log('Form element found:', form);
    console.log('File input found:', fileInput);
    console.log('Process button found:', processBtn);
    
    if (!form) {
        console.error('ERROR: backgroundRemovalForm not found!');
        return;
    }
    
    // Set up event listeners
    setupEventListeners();
    
    // Reset to initial state
    resetToInitialState();
});

function setupEventListeners() {
    console.log('Setting up event listeners...');
    
    // File input change
    if (fileInput) {
        fileInput.addEventListener('change', handleFileSelect);
        console.log('File input change listener added');
    }
    
    // Form submission
    if (form) {
        form.addEventListener('submit', handleFormSubmit);
        console.log('Form submit listener added');
    }
    
    // Process button click (alternative to form submit)
    if (processBtn) {
        processBtn.addEventListener('click', function(event) {
            console.log('Process button clicked!');
            event.preventDefault();
            handleFormSubmit(event);
        });
        console.log('Process button click listener added');
    }
    
    // Download button
    if (downloadBtn) {
        downloadBtn.addEventListener('click', handleDownload);
        console.log('Download button listener added');
    }
    
    // Drag and drop support
    setupDragAndDrop();
}

function setupDragAndDrop() {
    const leftPanel = document.querySelector('.left-panel');
    
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        leftPanel.addEventListener(eventName, preventDefaults, false);
    });
    
    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }
    
    ['dragenter', 'dragover'].forEach(eventName => {
        leftPanel.addEventListener(eventName, highlight, false);
    });
    
    ['dragleave', 'drop'].forEach(eventName => {
        leftPanel.addEventListener(eventName, unhighlight, false);
    });
    
    function highlight(e) {
        leftPanel.classList.add('drag-highlight');
    }
    
    function unhighlight(e) {
        leftPanel.classList.remove('drag-highlight');
    }
    
    leftPanel.addEventListener('drop', handleDrop, false);
    
    function handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        
        if (files.length > 0) {
            fileInput.files = files;
            handleFileSelect({ target: { files: files } });
        }
    }
}

function handleFileSelect(event) {
    const file = event.target.files[0];
    
    if (!file) {
        hideImagePreview();
        return;
    }
    
    // Validate file type
    if (!file.type.startsWith('image/')) {
        showError('Please select a valid image file');
        hideImagePreview();
        return;
    }
    
    // Validate file size (10MB max)
    const maxSize = 10 * 1024 * 1024; // 10MB
    if (file.size > maxSize) {
        showError('File size must be less than 10MB');
        hideImagePreview();
        return;
    }
    
    // Store filename for later use
    currentFilename = file.name;
    
    // Show preview
    showImagePreview(file);
    
    console.log('File selected:', file.name, 'Size:', formatFileSize(file.size));
}

function showImagePreview(file) {
    const reader = new FileReader();
    
    reader.onload = function(e) {
        imagePreview.src = e.target.result;
        imageInfo.textContent = `${file.name} (${formatFileSize(file.size)})`;
        imagePreviewContainer.classList.remove('d-none');
    };
    
    reader.readAsDataURL(file);
}

function hideImagePreview() {
    imagePreviewContainer.classList.add('d-none');
    imagePreview.src = '';
    imageInfo.textContent = '';
}

function handleFormSubmit(event) {
    console.log('Form submit handler called!');
    event.preventDefault();
    
    const formData = new FormData();
    const file = fileInput.files[0];
    
    console.log('Selected file:', file);
    
    if (!file) {
        console.log('No file selected, showing error');
        showError('Please select an image file');
        return;
    }
    
    formData.append('image', file);
    
    console.log('Starting background removal process...');
    
    // Show loading state
    showLoadingState();
    
    const startTime = Date.now();
    
    // Make the request
    console.log('Sending POST request to /background-removal/process');
    fetch('/background-removal/process', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        console.log('Response received:', response.status, response.statusText);
        return response.json();
    })
    .then(data => {
        console.log('Response data:', data);
        const processingTime = Date.now() - startTime;
        handleProcessingResponse(data, processingTime);
    })
    .catch(error => {
        console.error('Fetch error:', error);
        showError('Network error occurred. Please try again.');
    });
}

function handleProcessingResponse(data, processingTime) {
    console.log('Processing response:', data);
    
    if (!data.success) {
        showError(data.error || 'Processing failed');
        return;
    }
    
    // Store processed data for download
    currentProcessedData = data;
    
    // Update processing info
    updateProcessingInfo(processingTime, data);
    
    // Show results
    showResults(data);
}

function updateProcessingInfo(processingTime, data) {
    const processingInfoCard = document.getElementById('processingInfo');
    const processingTimeEl = document.getElementById('processingTime');
    const sizeReductionEl = document.getElementById('sizeReduction');
    
    processingTimeEl.textContent = `${(processingTime / 1000).toFixed(1)}s`;
    sizeReductionEl.textContent = data.sizeReduction || '0%';
    
    processingInfoCard.classList.remove('d-none');
}

function showResults(data) {
    // Hide other states
    hideAllStates();
    
    // Show results container
    resultsContainer.classList.remove('d-none');
    
    // Set images
    document.getElementById('originalImage').src = data.originalImage;
    document.getElementById('processedImage').src = data.processedImage;
    
    // Update badges and stats
    document.getElementById('originalSize').textContent = formatFileSize(data.originalSize);
    document.getElementById('processedSize').textContent = formatFileSize(data.processedSize);
    
    // Update statistics
    document.getElementById('statOriginalSize').textContent = formatFileSize(data.originalSize);
    document.getElementById('statProcessedSize').textContent = formatFileSize(data.processedSize);
    document.getElementById('statReduction').textContent = data.sizeReduction || '0%';
    
    // Status
    const statusEl = document.getElementById('statStatus');
    const messageEl = document.getElementById('processingMessage');
    
    if (data.backgroundRemoved) {
        statusEl.textContent = 'Success';
        statusEl.className = 'text-success mb-0';
        messageEl.textContent = 'Background successfully removed using rembg';
    } else {
        statusEl.textContent = 'No Change';
        statusEl.className = 'text-warning mb-0';
        messageEl.textContent = data.message || 'Image processed but no background removal occurred';
    }
    
    console.log('Results displayed successfully');
}

function handleDownload() {
    if (!currentProcessedData || !currentProcessedData.processedImage) {
        showError('No processed image available for download');
        return;
    }
    
    // Create form for download
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/background-removal/download';
    
    // Add image data
    const imageDataInput = document.createElement('input');
    imageDataInput.type = 'hidden';
    imageDataInput.name = 'imageData';
    imageDataInput.value = currentProcessedData.processedImage;
    form.appendChild(imageDataInput);
    
    // Add filename
    const filenameInput = document.createElement('input');
    filenameInput.type = 'hidden';
    filenameInput.name = 'filename';
    filenameInput.value = currentFilename || 'processed_image.png';
    form.appendChild(filenameInput);
    
    // Submit form
    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
    
    console.log('Download initiated for:', currentFilename);
}

function showLoadingState() {
    hideAllStates();
    loadingState.classList.remove('d-none');
    
    // Update button state
    processBtn.disabled = true;
    processBtn.innerHTML = `
        <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
        Processing...
    `;
}

function showError(message) {
    hideAllStates();
    
    document.getElementById('errorMessage').textContent = message;
    errorState.classList.remove('d-none');
    
    // Reset button state
    resetButtonState();
    
    console.error('Error shown:', message);
}

function resetToInitialState() {
    hideAllStates();
    initialState.classList.remove('d-none');
    
    // Reset form
    form.reset();
    hideImagePreview();
    
    // Reset button state
    resetButtonState();
    
    // Reset data
    currentProcessedData = null;
    currentFilename = null;
    
    // Hide processing info
    document.getElementById('processingInfo').classList.add('d-none');
    
    console.log('Reset to initial state');
}

function resetButtonState() {
    processBtn.disabled = false;
    processBtn.innerHTML = `
        <span class="spinner-border spinner-border-sm d-none" role="status" aria-hidden="true"></span>
        <i class="bi bi-magic"></i>
        Remove Background
    `;
}

function hideAllStates() {
    initialState.classList.add('d-none');
    loadingState.classList.add('d-none');
    errorState.classList.add('d-none');
    resultsContainer.classList.add('d-none');
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Expose functions to global scope for HTML onclick handlers
window.resetToInitialState = resetToInitialState;
