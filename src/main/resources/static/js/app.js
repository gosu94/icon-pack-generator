document.addEventListener('DOMContentLoaded', function() {
    const iconCountSelect = document.getElementById('iconCount');
    const iconDescriptionsContainer = document.getElementById('iconDescriptions');
    const iconForm = document.getElementById('iconForm');
    const generateBtn = document.getElementById('generateBtn');
    const exportBtn = document.getElementById('exportBtn');
    const loadingState = document.getElementById('loadingState');
    const errorState = document.getElementById('errorState');
    const resultsGrid = document.getElementById('resultsGrid');
    const initialState = document.getElementById('initialState');
    
    // Export modal elements
    const exportModal = document.getElementById('exportModal');
    const exportProgressModal = document.getElementById('exportProgressModal');
    const removeBackgroundToggle = document.getElementById('removeBackgroundToggle');
    const confirmExportBtn = document.getElementById('confirmExportBtn');
    const exportIconCount = document.getElementById('exportIconCount');
    
    // Progress modal elements
    const exportProgressTitle = document.getElementById('exportProgressTitle');
    const exportProgressMessage = document.getElementById('exportProgressMessage');
    const currentStep = document.getElementById('currentStep');
    const totalSteps = document.getElementById('totalSteps');
    
    let currentIcons = []; // Store current icons for export
    let currentRequest = null; // Store current request for missing icons feature
    let currentResponse = null; // Store current response for accessing original images
    let exportModalInstance = null;
    let progressModalInstance = null;

    // Handle icon count change to show/hide individual description fields
    iconCountSelect.addEventListener('change', function() {
        const count = parseInt(this.value);
        updateIconDescriptionFields(count);
    });

    // Handle form submission
    iconForm.addEventListener('submit', function(e) {
        e.preventDefault();
        
        if (validateForm()) {
            generateIcons();
        }
    });

    // Initialize Bootstrap modals
    if (exportModal) {
        exportModalInstance = new bootstrap.Modal(exportModal);
    }
    if (exportProgressModal) {
        progressModalInstance = new bootstrap.Modal(exportProgressModal);
    }

    // Handle export button click - show options modal
    exportBtn.addEventListener('click', function() {
        if (currentIcons.length > 0) {
            showExportModal();
        }
    });

    // Handle export confirmation
    if (confirmExportBtn) {
        confirmExportBtn.addEventListener('click', function() {
            const removeBackground = removeBackgroundToggle.checked;
            exportModalInstance.hide();
            exportIcons(currentIcons, removeBackground);
        });
    }

    function updateIconDescriptionFields(count) {
        iconDescriptionsContainer.innerHTML = '';
        
        if (count > 0) {
            for (let i = 1; i <= count; i++) {
                const fieldDiv = document.createElement('div');
                fieldDiv.className = 'icon-field';
                fieldDiv.innerHTML = `
                    <input type="text" class="form-control form-control-sm" 
                           name="individualDescriptions" 
                           placeholder="Icon ${i} description (optional)">
                `;
                iconDescriptionsContainer.appendChild(fieldDiv);
            }
        }
    }

    function validateForm() {
        const generalDescription = document.getElementById('generalDescription').value.trim();
        const iconCount = document.getElementById('iconCount').value;
        
        let isValid = true;

        // Reset validation states
        document.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));

        if (!generalDescription) {
            document.getElementById('generalDescription').classList.add('is-invalid');
            isValid = false;
        }

        if (!iconCount) {
            document.getElementById('iconCount').classList.add('is-invalid');
            isValid = false;
        }

        return isValid;
    }

    function generateIcons() {
        // Show streaming loading state
        setUIState('streaming');
        
        // Collect form data
        const formData = {
            generalDescription: document.getElementById('generalDescription').value.trim(),
            iconCount: parseInt(document.getElementById('iconCount').value),
            individualDescriptions: []
        };

        // Collect individual descriptions
        const descriptionInputs = document.querySelectorAll('input[name="individualDescriptions"]');
        descriptionInputs.forEach(input => {
            formData.individualDescriptions.push(input.value.trim());
        });
        
        // Store current request for missing icons feature
        currentRequest = { ...formData };

        // Initialize streaming UI
        initializeStreamingUI();

        // Step 1: Start the generation process and get request ID
        fetch('/generate-stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(formData)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            const requestId = data.requestId;
            const enabledServices = data.enabledServices;
            console.log('Generation started with request ID:', requestId);
            console.log('Enabled services:', enabledServices);
            
            // Update the streaming UI to only show enabled services
            updateStreamingUIForEnabledServices(enabledServices);
            
            // Step 2: Connect to SSE stream
            const eventSource = new EventSource(`/stream/${requestId}`);
            
            // Handle service updates
            eventSource.addEventListener('service_update', function(event) {
                try {
                    const update = JSON.parse(event.data);
                    handleServiceUpdate(update);
                } catch (error) {
                    console.error('Error parsing service update:', error);
                }
            });

            // Handle generation completion
            eventSource.addEventListener('generation_complete', function(event) {
                try {
                    const update = JSON.parse(event.data);
                    handleGenerationComplete(update);
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing completion update:', error);
                    eventSource.close();
                }
            });

            // Handle errors
            eventSource.addEventListener('generation_error', function(event) {
                try {
                    const update = JSON.parse(event.data);
                    showError(update.message || 'Generation failed');
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing error update:', error);
                    showError('Generation failed with unknown error');
                    eventSource.close();
                }
            });

            // Handle EventSource errors
            eventSource.onerror = function(error) {
                console.error('EventSource error:', error);
                showError('Connection error. Please try again.');
                eventSource.close();
            };
        })
        .catch(error => {
            console.error('Error starting generation:', error);
            showError('Failed to start generation. Please try again.');
        });
    }

    function setUIState(state) {
        // Hide all states first
        loadingState.classList.add('d-none');
        errorState.classList.add('d-none');
        resultsGrid.classList.add('d-none');
        initialState.classList.add('d-none');
        exportBtn.classList.add('d-none');

        const spinner = generateBtn.querySelector('.spinner-border');
        
        switch (state) {
            case 'loading':
                loadingState.classList.remove('d-none');
                generateBtn.disabled = true;
                spinner.classList.remove('d-none');
                break;
            case 'streaming':
                resultsGrid.classList.remove('d-none');
                generateBtn.disabled = true;
                spinner.classList.remove('d-none');
                break;
            case 'error':
                errorState.classList.remove('d-none');
                generateBtn.disabled = false;
                spinner.classList.add('d-none');
                break;
            case 'results':
                resultsGrid.classList.remove('d-none');
                exportBtn.classList.remove('d-none');
                generateBtn.disabled = false;
                spinner.classList.add('d-none');
                break;
            default:
                initialState.classList.remove('d-none');
                generateBtn.disabled = false;
                spinner.classList.add('d-none');
        }
    }

    // Global variables for streaming
    let streamingServices = {};
    let streamingResults = {};

    function initializeStreamingUI() {
        resultsGrid.innerHTML = '';
        streamingServices = {};
        streamingResults = {};
        
        // Create container for streaming results
        const servicesContainer = document.createElement('div');
        servicesContainer.className = 'services-container streaming';
        servicesContainer.id = 'streamingContainer';
        
        // Initially create container, services will be added based on enabled services
        resultsGrid.appendChild(servicesContainer);
    }

    function updateStreamingUIForEnabledServices(enabledServices) {
        const servicesContainer = document.getElementById('streamingContainer');
        if (!servicesContainer) return;
        
        // Clear any existing content
        servicesContainer.innerHTML = '';
        
        // Define all possible services
        const allServices = [
            { id: 'flux', name: 'Flux-Pro' },
            { id: 'recraft', name: 'Recraft V3' },
            { id: 'photon', name: 'Luma Photon' },
            { id: 'gpt', name: 'GPT Image' },
            { id: 'imagen', name: 'Imagen 4' }
        ];
        
        // Filter to only enabled services
        const enabledServicesList = allServices.filter(service => enabledServices[service.id]);
        
        // Create sections only for enabled services
        enabledServicesList.forEach((service, index) => {
            if (index > 0) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            
            const section = createStreamingServiceSection(service.name, service.id);
            servicesContainer.appendChild(section);
        });
    }

    function createStreamingServiceSection(serviceName, serviceId) {
        const section = document.createElement('div');
        section.className = `service-section ${serviceId}-section streaming`;
        section.id = `section-${serviceId}`;
        
        // Create header with progress indicator
        const header = document.createElement('div');
        header.className = 'service-header';
        header.innerHTML = `
            <h4 class="service-title">
                <span class="service-status-icon" id="status-${serviceId}">⏳</span> 
                ${serviceName}
                <span class="generation-time" id="time-${serviceId}"></span>
            </h4>
            <div class="progress mb-2" id="progress-${serviceId}" style="height: 4px;">
                <div class="progress-bar progress-bar-striped progress-bar-animated" 
                     role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
                </div>
            </div>
            <p class="service-status" id="message-${serviceId}">Initializing...</p>
        `;
        section.appendChild(header);
        
        // Create placeholder for icons
        const iconsContainer = document.createElement('div');
        iconsContainer.className = 'service-icons-container';
        iconsContainer.id = `icons-${serviceId}`;
        section.appendChild(iconsContainer);
        
        return section;
    }

    function handleServiceUpdate(update) {
        const serviceId = update.serviceName;
        const statusIcon = document.getElementById(`status-${serviceId}`);
        const progressBar = document.querySelector(`#progress-${serviceId} .progress-bar`);
        const messageElement = document.getElementById(`message-${serviceId}`);
        const timeElement = document.getElementById(`time-${serviceId}`);
        const iconsContainer = document.getElementById(`icons-${serviceId}`);
        
        if (!statusIcon || !progressBar || !messageElement) {
            console.warn('Service UI elements not found for:', serviceId);
            return;
        }
        
        switch (update.status) {
            case 'started':
                statusIcon.textContent = '🔄';
                progressBar.style.width = '25%';
                messageElement.textContent = 'Generation started...';
                break;
                
            case 'success':
                statusIcon.textContent = '✅';
                progressBar.style.width = '100%';
                progressBar.classList.remove('progress-bar-animated');
                messageElement.textContent = update.message || 'Generation completed successfully';
                
                // Display generation time
                if (update.generationTimeMs) {
                    timeElement.textContent = ` (${(update.generationTimeMs / 1000).toFixed(1)}s)`;
                }
                
                // Display icons
                if (update.icons && update.icons.length > 0) {
                    displayServiceIcons(serviceId, update.icons, getServiceDisplayName(serviceId));
                    
                    // Store results for final export
                    streamingResults[serviceId] = {
                        icons: update.icons,
                        originalGridImageBase64: update.originalGridImageBase64,
                        generationTimeMs: update.generationTimeMs,
                        status: 'success',
                        message: update.message
                    };
                }
                break;
                
            case 'error':
                statusIcon.textContent = '❌';
                progressBar.style.width = '100%';
                progressBar.classList.remove('progress-bar-animated');
                progressBar.classList.add('bg-danger');
                messageElement.textContent = update.message || 'Generation failed';
                
                if (update.generationTimeMs) {
                    timeElement.textContent = ` (${(update.generationTimeMs / 1000).toFixed(1)}s)`;
                }
                break;
        }
    }

    function displayServiceIcons(serviceId, icons, serviceName) {
        const iconsContainer = document.getElementById(`icons-${serviceId}`);
        if (!iconsContainer) return;
        
        const iconsGrid = document.createElement('div');
        iconsGrid.className = 'row g-3 service-icons-grid';
        
        icons.forEach((icon, index) => {
            const iconDiv = document.createElement('div');
            iconDiv.className = 'col-md-4 col-sm-6';
            iconDiv.innerHTML = `
                <div class="icon-item fade-in">
                    <img src="data:image/png;base64,${icon.base64Data}" 
                         alt="Generated Icon ${index + 1}" 
                         class="img-fluid">
                    <div class="icon-description">${icon.description || `Icon ${index + 1}`}</div>
                    <div class="service-badge">${serviceName}</div>
                </div>
            `;
            iconsGrid.appendChild(iconDiv);
        });
        
        iconsContainer.appendChild(iconsGrid);
    }

    function handleGenerationComplete(update) {
        console.log('Generation completed:', update);
        
        // Combine all successful results for export
        let allIcons = [];
        Object.values(streamingResults).forEach(result => {
            if (result.icons) {
                allIcons = allIcons.concat(result.icons);
            }
        });
        
        currentIcons = allIcons;
        currentResponse = {
            icons: allIcons,
            falAiResults: streamingResults.flux,
            recraftResults: streamingResults.recraft,
            photonResults: streamingResults.photon,
            gptResults: streamingResults.gpt,
            imagenResults: streamingResults.imagen
        };
        
        // Show export button and enable generate button
        setUIState('results');
        
        // Add missing icons sections if we have individual descriptions
        if (currentRequest && currentRequest.individualDescriptions && currentRequest.individualDescriptions.length > 0) {
            Object.keys(streamingResults).forEach(serviceId => {
                const result = streamingResults[serviceId];
                if (result && result.status === 'success') {
                    const serviceName = getServiceDisplayName(serviceId);
                    const section = document.getElementById(`section-${serviceId}`);
                    if (section) {
                        const missingSection = createMissingIconsSection(serviceId, serviceName);
                        section.appendChild(missingSection);
                    }
                }
            });
        }
    }

    function getServiceDisplayName(serviceId) {
        const serviceNames = {
            'flux': 'Flux-Pro',
            'recraft': 'Recraft V3',
            'photon': 'Luma Photon',
            'gpt': 'GPT Image',
            'imagen': 'Imagen 4'
        };
        return serviceNames[serviceId] || serviceId;
    }

    function displayResults(data) {
        resultsGrid.innerHTML = '';
        currentIcons = data.icons; // Store all icons for export
        currentResponse = data; // Store current response for missing icons feature
        
        // Create container for both service results
        const servicesContainer = document.createElement('div');
        servicesContainer.className = 'services-container';
        
        let hasMultipleSections = false;
        
        // Display FalAI results (only if enabled and has results)
        if (data.falAiResults && data.falAiResults.status !== 'disabled' && data.falAiResults.icons && data.falAiResults.icons.length > 0) {
            const falAiSection = createServiceSection('Flux-Pro', data.falAiResults, 'flux');
            servicesContainer.appendChild(falAiSection);
            hasMultipleSections = true;
        }
        
        // Display Recraft results (only if enabled and has results)
        if (data.recraftResults && data.recraftResults.status !== 'disabled' && data.recraftResults.icons && data.recraftResults.icons.length > 0) {
            if (hasMultipleSections) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            const recraftSection = createServiceSection('Recraft V3', data.recraftResults, 'recraft');
            servicesContainer.appendChild(recraftSection);
            hasMultipleSections = true;
        }
        
        // Display Photon results (only if enabled and has results)
        if (data.photonResults && data.photonResults.status !== 'disabled' && data.photonResults.icons && data.photonResults.icons.length > 0) {
            if (hasMultipleSections) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            const photonSection = createServiceSection('Luma Photon', data.photonResults, 'photon');
            servicesContainer.appendChild(photonSection);
            hasMultipleSections = true;
        }
        
        // Display GPT results (only if enabled and has results)
        if (data.gptResults && data.gptResults.status !== 'disabled' && data.gptResults.icons && data.gptResults.icons.length > 0) {
            if (hasMultipleSections) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            const gptSection = createServiceSection('GPT Image', data.gptResults, 'gpt');
            servicesContainer.appendChild(gptSection);
            hasMultipleSections = true;
        }
        
        // Display Imagen results (only if enabled and has results)
        if (data.imagenResults && data.imagenResults.status !== 'disabled' && data.imagenResults.icons && data.imagenResults.icons.length > 0) {
            if (hasMultipleSections) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            const imagenSection = createServiceSection('Imagen 4', data.imagenResults, 'imagen');
            servicesContainer.appendChild(imagenSection);
            hasMultipleSections = true;
        }
        
        resultsGrid.appendChild(servicesContainer);
        setUIState('results');
    }
    
    function createServiceSection(serviceName, serviceResults, serviceId) {
        const section = document.createElement('div');
        section.className = `service-section ${serviceId}-section`;
        
        // Create header
        const header = document.createElement('div');
        header.className = 'service-header';
        
        let statusIcon;
        if (serviceResults.status === 'success') {
            statusIcon = '✅';
        } else if (serviceResults.status === 'disabled') {
            statusIcon = '⚫';
        } else {
            statusIcon = '❌';
        }
        
        const generationTime = serviceResults.generationTimeMs && serviceResults.generationTimeMs > 0 ? 
            ` (${(serviceResults.generationTimeMs / 1000).toFixed(1)}s)` : '';
        
        header.innerHTML = `
            <h4 class="service-title">
                ${statusIcon} ${serviceName}${generationTime}
            </h4>
            <p class="service-status">${serviceResults.message || serviceResults.status}</p>
        `;
        section.appendChild(header);
        
        // Create icons grid
        if (serviceResults.icons && serviceResults.icons.length > 0) {
            const iconsGrid = document.createElement('div');
            iconsGrid.className = 'row g-3 service-icons-grid';
            
            serviceResults.icons.forEach((icon, index) => {
                const iconDiv = document.createElement('div');
                iconDiv.className = 'col-md-4 col-sm-6';
                iconDiv.innerHTML = `
                    <div class="icon-item fade-in">
                        <img src="data:image/png;base64,${icon.base64Data}" 
                             alt="Generated Icon ${index + 1}" 
                             class="img-fluid">
                        <div class="icon-description">${icon.description || `Icon ${index + 1}`}</div>
                        <div class="service-badge">${serviceName}</div>
                    </div>
                `;
                iconsGrid.appendChild(iconDiv);
            });
            
            section.appendChild(iconsGrid);
        }
        
        // Add "What is missing" section if service was successful and we have individual descriptions
        if (serviceResults.status === 'success' && currentRequest && currentRequest.individualDescriptions && currentRequest.individualDescriptions.length > 0) {
            const missingSection = createMissingIconsSection(serviceId, serviceName);
            section.appendChild(missingSection);
        }
        
        return section;
    }

    function createMissingIconsSection(serviceId, serviceName) {
        const missingSection = document.createElement('div');
        missingSection.className = 'missing-icons-section mt-4';
        missingSection.id = `missing-${serviceId}`;
        
        // Get non-empty individual descriptions
        const nonEmptyDescriptions = currentRequest.individualDescriptions.filter(desc => desc && desc.trim() !== '');
        
        if (nonEmptyDescriptions.length === 0) {
            return missingSection; // Return empty section if no descriptions
        }
        
        missingSection.innerHTML = `
            <div class="missing-icons-header">
                <h6 class="text-muted mb-3">
                    <i class="bi bi-plus-circle me-2"></i>What is missing?
                </h6>
                <p class="small text-muted mb-3">
                    Select the icons you want to generate using ${serviceName} image-to-image:
                </p>
            </div>
            <div class="missing-icons-checkboxes mb-3">
                ${nonEmptyDescriptions.map((desc, index) => `
                    <div class="form-check form-check-inline me-3 mb-2">
                        <input class="form-check-input" type="checkbox" 
                               id="missing-${serviceId}-${index}" 
                               value="${desc}">
                        <label class="form-check-label small" for="missing-${serviceId}-${index}">
                            ${desc}
                        </label>
                    </div>
                `).join('')}
            </div>
            <div class="missing-icons-actions">
                <button class="btn btn-outline-primary btn-sm" 
                        onclick="generateMissingIcons('${serviceId}', '${serviceName}')"
                        id="generate-missing-${serviceId}">
                    <i class="bi bi-magic me-2"></i>Generate Selected
                </button>
                <button class="btn btn-outline-secondary btn-sm ms-2" 
                        onclick="selectAllMissing('${serviceId}')">
                    Select All
                </button>
            </div>
            <div class="missing-icons-results mt-3" id="missing-results-${serviceId}" style="display: none;">
                <!-- New icons will appear here -->
            </div>
        `;
        
        return missingSection;
    }

    function showError(message) {
        document.getElementById('errorMessage').textContent = message;
        setUIState('error');
    }

    function showExportModal() {
        // Update icon count in modal
        if (exportIconCount) {
            exportIconCount.textContent = currentIcons.length;
        }
        
        // Show the modal
        if (exportModalInstance) {
            exportModalInstance.show();
        }
    }

    function showExportProgress(step, message, progressPercent) {
        // Update progress content
        if (exportProgressTitle) {
            exportProgressTitle.textContent = message;
        }
        
        if (currentStep) {
            currentStep.textContent = `Step ${step}`;
        }
        
        // Update progress bar
        const progressBar = document.querySelector('#exportProgressModal .progress-bar');
        if (progressBar) {
            progressBar.style.width = `${progressPercent}%`;
        }
        
        // Show progress modal if not already shown
        if (progressModalInstance && !exportProgressModal.classList.contains('show')) {
            progressModalInstance.show();
        }
    }

    function hideExportProgress() {
        if (progressModalInstance) {
            progressModalInstance.hide();
        }
    }

    function exportIcons(icons, removeBackground = false) {
        const requestId = Date.now(); // Simple request ID for this export
        
        // Show progress - Step 1
        showExportProgress(1, 'Preparing export request...', 25);
        
        const exportData = {
            requestId: requestId,
            icons: icons,
            removeBackground: removeBackground
        };
        
        // Show progress - Step 2
        setTimeout(() => {
            showExportProgress(2, removeBackground ? 'Processing icons and removing backgrounds...' : 'Processing icons...', 50);
        }, 500);
        
        // Create a blob URL for the export endpoint
        fetch('/export', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(exportData)
        })
        .then(response => {
            // Show progress - Step 3
            showExportProgress(3, 'Creating ZIP file...', 75);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.blob();
        })
        .then(blob => {
            // Show progress - Step 4
            showExportProgress(4, 'Finalizing download...', 100);
            
            // Small delay to show completion
            setTimeout(() => {
                hideExportProgress();
                
                // Create download link
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;
                a.download = `icon-pack-${requestId}.zip`;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                
                // Show success message
                showSuccessToast('Icon pack downloaded successfully!');
            }, 1000);
        })
        .catch(error => {
            console.error('Error exporting icons:', error);
            hideExportProgress();
            showErrorToast('Failed to export icons. Please try again.');
        });
    }

    function showSuccessToast(message) {
        // Create and show a temporary success message
        const toast = document.createElement('div');
        toast.className = 'toast-notification toast-success';
        toast.innerHTML = `
            <div class="d-flex align-items-center">
                <i class="bi bi-check-circle-fill me-2"></i>
                <span>${message}</span>
            </div>
        `;
        document.body.appendChild(toast);
        
        // Show and auto-hide
        setTimeout(() => toast.classList.add('show'), 100);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => document.body.removeChild(toast), 300);
        }, 3000);
    }

    function showErrorToast(message) {
        // Create and show a temporary error message
        const toast = document.createElement('div');
        toast.className = 'toast-notification toast-error';
        toast.innerHTML = `
            <div class="d-flex align-items-center">
                <i class="bi bi-exclamation-triangle-fill me-2"></i>
                <span>${message}</span>
            </div>
        `;
        document.body.appendChild(toast);
        
        // Show and auto-hide
        setTimeout(() => toast.classList.add('show'), 100);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => document.body.removeChild(toast), 300);
        }, 5000);
    }

    // Missing Icons Generation Functions
    window.generateMissingIcons = function(serviceId, serviceName) {
        const selectedDescriptions = getSelectedMissingDescriptions(serviceId);
        
        if (selectedDescriptions.length === 0) {
            showErrorToast('Please select at least one icon to generate');
            return;
        }
        
        const generateBtn = document.getElementById(`generate-missing-${serviceId}`);
        const originalText = generateBtn.innerHTML;
        
        // Show loading state
        generateBtn.disabled = true;
        generateBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Generating...';
        
        // Get the original image for this service
        const originalImageBase64 = getOriginalImageForService(serviceId);
        
        if (!originalImageBase64) {
            showErrorToast('Original image not found for this service');
            generateBtn.disabled = false;
            generateBtn.innerHTML = originalText;
            return;
        }
        
        // Prepare request
        const missingRequest = {
            originalRequestId: currentResponse.requestId,
            serviceName: serviceId,
            originalImageBase64: originalImageBase64,
            generalDescription: currentRequest.generalDescription,
            missingIconDescriptions: selectedDescriptions,
            iconCount: 9,
            seed: currentResponse.seed // Use the same seed for consistency
        };
        
        console.log(`Generating missing icons for ${serviceName} with seed: ${currentResponse.seed}`);
        
        // Make API call
        fetch('/generate-missing', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(missingRequest)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            if (data.status === 'success') {
                displayMissingIconsResults(serviceId, data);
                showSuccessToast(`Generated new 3x3 grid (${data.newIcons.length} icons) with ${serviceName}!`);
            } else {
                showErrorToast(data.message || 'Failed to generate missing icons');
            }
        })
        .catch(error => {
            console.error('Error generating missing icons:', error);
            showErrorToast('Failed to generate missing icons. Please try again.');
        })
        .finally(() => {
            generateBtn.disabled = false;
            generateBtn.innerHTML = originalText;
        });
    };

    window.selectAllMissing = function(serviceId) {
        const checkboxes = document.querySelectorAll(`#missing-${serviceId} input[type="checkbox"]`);
        const selectAllBtn = document.querySelector(`#missing-${serviceId} .btn-outline-secondary`);
        
        // Check if all are currently selected
        const allSelected = Array.from(checkboxes).every(cb => cb.checked);
        
        // Toggle all checkboxes
        checkboxes.forEach(cb => cb.checked = !allSelected);
        
        // Update button text
        selectAllBtn.textContent = allSelected ? 'Select All' : 'Deselect All';
    };

    function getSelectedMissingDescriptions(serviceId) {
        const selectedCheckboxes = document.querySelectorAll(`#missing-${serviceId} input[type="checkbox"]:checked`);
        return Array.from(selectedCheckboxes).map(cb => cb.value);
    }

    function getOriginalImageForService(serviceId) {
        // Get the original grid image before cropping (not individual icons)
        const serviceResults = getServiceResults(serviceId);
        if (serviceResults && serviceResults.originalGridImageBase64) {
            return serviceResults.originalGridImageBase64;
        }
        return null;
    }

    function getServiceResults(serviceId) {
        if (!currentResponse) return null;
        
        switch (serviceId) {
            case 'flux':
                return currentResponse.falAiResults;
            case 'recraft':
                return currentResponse.recraftResults;
            case 'photon':
                return currentResponse.photonResults;
            case 'gpt':
                return currentResponse.gptResults;
            case 'imagen':
                return currentResponse.imagenResults;
            default:
                return null;
        }
    }

    function displayMissingIconsResults(serviceId, data) {
        const resultsContainer = document.getElementById(`missing-results-${serviceId}`);
        
        if (data.newIcons && data.newIcons.length > 0) {
            const iconsGrid = document.createElement('div');
            iconsGrid.className = 'row g-3 mt-2';
            
            // Add a small header
            const headerDiv = document.createElement('div');
            headerDiv.className = 'col-12';
            headerDiv.innerHTML = `
                <div class="alert alert-success alert-sm py-2">
                    <i class="bi bi-check-circle me-2"></i>
                    <strong>New 3x3 grid generated:</strong> ${data.newIcons.length} icons in ${(data.generationTimeMs / 1000).toFixed(1)}s
                </div>
            `;
            iconsGrid.appendChild(headerDiv);
            
            data.newIcons.forEach((icon, index) => {
                const iconDiv = document.createElement('div');
                iconDiv.className = 'col-md-4 col-sm-6';
                iconDiv.innerHTML = `
                    <div class="icon-item fade-in">
                        <img src="data:image/png;base64,${icon.base64Data}" 
                             alt="Generated Missing Icon ${index + 1}" 
                             class="img-fluid">
                        <div class="icon-description">${icon.description || `Missing Icon ${index + 1}`}</div>
                        <div class="service-badge bg-success">New</div>
                    </div>
                `;
                iconsGrid.appendChild(iconDiv);
            });
            
            resultsContainer.innerHTML = '';
            resultsContainer.appendChild(iconsGrid);
            resultsContainer.style.display = 'block';
            
            // Add new icons to current icons for export
            currentIcons = currentIcons.concat(data.newIcons);
        }
    }

    // Initialize with no fields showing
    updateIconDescriptionFields(0);
});
