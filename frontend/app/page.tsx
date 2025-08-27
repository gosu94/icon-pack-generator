'use client';

import { useState, useRef, useEffect } from 'react';
import { Icon, ServiceResult, StreamingResults, GenerationResponse, UIState } from '../lib/types';
import Navigation from '../components/Navigation';
import GeneratorForm from '../components/GeneratorForm';
import ResultsDisplay from '../components/ResultsDisplay';
import ExportModal from '../components/ExportModal';
import ProgressModal from '../components/ProgressModal';

export default function Page() {
    // Form state
    const [inputType, setInputType] = useState('text');
    const [iconCount, setIconCount] = useState('9');
    const [generationsPerService] = useState('2');
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
    const [showResultsPanes, setShowResultsPanes] = useState(false);

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
    
    // Unified progress timer
    const [overallProgress, setOverallProgress] = useState(0);
    const [totalDuration, setTotalDuration] = useState(0);
    const overallProgressTimerRef = useRef<NodeJS.Timeout | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const count = parseInt(iconCount);
        setIndividualDescriptions(new Array(count).fill(''));
    }, [iconCount]);

    useEffect(() => {
        return () => {
            if (overallProgressTimerRef.current) {
                clearInterval(overallProgressTimerRef.current);
            }
            Object.values(animationTimers).forEach(timers => {
                timers.forEach(timer => clearTimeout(timer));
            });
        };
    }, [animationTimers]);

    const startIconAnimation = (serviceId: string, iconCount: number) => {
        if (animationTimers[serviceId]) {
            animationTimers[serviceId].forEach(timer => clearTimeout(timer));
        }
        setAnimatingIcons(prev => ({ ...prev, [serviceId]: 0 }));
        const timers: NodeJS.Timeout[] = [];
        for (let i = 0; i < iconCount; i++) {
            const timer = setTimeout(() => {
                setAnimatingIcons(prev => ({ ...prev, [serviceId]: i + 1 }));
            }, i * 150);
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
        return isVisible ? "opacity-100 scale-100 transition-all duration-500 ease-out" : "opacity-0 scale-75 transition-all duration-500 ease-out";
    };

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

    const calculateTimeRemaining = () => {
        if (overallProgress >= 100) return '0s';
        const remainingMs = totalDuration * (1 - (overallProgress / 100));
        const remainingSeconds = Math.round(remainingMs / 1000);
        return `${remainingSeconds}s`;
    };

    const handleImageSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) {
            setReferenceImage(null);
            setImagePreview('');
            return;
        }
        if (!file.type.startsWith('image/')) {
            alert('Please select a valid image file.');
            return;
        }
        if (file.size > 10 * 1024 * 1024) {
            alert('File size must be less than 10MB.');
            return;
        }
        setReferenceImage(file);
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

    const generateIcons = async () => {
        if (!validateForm()) {
            setUiState('error');
            return;
        }
        setIsGenerating(true);
        setUiState('streaming');
        setStreamingResults({});
        setShowResultsPanes(false);
        setOverallProgress(0);
        if (overallProgressTimerRef.current) {
            clearInterval(overallProgressTimerRef.current);
        }
        Object.keys(animationTimers).forEach(serviceId => {
            clearIconAnimation(serviceId);
        });
        let duration = 35000;
        if (iconCount === '18') {
            duration = 90000;
        } else if (inputType === 'image') {
            duration = 60000;
        }
        setTotalDuration(duration);
        const increment = 100 / (duration / 100);
        overallProgressTimerRef.current = setInterval(() => {
            setOverallProgress(prev => {
                const newProgress = prev + increment;
                if (newProgress >= 100) {
                    if (overallProgressTimerRef.current) {
                        clearInterval(overallProgressTimerRef.current);
                    }
                    return 100;
                }
                return newProgress;
            });
        }, 100);
        const formData: any = {
            iconCount: parseInt(iconCount),
            generationsPerService: parseInt(generationsPerService),
            individualDescriptions: individualDescriptions.filter(desc => desc.trim())
        };
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
                if (overallProgressTimerRef.current) clearInterval(overallProgressTimerRef.current);
                return;
            }
        }
        setCurrentRequest({ ...formData });
        try {
            const response = await fetch('/generate-stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(formData)
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            const { requestId, enabledServices } = data;
            initializeStreamingResults(enabledServices);
            const eventSource = new EventSource(`/stream/${requestId}`);
            eventSource.addEventListener('service_update', (event) => {
                try {
                    handleServiceUpdate(JSON.parse(event.data));
                } catch (error) {
                    console.error('Error parsing service update:', error);
                }
            });
            eventSource.addEventListener('generation_complete', (event) => {
                try {
                    handleGenerationComplete(JSON.parse(event.data));
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing completion update:', error);
                    eventSource.close();
                }
            });
            eventSource.addEventListener('generation_error', (event) => {
                try {
                    const update = JSON.parse(event.data);
                    setErrorMessage(update.message || 'Generation failed');
                    setUiState('error');
                    setIsGenerating(false);
                    if (overallProgressTimerRef.current) clearInterval(overallProgressTimerRef.current);
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing error update:', error);
                    setErrorMessage('Generation failed with unknown error');
                    setUiState('error');
                    setIsGenerating(false);
                    if (overallProgressTimerRef.current) clearInterval(overallProgressTimerRef.current);
                    eventSource.close();
                }
            });
            eventSource.onerror = (error) => {
                console.error('EventSource error:', error);
                setErrorMessage('Connection error. Please try again.');
                setUiState('error');
                setIsGenerating(false);
                if (overallProgressTimerRef.current) clearInterval(overallProgressTimerRef.current);
                eventSource.close();
            };
        } catch (error) {
            console.error('Error starting generation:', error);
            setErrorMessage('Failed to start generation. Please try again.');
            setUiState('error');
            setIsGenerating(false);
            if (overallProgressTimerRef.current) clearInterval(overallProgressTimerRef.current);
        }
    };

    const initializeStreamingResults = (enabledServices: {[key: string]: boolean}) => {
        const newResults: StreamingResults = {};
        const allServices = [
            { id: 'flux', name: 'Flux-Pro' },
            { id: 'recraft', name: 'Recraft V3' },
            { id: 'photon', name: 'Luma Photon' },
            { id: 'gpt', name: '' },
            { id: 'imagen', name: 'Imagen 4' }
        ];
        const enabledServicesList = allServices.filter(service => enabledServices[service.id]);
        const generationsNum = parseInt(generationsPerService) || 1;
        enabledServicesList.forEach(service => {
            for (let genIndex = 1; genIndex <= generationsNum; genIndex++) {
                const uniqueId = `${service.id}-gen${genIndex}`;
                newResults[uniqueId] = {
                    icons: [],
                    generationTimeMs: 0,
                    status: 'started',
                    message: 'Progressing..',
                    generationIndex: genIndex,
                };
            }
        });
        setStreamingResults(newResults);
    };

    const handleServiceUpdate = (update: any) => {
        const serviceId = update.serviceName;
        setStreamingResults(prev => {
            const current = prev[serviceId] || {};
            const updated = {
                ...current,
                status: update.status,
                message: update.message || current.message,
                generationTimeMs: update.generationTimeMs || current.generationTimeMs
            };
            if (update.status === 'success') {
                updated.icons = update.icons || [];
                updated.originalGridImageBase64 = update.originalGridImageBase64;
                updated.generationIndex = update.generationIndex;
            }
            return { ...prev, [serviceId]: updated };
        });
    };

    const handleGenerationComplete = (update: any) => {
        if (overallProgressTimerRef.current) {
            clearInterval(overallProgressTimerRef.current);
        }
        setOverallProgress(100);
        setShowResultsPanes(true);
        setStreamingResults(latestStreamingResults => {
            setTimeout(() => {
                Object.entries(latestStreamingResults).forEach(([serviceId, result]) => {
                    if (result.status === 'success' && result.icons.length > 0) {
                        startIconAnimation(serviceId, result.icons.length);
                    }
                });
            }, 300);
            let allIcons: Icon[] = [];
            Object.values(latestStreamingResults).forEach(result => {
                if (result.icons) {
                    allIcons = allIcons.concat(result.icons);
                }
            });
            setCurrentIcons(allIcons);
            const groupedResults = {
                falAiResults: [] as ServiceResult[],
                recraftResults: [] as ServiceResult[],
                photonResults: [] as ServiceResult[],
                gptResults: [] as ServiceResult[],
                imagenResults: [] as ServiceResult[]
            };
            Object.entries(latestStreamingResults).forEach(([serviceKey, result]) => {
                const baseServiceId = serviceKey.replace(/-gen\d+$/, '');
                switch (baseServiceId) {
                    case 'flux': groupedResults.falAiResults.push(result); break;
                    case 'recraft': groupedResults.recraftResults.push(result); break;
                    case 'photon': groupedResults.photonResults.push(result); break;
                    case 'gpt': groupedResults.gptResults.push(result); break;
                    case 'imagen': groupedResults.imagenResults.push(result); break;
                }
            });
            setCurrentResponse({
                icons: allIcons,
                ...groupedResults,
                requestId: update.requestId
            });
            setUiState('results');
            setIsGenerating(false);
            return latestStreamingResults;
        });
    };

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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(exportData)
            });
            setExportProgress({ step: 3, message: 'Creating ZIP file...', percent: 75 });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const blob = await response.blob();
            setExportProgress({ step: 4, message: 'Finalizing download...', percent: 100 });
            setTimeout(() => {
                setShowProgressModal(false);
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;
                a.download = fileName;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                // Icon pack downloaded successfully
            }, 1000);
        } catch (error) {
            console.error('Error exporting icons:', error);
            setShowProgressModal(false);
            setErrorMessage('Failed to export icons. Please try again.');
            setUiState('error');
        }
    };

    // Removed toast functions - now using progress UI instead of alerts

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
        const serviceResults = getServiceResults(serviceId, generationIndex);
        if (!serviceResults?.originalGridImageBase64) {
            setErrorMessage('Original image not found for this service');
            setUiState('error');
            return;
        }

        // Show progress for the specific generation
        setStreamingResults(prev => ({
            ...prev,
            [uniqueId]: {
                ...prev[uniqueId],
                status: 'started',
                message: 'Generating more icons...'
            }
        }));

        const moreIconsRequest = {
            originalRequestId: currentResponse?.requestId,
            serviceName: serviceId,
            originalImageBase64: serviceResults.originalGridImageBase64,
            generalDescription: currentRequest?.generalDescription,
            iconDescriptions: descriptions,
            iconCount: 9,
            seed: serviceResults.seed,
            generationIndex: generationIndex // Include generation index
        };

        try {
            const response = await fetch('/generate-more', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(moreIconsRequest)
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            
            if (data.status === 'success') {
                // Update current icons list
                setCurrentIcons(prev => prev.concat(data.newIcons));
                
                // Update streaming results
                const previousIconCount = streamingResults[uniqueId]?.icons?.length || 0;
                setStreamingResults(prev => ({
                    ...prev,
                    [uniqueId]: {
                        ...prev[uniqueId],
                        status: 'success',
                        message: 'More icons generated successfully',
                        icons: [...(prev[uniqueId]?.icons || []), ...data.newIcons]
                    }
                }));

                // Animate new icons
                setTimeout(() => {
                    setAnimatingIcons(prev => ({ ...prev, [uniqueId]: previousIconCount }));
                    for (let i = 0; i < data.newIcons.length; i++) {
                        setTimeout(() => {
                            setAnimatingIcons(prev => ({ 
                                ...prev, 
                                [uniqueId]: previousIconCount + i + 1 
                            }));
                        }, i * 150);
                    }
                }, 200);

                hideMoreIconsForm(uniqueId);
            } else {
                // Show error in streaming results
                setStreamingResults(prev => ({
                    ...prev,
                    [uniqueId]: {
                        ...prev[uniqueId],
                        status: 'error',
                        message: data.message || 'Failed to generate more icons'
                    }
                }));
            }
        } catch (error) {
            console.error('Error generating more icons:', error);
            setStreamingResults(prev => ({
                ...prev,
                [uniqueId]: {
                    ...prev[uniqueId],
                    status: 'error',
                    message: 'Failed to generate more icons. Please try again.'
                }
            }));
        }
    };

    const getServiceResults = (serviceId: string, generationIndex: number): ServiceResult | null => {
        if (!currentResponse) return null;
        let resultsArray: ServiceResult[] | undefined;
        switch (serviceId) {
            case 'flux': resultsArray = currentResponse.falAiResults; break;
            case 'recraft': resultsArray = currentResponse.recraftResults; break;
            case 'photon': resultsArray = currentResponse.photonResults; break;
            case 'gpt': resultsArray = currentResponse.gptResults; break;
            case 'imagen': resultsArray = currentResponse.imagenResults; break;
            default: return null;
        }
        if (resultsArray && resultsArray.length > 0) {
            return resultsArray.find(r => r.generationIndex === generationIndex) || null;
        }
        return null;
    };

    return (
        <div className="min-h-screen bg-white">
            <Navigation />
            <div className="flex h-screen bg-gray-100">
                <GeneratorForm
                    inputType={inputType} setInputType={setInputType}
                    iconCount={iconCount} setIconCount={setIconCount}
                    generalDescription={generalDescription} setGeneralDescription={setGeneralDescription}
                    individualDescriptions={individualDescriptions} setIndividualDescriptions={setIndividualDescriptions}
                    referenceImage={referenceImage} imagePreview={imagePreview}
                    isGenerating={isGenerating} generateIcons={generateIcons}
                    handleImageSelect={handleImageSelect} removeImage={removeImage}
                    fileInputRef={fileInputRef} formatFileSize={formatFileSize}
                />
                <ResultsDisplay
                    uiState={uiState} isGenerating={isGenerating} overallProgress={overallProgress}
                    calculateTimeRemaining={calculateTimeRemaining} errorMessage={errorMessage}
                    streamingResults={streamingResults} showResultsPanes={showResultsPanes}
                    getIconAnimationClass={getIconAnimationClass} animatingIcons={animatingIcons}
                    exportGeneration={exportGeneration} currentResponse={currentResponse}
                    moreIconsVisible={moreIconsVisible} showMoreIconsForm={showMoreIconsForm}
                    hideMoreIconsForm={hideMoreIconsForm} generateMoreIcons={generateMoreIcons}
                    moreIconsDescriptions={moreIconsDescriptions} setMoreIconsDescriptions={setMoreIconsDescriptions}
                    getServiceDisplayName={getServiceDisplayName}
                />
            </div>
            <ExportModal
                show={showExportModal}
                onClose={() => setShowExportModal(false)}
                onConfirm={confirmExport}
                removeBackground={removeBackground} setRemoveBackground={setRemoveBackground}
                outputFormat={outputFormat} setOutputFormat={setOutputFormat}
                iconCount={exportContext ? streamingResults[`${exportContext.serviceName}-gen${exportContext.generationIndex}`]?.icons?.length || 0 : 0}
            />
            <ProgressModal show={showProgressModal} progress={exportProgress} />
        </div>
    );
}