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
            generationsPerService: parseInt(document.getElementById('generationsPerService').value),
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
        
        // Get the number of generations per service from the form
        const generationsPerService = parseInt(document.getElementById('generationsPerService').value) || 1;
        
        let sectionIndex = 0;
        
        // Create sections for each enabled service and each generation
        enabledServicesList.forEach(service => {
            for (let genIndex = 1; genIndex <= generationsPerService; genIndex++) {
                if (sectionIndex > 0) {
                    const separator = document.createElement('div');
                    separator.className = 'service-separator';
                    separator.innerHTML = '<div class="separator-line"></div>';
                    servicesContainer.appendChild(separator);
                }
                
                // Create section name with generation index if multiple generations
                const sectionName = generationsPerService > 1 
                    ? `${service.name} (Generation ${genIndex})`
                    : service.name;
                
                console.log(`Creating streaming section for: ${service.id}-gen${genIndex} with name: ${sectionName}`);
                const section = createStreamingServiceSection(sectionName, service.id, genIndex);
                servicesContainer.appendChild(section);
                sectionIndex++;
            }
        });
    }

    function createStreamingServiceSection(serviceName, serviceId, generationIndex = 1) {
        // Always include generation suffix for consistency with backend progress updates
        const uniqueId = `${serviceId}-gen${generationIndex}`;
        
        console.log(`Creating streaming service section with uniqueId: ${uniqueId} for service: ${serviceName}`);
        
        const section = document.createElement('div');
        section.className = `service-section ${serviceId}-section streaming`;
        section.id = `section-${uniqueId}`;
        
        // Create header with progress indicator
        const header = document.createElement('div');
        header.className = 'service-header';
        header.innerHTML = `
            <h4 class="service-title">
                <span class="service-status-icon" id="status-${uniqueId}">⏳</span> 
                ${serviceName}
                <span class="generation-time" id="time-${uniqueId}"></span>
            </h4>
            <div class="progress mb-2" id="progress-${uniqueId}" style="height: 4px;">
                <div class="progress-bar progress-bar-striped progress-bar-animated" 
                     role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
                </div>
            </div>
            <p class="service-status" id="message-${uniqueId}">Initializing...</p>
        `;
        section.appendChild(header);
        
        // Create placeholder for icons
        const iconsContainer = document.createElement('div');
        iconsContainer.className = 'service-icons-container';
        iconsContainer.id = `icons-${uniqueId}`;
        section.appendChild(iconsContainer);
        
        console.log(`Created elements with IDs: status-${uniqueId}, progress-${uniqueId}, message-${uniqueId}, icons-${uniqueId}`);
        
        return section;
    }

    function handleServiceUpdate(update) {
        const serviceId = update.serviceName;
        console.log('Handling service update for:', serviceId);
        
        // The serviceId from backend includes generation suffix (e.g., "flux-gen1", "flux-gen2")
        // Our frontend elements use the same naming convention
        const statusIcon = document.getElementById(`status-${serviceId}`);
        const progressBar = document.querySelector(`#progress-${serviceId} .progress-bar`);
        const messageElement = document.getElementById(`message-${serviceId}`);
        const timeElement = document.getElementById(`time-${serviceId}`);
        const iconsContainer = document.getElementById(`icons-${serviceId}`);
        
        if (!statusIcon || !progressBar || !messageElement) {
            console.warn('Service UI elements not found for:', serviceId);
            console.log('Available elements:', {
                statusIcon: !!statusIcon,
                progressBar: !!progressBar,
                messageElement: !!messageElement,
                timeElement: !!timeElement,
                iconsContainer: !!iconsContainer
            });
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
                    // Extract base service name for display (remove generation suffix if present)
                    const baseServiceId = serviceId.replace(/-gen\d+$/, '');
                    displayServiceIcons(serviceId, update.icons, getServiceDisplayName(baseServiceId));
                    
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
<!--                    <div class="icon-description">${icon.description || `Icon ${index + 1}`}</div>-->
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
        
        // Group streaming results by service (handling multiple generations)
        const groupedResults = {
            falAiResults: [],
            recraftResults: [],
            photonResults: [],
            gptResults: [],
            imagenResults: []
        };
        
        // Group results by base service name
        Object.entries(streamingResults).forEach(([serviceKey, result]) => {
            const baseServiceId = serviceKey.replace(/-gen\d+$/, '');
            switch (baseServiceId) {
                case 'flux':
                    groupedResults.falAiResults.push(result);
                    break;
                case 'recraft':
                    groupedResults.recraftResults.push(result);
                    break;
                case 'photon':
                    groupedResults.photonResults.push(result);
                    break;
                case 'gpt':
                    groupedResults.gptResults.push(result);
                    break;
                case 'imagen':
                    groupedResults.imagenResults.push(result);
                    break;
            }
        });
        
        currentResponse = {
            icons: allIcons,
            ...groupedResults
        };
        
        // Show export button and enable generate button
        setUIState('results');
        
        // Add "Generate More With Same Style" sections for successful services
        Object.keys(streamingResults).forEach(serviceId => {
            const result = streamingResults[serviceId];
            if (result && result.status === 'success') {
                const baseServiceId = serviceId.replace(/-gen\d+$/, '');
                const serviceName = getServiceDisplayName(baseServiceId);
                const section = document.getElementById(`section-${serviceId}`);
                if (section) {
                    const moreIconsSection = createGenerateMoreSection(baseServiceId, serviceName);
                    section.appendChild(moreIconsSection);
                }
            }
        });
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
        
        // Create container for all service results (including multiple generations)
        const servicesContainer = document.createElement('div');
        servicesContainer.className = 'services-container';
        
        let hasMultipleSections = false;
        
        // Display all results, handling both single and multiple generations per service
        const services = [
            { results: data.falAiResults, name: 'Flux-Pro', id: 'flux' },
            { results: data.recraftResults, name: 'Recraft V3', id: 'recraft' },
            { results: data.photonResults, name: 'Luma Photon', id: 'photon' },
            { results: data.gptResults, name: 'GPT Image', id: 'gpt' },
            { results: data.imagenResults, name: 'Imagen 4', id: 'imagen' }
        ];
        
        services.forEach(service => {
            if (service.results && service.results.length > 0) {
                service.results.forEach((serviceResult, index) => {
                    // Only display if not disabled and has icons
                    if (serviceResult.status !== 'disabled' && serviceResult.icons && serviceResult.icons.length > 0) {
                        if (hasMultipleSections) {
                            const separator = document.createElement('div');
                            separator.className = 'service-separator';
                            separator.innerHTML = '<div class="separator-line"></div>';
                            servicesContainer.appendChild(separator);
                        }
                        
                        // Create section name with generation index if multiple generations
                        const sectionName = service.results.length > 1 
                            ? `${service.name} (Generation ${serviceResult.generationIndex || index + 1})`
                            : service.name;
                        
                        const section = createServiceSection(sectionName, serviceResult, service.id);
                        section.id = `section-${service.id}-gen${serviceResult.generationIndex || index + 1}`;
                        servicesContainer.appendChild(section);
                        hasMultipleSections = true;
                    }
                });
            }
        });
        
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
        
        // Add "Generate More With Same Style" section if service was successful
        if (serviceResults.status === 'success') {
            const moreIconsSection = createGenerateMoreSection(serviceId, serviceName);
            section.appendChild(moreIconsSection);
        }
        
        return section;
    }

    function createGenerateMoreSection(serviceId, serviceName) {
        const moreSection = document.createElement('div');
        moreSection.className = 'generate-more-section mt-4';
        moreSection.id = `more-${serviceId}`;
        
        moreSection.innerHTML = `
            <div class="generate-more-header">
                <h6 class="text-muted mb-3">
                    <i class="bi bi-plus-circle me-2"></i>Generate More With Same Style
                </h6>
                <p class="small text-muted mb-3">
                    Create 9 more icons using ${serviceName} with the same style as above:
                </p>
            </div>
            <div class="generate-more-actions mb-3">
                <button class="btn btn-outline-primary btn-sm" 
                        onclick="showMoreIconsForm('${serviceId}')"
                        id="show-more-form-${serviceId}">
                    <i class="bi bi-magic me-2"></i>Generate More Icons
                </button>
            </div>
            <div class="more-icons-form mt-3" id="more-form-${serviceId}" style="display: none;">
                <h6 class="small text-muted mb-3">Describe 9 new icons (leave empty for creative variations):</h6>
                <div class="icon-descriptions-grid mb-3">
                    ${Array.from({length: 9}, (_, i) => `
                        <div class="icon-description-field">
                            <input type="text" 
                                   class="form-control form-control-sm" 
                                   placeholder="Icon ${i + 1}"
                                   id="more-${serviceId}-desc-${i}">
                        </div>
                    `).join('')}
                </div>
                <div class="more-icons-actions">
                    <button class="btn btn-primary btn-sm" 
                            onclick="generateMoreIcons('${serviceId}', '${serviceName}')"
                            id="generate-more-${serviceId}">
                        <i class="bi bi-magic me-2"></i>Generate 9 More Icons
                    </button>
                    <button class="btn btn-outline-secondary btn-sm ms-2" 
                            onclick="hideMoreIconsForm('${serviceId}')">
                        Cancel
                    </button>
                </div>
            </div>
            <div class="more-icons-results mt-3" id="more-results-${serviceId}" style="display: none;">
                <!-- New icons will appear here -->
                <p class="text-muted small">Results will appear here...</p>
            </div>
        `;
        
        return moreSection;
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

    // Generate More Icons Functions
    window.showMoreIconsForm = function(serviceId) {
        const form = document.getElementById(`more-form-${serviceId}`);
        const showBtn = document.getElementById(`show-more-form-${serviceId}`);
        
        form.style.display = 'block';
        showBtn.style.display = 'none';
        
        // Focus on the first input field
        const firstInput = document.getElementById(`more-${serviceId}-desc-0`);
        if (firstInput) {
            firstInput.focus();
        }
    };

    window.hideMoreIconsForm = function(serviceId) {
        const form = document.getElementById(`more-form-${serviceId}`);
        const showBtn = document.getElementById(`show-more-form-${serviceId}`);
        
        form.style.display = 'none';
        showBtn.style.display = 'block';
        
        // Clear all input fields
        for (let i = 0; i < 9; i++) {
            const input = document.getElementById(`more-${serviceId}-desc-${i}`);
            if (input) {
                input.value = '';
            }
        }
    };

    window.generateMoreIcons = function(serviceId, serviceName) {
        const descriptions = getMoreIconDescriptions(serviceId);
        
        const generateBtn = document.getElementById(`generate-more-${serviceId}`);
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
        const moreIconsRequest = {
            originalRequestId: currentResponse.requestId,
            serviceName: serviceId,
            originalImageBase64: originalImageBase64,
            generalDescription: currentRequest.generalDescription,
            iconDescriptions: descriptions,
            iconCount: 9,
            seed: currentResponse.seed // Use the same seed for consistency
        };
        
        console.log(`Generating more icons for ${serviceName} with seed: ${currentResponse.seed}`);
        
        // Make API call
        fetch('/generate-more', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(moreIconsRequest)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log('Generate more response:', data);
            if (data.status === 'success') {
                console.log('Success - calling displayMoreIconsResults with serviceId:', serviceId, 'data:', data);
                displayMoreIconsResults(serviceId, data);
                showSuccessToast(`Generated new 3x3 grid (${data.newIcons.length} icons) with ${serviceName}!`);
                hideMoreIconsForm(serviceId);
            } else {
                console.error('Generate more failed:', data.message);
                showErrorToast(data.message || 'Failed to generate more icons');
            }
        })
        .catch(error => {
            console.error('Error generating more icons:', error);
            showErrorToast('Failed to generate more icons. Please try again.');
        })
        .finally(() => {
            generateBtn.disabled = false;
            generateBtn.innerHTML = originalText;
        });
    };

    function getMoreIconDescriptions(serviceId) {
        const descriptions = [];
        for (let i = 0; i < 9; i++) {
            const input = document.getElementById(`more-${serviceId}-desc-${i}`);
            if (input) {
                descriptions.push(input.value.trim());
            }
        }
        return descriptions;
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

    function displayMoreIconsResults(serviceId, data) {
        console.log('displayMoreIconsResults called with serviceId:', serviceId, 'data:', data);
        const resultsContainer = document.getElementById(`more-results-${serviceId}`);
        console.log('Results container found:', resultsContainer);
        
        if (!resultsContainer) {
            console.error('Results container not found for serviceId:', serviceId);
            return;
        }
        
        if (data.newIcons && data.newIcons.length > 0) {
            console.log('Data has newIcons:', data.newIcons.length);
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
                             alt="Generated More Icon ${index + 1}" 
                             class="img-fluid">
                        <div class="icon-description">${icon.description || `More Icon ${index + 1}`}</div>
                        <div class="service-badge bg-success">New</div>
                    </div>
                `;
                iconsGrid.appendChild(iconDiv);
            });
            
            resultsContainer.innerHTML = '';
            resultsContainer.appendChild(iconsGrid);
            resultsContainer.style.display = 'block';
            console.log('Results displayed successfully. Container now has children:', resultsContainer.children.length);
            
            // Add new icons to current icons for export
            currentIcons = currentIcons.concat(data.newIcons);
        } else {
            console.log('No newIcons in data or empty array. Data structure:', Object.keys(data));
        }
    }

    // Initialize with no fields showing
    updateIconDescriptionFields(0);
});
