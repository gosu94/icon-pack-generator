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
    
    let currentIcons = []; // Store current icons for export

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

    // Handle export button click
    exportBtn.addEventListener('click', function() {
        if (currentIcons.length > 0) {
            exportIcons(currentIcons);
        }
    });

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
        // Show loading state
        setUIState('loading');
        
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

        // Make API call
        fetch('/generate', {
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
            if (data.status === 'success') {
                displayResults(data);
            } else {
                showError(data.message || 'Failed to generate icons');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showError('Network error occurred. Please try again.');
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

    function displayResults(data) {
        resultsGrid.innerHTML = '';
        currentIcons = data.icons; // Store all icons for export
        
        // Create container for both service results
        const servicesContainer = document.createElement('div');
        servicesContainer.className = 'services-container';
        
        let hasMultipleSections = false;
        
        // Display FalAI results (including disabled status)
        if (data.falAiResults && (data.falAiResults.icons && data.falAiResults.icons.length > 0 || data.falAiResults.status === 'disabled')) {
            const falAiSection = createServiceSection('Flux-Pro', data.falAiResults, 'fal-ai');
            servicesContainer.appendChild(falAiSection);
            hasMultipleSections = true;
        }
        
        // Display OpenAI results (including disabled status)
        if (data.openAiResults && (data.openAiResults.icons && data.openAiResults.icons.length > 0 || data.openAiResults.status === 'disabled')) {
            if (hasMultipleSections) {
                const separator = document.createElement('div');
                separator.className = 'service-separator';
                separator.innerHTML = '<div class="separator-line"></div>';
                servicesContainer.appendChild(separator);
            }
            const openAiSection = createServiceSection('GPT-Image', data.openAiResults, 'openai');
            servicesContainer.appendChild(openAiSection);
            hasMultipleSections = true;
        }
        
        // Display Recraft results (including disabled status)
        if (data.recraftResults && (data.recraftResults.icons && data.recraftResults.icons.length > 0 || data.recraftResults.status === 'disabled')) {
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
        
        return section;
    }

    function showError(message) {
        document.getElementById('errorMessage').textContent = message;
        setUIState('error');
    }

    function exportIcons(icons) {
        const requestId = Date.now(); // Simple request ID for this export
        
        const exportData = {
            requestId: requestId,
            icons: icons
        };
        
        // Create a blob URL for the export endpoint
        fetch('/export', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(exportData)
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.blob();
        })
        .then(blob => {
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
        })
        .catch(error => {
            console.error('Error exporting icons:', error);
            alert('Failed to export icons. Please try again.');
        });
    }

    // Initialize with no fields showing
    updateIconDescriptionFields(0);
});
