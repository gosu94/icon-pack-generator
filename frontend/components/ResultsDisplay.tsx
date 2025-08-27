import React from 'react';
import { UIState, ServiceResult, GenerationResponse } from '../lib/types';

interface ResultsDisplayProps {
    uiState: UIState;
    isGenerating: boolean;
    overallProgress: number;
    calculateTimeRemaining: () => string;
    errorMessage: string;
    streamingResults: { [key: string]: ServiceResult };
    showResultsPanes: boolean;
    getIconAnimationClass: (serviceId: string, iconIndex: number) => string;
    animatingIcons: { [key: string]: number };
    exportGeneration: (requestId: string, serviceName: string, generationIndex: number) => void;
    currentResponse: GenerationResponse | null;
    moreIconsVisible: { [key: string]: boolean };
    showMoreIconsForm: (uniqueId: string) => void;
    hideMoreIconsForm: (uniqueId: string) => void;
    generateMoreIcons: (serviceId: string, serviceName: string, generationIndex: number) => void;
    moreIconsDescriptions: { [key: string]: string[] };
    setMoreIconsDescriptions: React.Dispatch<React.SetStateAction<{ [key: string]: string[] }>>;
    getServiceDisplayName: (serviceId: string) => string;
}

const ResultsDisplay: React.FC<ResultsDisplayProps> = ({
    uiState, isGenerating, overallProgress, calculateTimeRemaining, errorMessage, 
    streamingResults, showResultsPanes, getIconAnimationClass, animatingIcons,
    exportGeneration, currentResponse, moreIconsVisible, showMoreIconsForm, 
    hideMoreIconsForm, generateMoreIcons, moreIconsDescriptions, setMoreIconsDescriptions,
    getServiceDisplayName
}) => {

    const getGenerationResults = (generationNumber: number) => {
        return Object.entries(streamingResults)
            .filter(([, result]) => result.generationIndex === generationNumber)
            .map(([serviceId, result]) => ({ serviceId, ...result }));
    };

    const renderGenerationResults = (generationNumber: number) => {
        const results = getGenerationResults(generationNumber);
        return results.map((result, index) => {
            const baseServiceId = result.serviceId.replace(/-gen\d+$/, '');
            const serviceName = getServiceDisplayName(baseServiceId);

            return (
                <div key={result.serviceId}>
                    {index > 0 && <div className="border-t border-gray-200 my-6" />}
                    <div className="mb-4">
                        <div className="flex items-center justify-between">
                            <h3 className="text-lg font-medium text-gray-900 flex items-center">
                                <span className="mr-2">
                                    {result.status === 'success' ? '✅' : result.status === 'error' ? '❌' : '🔄'}
                                </span>
                                {result.status}
                                {result.generationTimeMs > 0 && (
                                    <span className="text-sm text-gray-500 ml-2">({(result.generationTimeMs / 1000).toFixed(1)}s)</span>
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
                        <p className="text-sm text-gray-600 mt-1">{result.message}</p>
                    </div>

                    {showResultsPanes && result.icons && result.icons.length > 0 && (
                        <div className="grid grid-cols-3 gap-4">
                            {result.icons.map((icon, iconIndex) => (
                                <div key={iconIndex} className={`relative group transform ${getIconAnimationClass(result.serviceId, iconIndex)}`}>
                                    <img src={`data:image/png;base64,${icon.base64Data}`} alt={`Generated Icon ${iconIndex + 1}`} className="w-full h-auto rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200" />
                                    <div className={`absolute inset-0 bg-gradient-to-r from-blue-400 to-purple-500 rounded-lg transition-opacity duration-700 ${animatingIcons[result.serviceId] > iconIndex ? 'opacity-0' : 'opacity-20'}`} />
                                </div>
                            ))}
                        </div>
                    )}

                    {result.status === 'success' && uiState === 'results' && (
                        <div className="mt-6 p-4 bg-gray-50 rounded-lg">
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-medium text-gray-900">Generate More With Same Style</h4>
                                {!moreIconsVisible[result.serviceId] && (
                                    <button onClick={() => showMoreIconsForm(result.serviceId)} className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Generate More Icons</button>
                                )}
                            </div>
                            {moreIconsVisible[result.serviceId] && (
                                <div className="space-y-3">
                                    <p className="text-xs text-gray-600">Describe 9 new icons (leave empty for creative variations):</p>
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
                                                    setMoreIconsDescriptions(prev => ({ ...prev, [result.serviceId]: newDescriptions }));
                                                }}
                                                className="w-full px-2 py-1 border border-gray-200 rounded text-xs"
                                            />
                                        ))}
                                    </div>
                                    <div className="flex space-x-2">
                                        <button onClick={() => generateMoreIcons(baseServiceId, serviceName, result.generationIndex)} className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Generate 9 More Icons</button>
                                        <button onClick={() => hideMoreIconsForm(result.serviceId)} className="px-3 py-1 bg-gray-200 text-gray-700 rounded text-sm hover:bg-gray-300">Cancel</button>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            );
        });
    };

    return (
        <div className="flex-1 p-6 flex flex-col">
            {isGenerating && (
                <div className="mb-6">
                    <div className="w-full bg-gray-200 rounded-full h-2.5">
                        <div className="bg-blue-600 h-2.5 rounded-full transition-all duration-300" style={{ width: `${overallProgress}%` }} />
                    </div>
                    <p className="text-center text-sm text-gray-600 mt-2">
                        {overallProgress < 100 ? `Generating icons... Estimated time remaining: ${calculateTimeRemaining()}`: 'Finalizing results...'}
                    </p>
                </div>
            )}
            <div className="flex-1 flex space-x-6">
                <div className="bg-white rounded-2xl shadow-sm flex-1">
                    <div className="p-6 h-full flex flex-col">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-medium text-gray-900">Your Icons</h2>
                        </div>
                        <div className="flex-1 overflow-y-auto">
                            {uiState === 'initial' && (
                                <div className="h-full flex items-center justify-center">
                                    <div className="text-center">
                                        <svg className="mx-auto h-16 w-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                                        <p className="text-gray-500">Generated icons will appear here</p>
                                    </div>
                                </div>
                            )}
                            {uiState === 'error' && (
                                <div className="h-full flex items-center justify-center">
                                    <div className="bg-red-50 border border-red-200 rounded-lg p-4 max-w-sm">
                                        <div className="flex items-center">
                                            <svg className="h-5 w-5 text-red-400 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                                            <div>
                                                <h3 className="text-sm font-medium text-red-800">Generation Failed</h3>
                                                <p className="text-sm text-red-700 mt-1">{errorMessage}</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                            {(uiState === 'streaming' || uiState === 'results') && (
                                <div className="space-y-6">{renderGenerationResults(1)}</div>
                            )}
                        </div>
                    </div>
                </div>
                <div className="bg-white rounded-2xl shadow-sm flex-1">
                    <div className="p-6 h-full flex flex-col">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-medium text-gray-900">Variation</h2>
                        </div>
                        <div className="flex-1 overflow-y-auto">
                            {uiState === 'initial' && (
                                <div className="h-full flex items-center justify-center">
                                    <div className="text-center">
                                        <svg className="mx-auto h-16 w-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                                        <p className="text-gray-500">Icon variations will appear here</p>
                                    </div>
                                </div>
                            )}
                            {(uiState === 'streaming' || uiState === 'results') && (
                                <div className="space-y-6">{renderGenerationResults(2)}</div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ResultsDisplay;
