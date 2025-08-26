'use client';

import { useState, useRef, useEffect } from 'react';

interface Icon {
    base64Data: string;
    description?: string;
}

interface ServiceResult {
    icons: Icon[];
    originalGridImageBase64?: string;
    generationTimeMs: number;
    status: string;
    message: string;
    generationIndex: number;
    seed?: string;
    progress?: number;
}

interface StreamingResults {
    [key: string]: ServiceResult;
}

interface GenerationResponse {
    icons: Icon[];
    requestId: string;
    falAiResults?: ServiceResult[];
    recraftResults?: ServiceResult[];
    photonResults?: ServiceResult[];
    gptResults?: ServiceResult[];
    imagenResults?: ServiceResult[];
}

type UIState = 'initial' | 'streaming' | 'error' | 'results';

export default function Page() {
    // Form state
    const [inputType, setInputType] = useState('text');
    const [iconCount, setIconCount] = useState('9');
    const [generationsPerService] = useState('2'); // Fixed to 2 generations (original + variation)
    const [generalDescription, setGeneralDescription] = useState('');
    const [individualDescriptions, setIndividualDescriptions] = useState<string[]>([]);
    const [referenceImage, setReferenceImage] = useState<File | null>(null);
    const [imagePreview, setImagePreview] = useState<string>('');
    
    // UI state
    const [uiState, setUiState] = useState<UIState>('initial');
    const [errorMessage, setErrorMessage] = useState('');
    const [isGenerating, setIsGenerating] = useState(false);
    
    // Generation data
    const [currentIcons, setCurrentIcons] = useState<Icon[]>([]);
    const [currentRequest, setCurrentRequest] = useState<any>(null);
    const [currentResponse, setCurrentResponse] = useState<GenerationResponse | null>(null);
    const [streamingResults, setStreamingResults] = useState<StreamingResults>({});
    
    // Modal state
    const [showExportModal, setShowExportModal] = useState(false);
    const [showProgressModal, setShowProgressModal] = useState(false);
    const [exportContext, setExportContext] = useState<any>(null);
    const [removeBackground, setRemoveBackground] = useState(true);
    const [outputFormat, setOutputFormat] = useState('png');
    const [exportProgress, setExportProgress] = useState({ step: 1, message: '', percent: 25 });
    
    // Generate more state
    const [moreIconsVisible, setMoreIconsVisible] = useState<{[key: string]: boolean}>({});
    const [moreIconsDescriptions, setMoreIconsDescriptions] = useState<{[key: string]: string[]}>({});
    
    // Animation state
    const [animatingIcons, setAnimatingIcons] = useState<{[key: string]: number}>({});
    const [animationTimers, setAnimationTimers] = useState<{[key: string]: NodeJS.Timeout[]}>({});
    
    // Progress timers
    const progressTimersRef = useRef<{[key: string]: NodeJS.Timeout}>({});
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Update individual descriptions when icon count changes
    useEffect(() => {
        const count = parseInt(iconCount);
        setIndividualDescriptions(new Array(count).fill(''));
    }, [iconCount]);

    // Cleanup timers on unmount
    useEffect(() => {
        return () => {
            Object.values(progressTimersRef.current).forEach(timer => clearInterval(timer));
            // Cleanup animation timers
            Object.values(animationTimers).forEach(timers => {
                timers.forEach(timer => clearTimeout(timer));
            });
        };
    }, [animationTimers]);

    // Animation functions
    const startIconAnimation = (serviceId: string, iconCount: number) => {
        // Clear any existing animation for this service
        if (animationTimers[serviceId]) {
            animationTimers[serviceId].forEach(timer => clearTimeout(timer));
        }

        // Reset animation state
        setAnimatingIcons(prev => ({ ...prev, [serviceId]: 0 }));

        // Create staggered animation timers
        const timers: NodeJS.Timeout[] = [];
        
        for (let i = 0; i < iconCount; i++) {
            const timer = setTimeout(() => {
                setAnimatingIcons(prev => ({ ...prev, [serviceId]: i + 1 }));
            }, i * 150); // 150ms delay between each icon
            timers.push(timer);
        }

        setAnimationTimers(prev => ({ ...prev, [serviceId]: timers }));
    };

    const clearIconAnimation = (serviceId: string) => {
        if (animationTimers[serviceId]) {
            animationTimers[serviceId].forEach(timer => clearTimeout(timer));
            setAnimationTimers(prev => {
                const newTimers = { ...prev };
                delete newTimers[serviceId];
                return newTimers;
            });
        }
        setAnimatingIcons(prev => {
            const newAnimating = { ...prev };
            delete newAnimating[serviceId];
            return newAnimating;
        });
    };

    const getIconAnimationClass = (serviceId: string, iconIndex: number) => {
        const visibleCount = animatingIcons[serviceId] || 0;
        const isVisible = iconIndex < visibleCount;
        
        return isVisible 
            ? "opacity-100 scale-100 transition-all duration-500 ease-out" 
            : "opacity-0 scale-75 transition-all duration-500 ease-out";
    };

    // Utility functions
    const fileToBase64 = (file: File): Promise<string> => {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = () => {
                const base64 = (reader.result as string).split(',')[1];
                resolve(base64);
            };
            reader.onerror = error => reject(error);
        });
    };

    const formatFileSize = (bytes: number): string => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const getServiceDisplayName = (serviceId: string): string => {
        const serviceNames: {[key: string]: string} = {
            'flux': 'Flux-Pro',
            'recraft': 'Recraft V3',
            'photon': 'Luma Photon',
            'gpt': 'GPT Image',
            'imagen': 'Imagen 4'
        };
        return serviceNames[serviceId] || serviceId;
    };

    // Progress timer functions
    const startProgressTimer = (serviceId: string) => {
        if (progressTimersRef.current[serviceId]) {
            clearInterval(progressTimersRef.current[serviceId]);
        }
        
        const iconCountNum = parseInt(iconCount) || 9;
        const duration = iconCountNum === 18 ? 90000 : 30000; // 90s for 18 icons, 30s for 9 icons
        
        let currentProgress = 0;
        const increment = 100 / (duration / 100); // Update every 100ms
        
        progressTimersRef.current[serviceId] = setInterval(() => {
            currentProgress += increment;
            if (currentProgress >= 100) {
                currentProgress = 100;
                clearInterval(progressTimersRef.current[serviceId]);
                delete progressTimersRef.current[serviceId];
            }
            
            // Update progress in streaming results
            setStreamingResults(prev => ({
                ...prev,
                [serviceId]: {
                    ...prev[serviceId],
                    progress: Math.min(currentProgress, 100)
                }
            }));
        }, 100);
    };

    const clearProgressTimer = (serviceId: string) => {
        if (progressTimersRef.current[serviceId]) {
            clearInterval(progressTimersRef.current[serviceId]);
            delete progressTimersRef.current[serviceId];
        }
    };

    // Image handling
    const handleImageSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) {
            setReferenceImage(null);
            setImagePreview('');
            return;
        }

        // Validate file type
        if (!file.type.startsWith('image/')) {
            alert('Please select a valid image file.');
            return;
        }

        // Validate file size (limit to 10MB)
        if (file.size > 10 * 1024 * 1024) {
            alert('File size must be less than 10MB.');
            return;
        }

        setReferenceImage(file);
        
        // Show preview
        const reader = new FileReader();
        reader.onload = (e) => {
            setImagePreview(e.target?.result as string);
        };
        reader.readAsDataURL(file);
    };

    const removeImage = () => {
        setReferenceImage(null);
        setImagePreview('');
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    // Form validation
    const validateForm = (): boolean => {
        if (inputType === 'text' && !generalDescription.trim()) {
            setErrorMessage('Please provide a general description.');
            return false;
        }

        if (inputType === 'image' && !referenceImage) {
            setErrorMessage('Please select a reference image.');
            return false;
        }

        if (!iconCount) {
            setErrorMessage('Please select the number of icons.');
            return false;
        }

        return true;
    };

    // Main generation function
    const generateIcons = async () => {
        if (!validateForm()) {
            setUiState('error');
            return;
        }

        setIsGenerating(true);
        setUiState('streaming');
        setStreamingResults({});
        
        // Clear any existing timers
        Object.keys(progressTimersRef.current).forEach(serviceId => {
            clearProgressTimer(serviceId);
        });
        
        // Clear any existing animations
        Object.keys(animationTimers).forEach(serviceId => {
            clearIconAnimation(serviceId);
        });

        // Collect form data
        const formData: any = {
            iconCount: parseInt(iconCount),
            generationsPerService: parseInt(generationsPerService),
            individualDescriptions: individualDescriptions.filter(desc => desc.trim())
        };

        // Handle input type
        if (inputType === 'text') {
            formData.generalDescription = generalDescription.trim();
        } else if (inputType === 'image' && referenceImage) {
            try {
                formData.referenceImageBase64 = await fileToBase64(referenceImage);
            } catch (error) {
                console.error('Error converting image to base64:', error);
                setErrorMessage('Failed to process reference image');
                setUiState('error');
                setIsGenerating(false);
                return;
            }
        }

        // Store current request for missing icons feature
        setCurrentRequest({ ...formData });

        try {
            // Step 1: Start the generation process and get request ID
            const response = await fetch('/generate-stream', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(formData)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            const requestId = data.requestId;
            const enabledServices = data.enabledServices;
            
            console.log('Generation started with request ID:', requestId);
            console.log('Enabled services:', enabledServices);

            // Initialize streaming results for enabled services
            initializeStreamingResults(enabledServices);
            
            // Step 2: Connect to SSE stream
            const eventSource = new EventSource(`/stream/${requestId}`);
            
            // Handle service updates
            eventSource.addEventListener('service_update', (event) => {
                try {
                    const update = JSON.parse(event.data);
                    handleServiceUpdate(update);
                } catch (error) {
                    console.error('Error parsing service update:', error);
                }
            });

            // Handle generation completion
            eventSource.addEventListener('generation_complete', (event) => {
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
            eventSource.addEventListener('generation_error', (event) => {
                try {
                    const update = JSON.parse(event.data);
                    setErrorMessage(update.message || 'Generation failed');
                    setUiState('error');
                    setIsGenerating(false);
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing error update:', error);
                    setErrorMessage('Generation failed with unknown error');
                    setUiState('error');
                    setIsGenerating(false);
                    eventSource.close();
                }
            });

            // Handle EventSource errors
            eventSource.onerror = (error) => {
                console.error('EventSource error:', error);
                setErrorMessage('Connection error. Please try again.');
                setUiState('error');
                setIsGenerating(false);
                eventSource.close();
            };

        } catch (error) {
            console.error('Error starting generation:', error);
            setErrorMessage('Failed to start generation. Please try again.');
            setUiState('error');
            setIsGenerating(false);
        }
    };

    // Streaming event handlers
    const initializeStreamingResults = (enabledServices: {[key: string]: boolean}) => {
        const newResults: StreamingResults = {};
        
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
        const generationsNum = parseInt(generationsPerService) || 1;
        
        // Create entries for each enabled service and each generation
        enabledServicesList.forEach(service => {
            for (let genIndex = 1; genIndex <= generationsNum; genIndex++) {
                const uniqueId = `${service.id}-gen${genIndex}`;
                newResults[uniqueId] = {
                    icons: [],
                    generationTimeMs: 0,
                    status: 'started',
                    message: 'Initializing...',
                    generationIndex: genIndex,
                    progress: 0
                };
                // Start progress timer for each service
                setTimeout(() => startProgressTimer(uniqueId), 100);
            }
        });
        
        setStreamingResults(newResults);
    };

    const handleServiceUpdate = (update: any) => {
        const serviceId = update.serviceName;
        console.log('Handling service update for:', serviceId, 'Status:', update.status);
        
        setStreamingResults(prev => {
            const current = prev[serviceId] || {};
            const updated = {
                ...current,
                status: update.status,
                message: update.message || current.message,
                generationTimeMs: update.generationTimeMs || current.generationTimeMs
            };

            switch (update.status) {
                case 'started':
                    // Timer already started when initialized
                    break;
                    
                case 'success':
                    clearProgressTimer(serviceId);
                    updated.progress = 100;
                    updated.icons = update.icons || [];
                    updated.originalGridImageBase64 = update.originalGridImageBase64;
                    updated.generationIndex = update.generationIndex;
                    
                    // Start icon animation
                    if (updated.icons.length > 0) {
                        setTimeout(() => {
                            startIconAnimation(serviceId, updated.icons.length);
                        }, 300); // Small delay to ensure DOM is updated
                    }
                    break;
                    
                case 'error':
                    clearProgressTimer(serviceId);
                    updated.progress = 100;
                    break;
            }

            return {
                ...prev,
                [serviceId]: updated
            };
        });
    };

    const handleGenerationComplete = (update: any) => {
        console.log('Generation completed:', update);
        
        // Clear all remaining progress timers
        Object.keys(progressTimersRef.current).forEach(serviceId => {
            clearProgressTimer(serviceId);
        });
        
        // Combine all successful results for export
        let allIcons: Icon[] = [];
        Object.values(streamingResults).forEach(result => {
            if (result.icons) {
                allIcons = allIcons.concat(result.icons);
            }
        });
        
        setCurrentIcons(allIcons);
        
        // Group streaming results by service
        const groupedResults = {
            falAiResults: [] as ServiceResult[],
            recraftResults: [] as ServiceResult[],
            photonResults: [] as ServiceResult[],
            gptResults: [] as ServiceResult[],
            imagenResults: [] as ServiceResult[]
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
        
        setCurrentResponse({
            icons: allIcons,
            ...groupedResults,
            requestId: update.requestId
        });
        
        setUiState('results');
        setIsGenerating(false);
    };

    // Export functionality
    const exportGeneration = (requestId: string, serviceName: string, generationIndex: number) => {
        setExportContext({ requestId, serviceName, generationIndex });
        setShowExportModal(true);
    };

    const confirmExport = () => {
        if (exportContext) {
            const { requestId, serviceName, generationIndex } = exportContext;
            const fileName = `icon-pack-${requestId}-${serviceName}-gen${generationIndex}.zip`;

            const exportData = {
                requestId: requestId,
                serviceName: serviceName,
                generationIndex: generationIndex,
                removeBackground: removeBackground,
                outputFormat: outputFormat
            };

            setShowExportModal(false);
            downloadZip(exportData, fileName);
        }
    };

    const downloadZip = async (exportData: any, fileName: string) => {
        setShowProgressModal(true);
        setExportProgress({ step: 1, message: 'Preparing export request...', percent: 25 });

        try {
            setTimeout(() => {
                setExportProgress({ 
                    step: 2, 
                    message: exportData.removeBackground ? 'Processing icons and removing backgrounds...' : 'Processing icons...', 
                    percent: 50 
                });
            }, 500);

            const response = await fetch('/export', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(exportData)
            });

            setExportProgress({ step: 3, message: 'Creating ZIP file...', percent: 75 });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const blob = await response.blob();
            setExportProgress({ step: 4, message: 'Finalizing download...', percent: 100 });

            // Small delay to show completion
            setTimeout(() => {
                setShowProgressModal(false);

                // Create download link
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;
                a.download = fileName;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);

                // Show success toast
                showSuccessToast('Icon pack downloaded successfully!');
            }, 1000);

        } catch (error) {
            console.error('Error exporting icons:', error);
            setShowProgressModal(false);
            showErrorToast('Failed to export icons. Please try again.');
        }
    };

    // Toast notifications
    const showSuccessToast = (message: string) => {
        // In a real app, you'd use a proper toast library
        alert(message);
    };

    const showErrorToast = (message: string) => {
        // In a real app, you'd use a proper toast library
        alert(message);
    };

    // Generate more icons functionality
    const showMoreIconsForm = (uniqueId: string) => {
        setMoreIconsVisible(prev => ({ ...prev, [uniqueId]: true }));
        setMoreIconsDescriptions(prev => ({ ...prev, [uniqueId]: new Array(9).fill('') }));
    };

    const hideMoreIconsForm = (uniqueId: string) => {
        setMoreIconsVisible(prev => ({ ...prev, [uniqueId]: false }));
        setMoreIconsDescriptions(prev => ({ ...prev, [uniqueId]: new Array(9).fill('') }));
    };

    const generateMoreIcons = async (serviceId: string, serviceName: string, generationIndex: number) => {
        const uniqueId = `${serviceId}-gen${generationIndex}`;
        const descriptions = moreIconsDescriptions[uniqueId] || [];
        
        // Get the original image for this service
        const serviceResults = getServiceResults(serviceId, generationIndex);
        if (!serviceResults?.originalGridImageBase64) {
            showErrorToast('Original image not found for this service');
            return;
        }
        
        const moreIconsRequest = {
            originalRequestId: currentResponse?.requestId,
            serviceName: serviceId,
            originalImageBase64: serviceResults.originalGridImageBase64,
            generalDescription: currentRequest?.generalDescription,
            iconDescriptions: descriptions,
            iconCount: 9,
            seed: serviceResults.seed
        };
        
        try {
            const response = await fetch('/generate-more', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(moreIconsRequest)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            
            if (data.status === 'success') {
                // Add new icons to current icons and streaming results
                setCurrentIcons(prev => prev.concat(data.newIcons));
                const previousIconCount = streamingResults[uniqueId]?.icons?.length || 0;
                
                setStreamingResults(prev => ({
                    ...prev,
                    [uniqueId]: {
                        ...prev[uniqueId],
                        icons: [...(prev[uniqueId]?.icons || []), ...data.newIcons]
                    }
                }));
                
                // Animate the new icons starting from the previous count
                setTimeout(() => {
                    const totalIcons = previousIconCount + data.newIcons.length;
                    setAnimatingIcons(prev => ({ ...prev, [uniqueId]: previousIconCount }));
                    
                    // Animate new icons one by one
                    for (let i = 0; i < data.newIcons.length; i++) {
                        setTimeout(() => {
                            setAnimatingIcons(prev => ({ 
                                ...prev, 
                                [uniqueId]: previousIconCount + i + 1 
                            }));
                        }, i * 150);
                    }
                }, 200);
                
                showSuccessToast(`Generated new 3x3 grid (${data.newIcons.length} icons) with ${serviceName}!`);
                hideMoreIconsForm(uniqueId);
            } else {
                showErrorToast(data.message || 'Failed to generate more icons');
            }
        } catch (error) {
            console.error('Error generating more icons:', error);
            showErrorToast('Failed to generate more icons. Please try again.');
        }
    };

    const getServiceResults = (serviceId: string, generationIndex: number): ServiceResult | null => {
        if (!currentResponse) return null;
        
        let resultsArray: ServiceResult[] | undefined;
        switch (serviceId) {
            case 'flux':
                resultsArray = currentResponse.falAiResults;
                break;
            case 'recraft':
                resultsArray = currentResponse.recraftResults;
                break;
            case 'photon':
                resultsArray = currentResponse.photonResults;
                break;
            case 'gpt':
                resultsArray = currentResponse.gptResults;
                break;
            case 'imagen':
                resultsArray = currentResponse.imagenResults;
                break;
            default:
                return null;
        }
        
        if (resultsArray && resultsArray.length > 0) {
            return resultsArray.find(r => r.generationIndex === generationIndex) || null;
        }
        
        return null;
    };

    // Utility functions for two-pane layout
    const getGeneration1Results = () => {
        return Object.entries(streamingResults)
            .filter(([serviceId, result]) => result.generationIndex === 1)
            .map(([serviceId, result]) => ({ serviceId, ...result }));
    };

    const getGeneration2Results = () => {
        return Object.entries(streamingResults)
            .filter(([serviceId, result]) => result.generationIndex === 2)
            .map(([serviceId, result]) => ({ serviceId, ...result }));
    };

    const renderGenerationResults = (generationNumber: number) => {
        const results = generationNumber === 1 ? getGeneration1Results() : getGeneration2Results();
        
        return results.map((result, index) => {
            const baseServiceId = result.serviceId.replace(/-gen\d+$/, '');
            const serviceName = getServiceDisplayName(baseServiceId);

            return (
                <div key={result.serviceId}>
                    {index > 0 && <div className="border-t border-gray-200 my-6" />}
                    
                    {/* Service Header */}
                    <div className="mb-4">
                        <div className="flex items-center justify-between">
                            <h3 className="text-lg font-medium text-gray-900 flex items-center">
                                <span className="mr-2">
                                    {result.status === 'success' ? '✅' : 
                                     result.status === 'error' ? '❌' : '🔄'}
                                </span>
                                {serviceName}
                                {result.generationTimeMs > 0 && (
                                    <span className="text-sm text-gray-500 ml-2">
                                        ({(result.generationTimeMs / 1000).toFixed(1)}s)
                                    </span>
                                )}
                            </h3>
                            {result.status === 'success' && result.icons.length > 0 && (
                                <button
                                    onClick={() => exportGeneration(currentResponse?.requestId || '', baseServiceId, result.generationIndex)}
                                    className="px-3 py-1 bg-blue-50 text-blue-600 rounded text-sm hover:bg-blue-100"
                                >
                                    Export
                                </button>
                            )}
                        </div>
                        
                        {/* Progress Bar */}
                        {uiState === 'streaming' && result.status !== 'success' && result.status !== 'error' && (
                            <div className="mt-2">
                                <div className="w-full bg-gray-200 rounded-full h-1">
                                    <div 
                                        className="bg-blue-600 h-1 rounded-full transition-all duration-300"
                                        style={{ width: `${result.progress || 0}%` }}
                                    />
                                </div>
                            </div>
                        )}
                        
                        <p className="text-sm text-gray-600 mt-1">{result.message}</p>
                    </div>

                    {/* Icons Grid */}
                    {result.icons && result.icons.length > 0 && (
                        <div className="grid grid-cols-3 gap-4">
                            {result.icons.map((icon, iconIndex) => (
                                <div 
                                    key={iconIndex} 
                                    className={`relative group transform ${getIconAnimationClass(result.serviceId, iconIndex)}`}
                                >
                                    <img 
                                        src={`data:image/png;base64,${icon.base64Data}`}
                                        alt={`Generated Icon ${iconIndex + 1}`}
                                        className="w-full h-auto rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200"
                                    />
                                    <div className={`absolute top-2 right-2 bg-gray-900 bg-opacity-75 text-white text-xs px-2 py-1 rounded transition-opacity duration-300 ${
                                        animatingIcons[result.serviceId] > iconIndex ? 'opacity-100' : 'opacity-0'
                                    }`}>
                                        {serviceName}
                                    </div>
                                    
                                    {/* Cool entrance effect overlay */}
                                    <div className={`absolute inset-0 bg-gradient-to-r from-blue-400 to-purple-500 rounded-lg transition-opacity duration-700 ${
                                        animatingIcons[result.serviceId] > iconIndex ? 'opacity-0' : 'opacity-20'
                                    }`} />
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Generate More Section */}
                    {result.status === 'success' && uiState === 'results' && (
                        <div className="mt-6 p-4 bg-gray-50 rounded-lg">
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-medium text-gray-900">
                                    Generate More With Same Style
                                </h4>
                                {!moreIconsVisible[result.serviceId] && (
                                    <button
                                        onClick={() => showMoreIconsForm(result.serviceId)}
                                        className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                                    >
                                        Generate More Icons
                                    </button>
                                )}
                            </div>
                            
                            {moreIconsVisible[result.serviceId] && (
                                <div className="space-y-3">
                                    <p className="text-xs text-gray-600">
                                        Describe 9 new icons (leave empty for creative variations):
                                    </p>
                                    <div className="grid grid-cols-3 gap-2">
                                        {Array.from({ length: 9 }, (_, i) => (
                                            <input
                                                key={i}
                                                type="text"
                                                placeholder={`Icon ${i + 1}`}
                                                value={moreIconsDescriptions[result.serviceId]?.[i] || ''}
                                                onChange={(e) => {
                                                    const newDescriptions = [...(moreIconsDescriptions[result.serviceId] || new Array(9).fill(''))];
                                                    newDescriptions[i] = e.target.value;
                                                    setMoreIconsDescriptions(prev => ({
                                                        ...prev,
                                                        [result.serviceId]: newDescriptions
                                                    }));
                                                }}
                                                className="w-full px-2 py-1 border border-gray-200 rounded text-xs"
                                            />
                                        ))}
                                    </div>
                                    <div className="flex space-x-2">
                                        <button
                                            onClick={() => generateMoreIcons(baseServiceId, serviceName, result.generationIndex)}
                                            className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                                        >
                                            Generate 9 More Icons
                                        </button>
                                        <button
                                            onClick={() => hideMoreIconsForm(result.serviceId)}
                                            className="px-3 py-1 bg-gray-200 text-gray-700 rounded text-sm hover:bg-gray-300"
                                        >
                                            Cancel
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            );
        });
    };

    const exportAllGeneration1 = () => {
        const gen1Results = getGeneration1Results();
        if (gen1Results.length > 0) {
            // Export the first available generation 1 result (can be enhanced to export all)
            const firstResult = gen1Results[0];
            const baseServiceId = firstResult.serviceId.replace(/-gen\d+$/, '');
            exportGeneration(currentResponse?.requestId || '', baseServiceId, 1);
        }
    };

    const exportAllGeneration2 = () => {
        const gen2Results = getGeneration2Results();
        if (gen2Results.length > 0) {
            // Export the first available generation 2 result (can be enhanced to export all)
            const firstResult = gen2Results[0];
            const baseServiceId = firstResult.serviceId.replace(/-gen\d+$/, '');
            exportGeneration(currentResponse?.requestId || '', baseServiceId, 2);
        }
    };

    const renderIconFields = () => {
        const count = parseInt(iconCount);
        const fields = [];

        for (let i = 0; i < count; i++) {
            fields.push(
                <input
                    key={i}
                    type="text"
                    placeholder={`Icon ${i + 1} description (optional)`}
                    value={individualDescriptions[i] || ''}
                    onChange={(e) => {
                        const newDescriptions = [...individualDescriptions];
                        newDescriptions[i] = e.target.value;
                        setIndividualDescriptions(newDescriptions);
                    }}
                    className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-gray-300"
                />,
            );
        }

        return (
            <div className="grid grid-cols-3 gap-3">
                {fields}
            </div>
        );
    };

    return (
        <div className="min-h-screen bg-white" data-oid="b5sohb5">
            {/* Navigation */}
            <nav className="border-b border-gray-200 px-6 py-4" data-oid="zknb.vl">
                <div className="flex items-center justify-between" data-oid="n-8.7xb">
                    <div className="flex items-center space-x-3" data-oid="cfnkurh">
                        <div
                            className="w-8 h-8 bg-black rounded-lg flex items-center justify-center"
                            data-oid="af4jp5e"
                        >
                            <svg
                                className="w-5 h-5 text-white"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="o.r04n7"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z"
                                    clipRule="evenodd"
                                    data-oid="9mtht3s"
                                />
                            </svg>
                        </div>
                        <span className="text-xl font-medium text-black" data-oid="p8a78sv">
                            Icon Pack Generator
                        </span>
                    </div>

                    <div className="flex items-center space-x-4" data-oid="yorvl9x">
                        {/* Dashboard */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="7vggw9v">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="4akwqht"
                            >
                                <path
                                    d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z"
                                    data-oid="25z35-_"
                                />
                            </svg>
                        </button>

                        {/* Gallery */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="jmagbl2">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="dhm6h6n"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z"
                                    clipRule="evenodd"
                                    data-oid="bsrvjm2"
                                />
                            </svg>
                        </button>

                        {/* Store */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="5hv4en:">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="87k07jv"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M10 2L3 7v11a2 2 0 002 2h10a2 2 0 002-2V7l-7-5zM8 15v-3a1 1 0 011-1h2a1 1 0 011 1v3H8z"
                                    clipRule="evenodd"
                                    data-oid="4qc_056"
                                />
                            </svg>
                        </button>

                        {/* Feedback */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="vu:..ja">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid=".gjp81p"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M18 10c0 3.866-3.582 7-8 7a8.841 8.841 0 01-4.083-.98L2 17l1.338-3.123C2.493 12.767 2 11.434 2 10c0-3.866 3.582-7 8-7s8 3.134 8 7zM7 9H5v2h2V9zm8 0h-2v2h2V9zM9 9h2v2H9V9z"
                                    clipRule="evenodd"
                                    data-oid="soz-cvx"
                                />
                            </svg>
                        </button>

                        {/* Settings */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="55iot5k">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="jiff5tr"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z"
                                    clipRule="evenodd"
                                    data-oid="xeh8yfi"
                                />
                            </svg>
                        </button>

                        {/* Logout */}
                        <button className="p-2 hover:bg-gray-100 rounded-lg" data-oid="_7h97iu">
                            <svg
                                className="w-5 h-5 text-gray-700"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                                data-oid="dh5t5m:"
                            >
                                <path
                                    fillRule="evenodd"
                                    d="M3 3a1 1 0 00-1 1v12a1 1 0 102 0V4a1 1 0 00-1-1zm10.293 9.293a1 1 0 001.414 1.414l3-3a1 1 0 000-1.414l-3-3a1 1 0 10-1.414 1.414L14.586 9H7a1 1 0 100 2h7.586l-1.293 1.293z"
                                    clipRule="evenodd"
                                    data-oid="ms_67.o"
                                />
                            </svg>
                        </button>
                    </div>
                </div>
            </nav>

            <div className="flex h-screen bg-gray-100">
                {/* Sidebar */}
                <div className="w-1/3 p-6">
                    <div className="bg-white rounded-2xl p-6 shadow-sm h-full overflow-y-auto">
                        <h2 className="text-xl font-medium text-gray-900 mb-6">Icon Pack Generator</h2>
                        
                        <form onSubmit={(e) => { e.preventDefault(); generateIcons(); }} className="space-y-6">
                            {/* Choose Input Type */}
                            <div>
                                <label className="block text-sm font-medium text-gray-900 mb-4">
                                    Choose input type
                                </label>
                                <div className="bg-gray-100 p-1 rounded-xl flex">
                                    <button
                                        type="button"
                                        onClick={() => setInputType('text')}
                                        className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${
                                            inputType === 'text'
                                                ? 'bg-white text-gray-900 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                        </svg>
                                        <span>Text Description</span>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setInputType('image')}
                                        className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${
                                            inputType === 'image'
                                                ? 'bg-white text-gray-900 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                        </svg>
                                        <span>Reference Image</span>
                                    </button>
                                </div>
                            </div>

                            {/* Input Section */}
                            <div>
                                {inputType === 'text' ? (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-900 mb-2">
                                            General Theme Description
                                        </label>
                                        <textarea
                                            rows={4}
                                            value={generalDescription}
                                            onChange={(e) => setGeneralDescription(e.target.value)}
                                            required={inputType === 'text'}
                                            className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                            placeholder="Describe the general theme for your icon pack..."
                                        />
                                    </div>
                                ) : (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-900 mb-2">
                                            Reference Image
                                        </label>
                                        <div className="space-y-3">
                                            <input
                                                ref={fileInputRef}
                                                type="file"
                                                accept="image/*"
                                                required={inputType === 'image'}
                                                onChange={handleImageSelect}
                                                className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                            />
                                            <div className="text-xs text-gray-500">
                                                Upload an image to use as style reference. The AI will create icons matching the style of your reference image.
                                            </div>
                                            
                                            {/* Image Preview */}
                                            {imagePreview && (
                                                <div className="mt-3">
                                                    <label className="block text-sm font-medium text-gray-900 mb-2">Preview:</label>
                                                    <div className="border rounded p-2 bg-gray-50 flex items-center space-x-3">
                                                        <img src={imagePreview} alt="Reference preview" className="h-20 w-20 object-cover rounded" />
                                                        <div className="flex-1">
                                                            <p className="text-sm text-gray-600">{referenceImage?.name}</p>
                                                            <p className="text-xs text-gray-400">{referenceImage ? formatFileSize(referenceImage.size) : ''}</p>
                                                        </div>
                                                        <button
                                                            type="button"
                                                            onClick={removeImage}
                                                            className="text-red-500 hover:text-red-700 text-sm"
                                                        >
                                                            Remove
                                                        </button>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Icon Count */}
                            <div>
                                <label className="block text-sm font-medium text-gray-900 mb-2">
                                    Number of Icons
                                </label>
                                <select
                                    value={iconCount}
                                    onChange={(e) => setIconCount(e.target.value)}
                                    required
                                    className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                >
                                    <option value="">Choose...</option>
                                    <option value="9">9 Icons (3x3 grid)</option>
                                    <option value="18">18 Icons (2x 3x3 grids)</option>
                                </select>
                            </div>

                            {/* Note about generations */}
                            <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                                <div className="flex items-center">
                                    <svg className="w-4 h-4 text-blue-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                    </svg>
                                    <div>
                                        <p className="text-sm font-medium text-blue-800">Two Generation Styles</p>
                                        <p className="text-sm text-blue-700">Generation 1: Original style. Generation 2: Minimalist modern style with professional color palette.</p>
                                    </div>
                                </div>
                            </div>

                            {/* Individual Icon Descriptions */}
                            {iconCount && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-3">
                                        Individual Icon Descriptions (Optional)
                                    </label>
                                    {renderIconFields()}
                                </div>
                            )}

                            {/* Generate Button */}
                            <button
                                type="submit"
                                disabled={isGenerating}
                                className={`w-full py-3 px-4 rounded-md text-white font-medium ${
                                    isGenerating 
                                        ? 'bg-gray-400 cursor-not-allowed' 
                                        : 'bg-blue-600 hover:bg-blue-700'
                                } transition-colors duration-200`}
                            >
                                {isGenerating ? (
                                    <div className="flex items-center justify-center space-x-2">
                                        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                                        <span>Generating...</span>
                                    </div>
                                ) : (
                                    'Generate Icons'
                                )}
                            </button>
                        </form>
                    </div>
                </div>

                {/* Main Content Area - Two Panes */}
                <div className="flex-1 p-6 flex space-x-6">
                    {/* Your Icons Component (Generation 1) */}
                    <div className="bg-white rounded-2xl shadow-sm flex-1">
                        <div className="p-6 h-full flex flex-col">
                            <div className="flex items-center justify-between mb-4">
                                <h2 className="text-lg font-medium text-gray-900">
                                    Your Icons
                                </h2>
                                {getGeneration1Results().length > 0 && (
                                    <button
                                        onClick={() => exportAllGeneration1()}
                                        className="px-4 py-2 bg-blue-50 text-blue-600 rounded-md text-sm hover:bg-blue-100"
                                    >
                                        Export Icons
                                    </button>
                                )}
                            </div>
                            <div className="flex-1 overflow-y-auto">
                                {uiState === 'initial' && (
                                    <div className="h-full flex items-center justify-center">
                                        <div className="text-center">
                                            <svg className="mx-auto h-16 w-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                            </svg>
                                            <p className="text-gray-500">Generated icons will appear here</p>
                                        </div>
                                    </div>
                                )}

                                {uiState === 'error' && (
                                    <div className="h-full flex items-center justify-center">
                                        <div className="bg-red-50 border border-red-200 rounded-lg p-4 max-w-sm">
                                            <div className="flex items-center">
                                                <svg className="h-5 w-5 text-red-400 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                </svg>
                                                <div>
                                                    <h3 className="text-sm font-medium text-red-800">Generation Failed</h3>
                                                    <p className="text-sm text-red-700 mt-1">{errorMessage}</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {(uiState === 'streaming' || uiState === 'results') && (
                                    <div className="space-y-6">
                                        {renderGenerationResults(1)}
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Variations Component (Generation 2) */}
                    <div className="bg-white rounded-2xl shadow-sm flex-1">
                        <div className="p-6 h-full flex flex-col">
                            <div className="flex items-center justify-between mb-4">
                                <h2 className="text-lg font-medium text-gray-900">
                                    Variations
                                </h2>
                                {getGeneration2Results().length > 0 && (
                                    <button
                                        onClick={() => exportAllGeneration2()}
                                        className="px-4 py-2 bg-blue-50 text-blue-600 rounded-md text-sm hover:bg-blue-100"
                                    >
                                        Export Icons
                                    </button>
                                )}
                            </div>
                            <div className="flex-1 overflow-y-auto">
                                {uiState === 'initial' && (
                                    <div className="h-full flex items-center justify-center">
                                        <div className="text-center">
                                            <svg className="mx-auto h-16 w-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                            </svg>
                                            <p className="text-gray-500">Icon variations will appear here</p>
                                        </div>
                                    </div>
                                )}

                                {(uiState === 'streaming' || uiState === 'results') && (
                                    <div className="space-y-6">
                                        {renderGenerationResults(2)}
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Export Options Modal */}
            {showExportModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 w-full max-w-md">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-medium text-gray-900">Export Options</h3>
                            <button
                                onClick={() => setShowExportModal(false)}
                                className="text-gray-400 hover:text-gray-600"
                            >
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </button>
                        </div>
                        
                        <div className="space-y-4">
                            <p className="text-gray-600 text-sm">Configure your export settings before downloading the icon pack.</p>
                            
                            {/* Background Removal Toggle */}
                            <div className="flex items-center justify-between">
                                <div>
                                    <label className="text-sm font-medium text-gray-900">Remove Background</label>
                                    <p className="text-xs text-gray-500">Remove backgrounds from icons for transparent PNG files</p>
                                </div>
                                <label className="relative inline-flex items-center cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={removeBackground}
                                        onChange={(e) => setRemoveBackground(e.target.checked)}
                                        className="sr-only peer"
                                    />
                                    <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
                                </label>
                            </div>
                            
                            {/* Output Format */}
                            <div>
                                <label className="block text-sm font-medium text-gray-900 mb-2">Output Format</label>
                                <select
                                    value={outputFormat}
                                    onChange={(e) => setOutputFormat(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300"
                                >
                                    <option value="png">PNG</option>
                                    <option value="svg">SVG</option>
                                </select>
                                <p className="text-xs text-gray-500 mt-1">Choose between standard PNG and vector SVG format</p>
                            </div>

                            {/* Export Info */}
                            <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                                <div className="flex items-center">
                                    <svg className="w-5 h-5 text-blue-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                                    </svg>
                                    <div>
                                        <p className="text-sm font-medium text-blue-800">Export Format: ZIP file containing PNG images</p>
                                        <p className="text-sm text-blue-700">
                                            Icon Count: {exportContext ? streamingResults[`${exportContext.serviceName}-gen${exportContext.generationIndex}`]?.icons?.length || 0 : 0} icons
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div className="flex justify-end space-x-3 mt-6">
                            <button
                                onClick={() => setShowExportModal(false)}
                                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={confirmExport}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                Download ZIP
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Export Progress Modal */}
            {showProgressModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 w-full max-w-md">
                        <div className="text-center">
                            <div className="mb-4">
                                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                            </div>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">Processing Export</h3>
                            <p className="text-gray-600 mb-4">{exportProgress.message}</p>
                            
                            {/* Progress Bar */}
                            <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
                                <div 
                                    className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                                    style={{ width: `${exportProgress.percent}%` }}
                                />
                            </div>
                            
                            <div className="flex justify-between text-sm text-gray-500">
                                <span>Step {exportProgress.step}</span>
                                <span>of 4</span>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
