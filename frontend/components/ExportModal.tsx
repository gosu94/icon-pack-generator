import React from 'react';
import { StreamingResults } from '../lib/types';

interface ExportModalProps {
    show: boolean;
    onClose: () => void;
    onConfirm: () => void;
    removeBackground: boolean;
    setRemoveBackground: (value: boolean) => void;
    outputFormat: string;
    setOutputFormat: (value: string) => void;
    context: any;
    streamingResults: StreamingResults;
}

const ExportModal: React.FC<ExportModalProps> = ({
    show, onClose, onConfirm, removeBackground, setRemoveBackground, outputFormat, setOutputFormat, context, streamingResults
}) => {
    if (!show) return null;

    const iconCount = context ? streamingResults[`${context.serviceName}-gen${context.generationIndex}`]?.icons?.length || 0 : 0;

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white rounded-lg p-6 w-full max-w-md">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-medium text-gray-900">Export Options</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                    </button>
                </div>
                <div className="space-y-4">
                    <p className="text-gray-600 text-sm">Configure your export settings before downloading the icon pack.</p>
                    <div className="flex items-center justify-between">
                        <div>
                            <label className="text-sm font-medium text-gray-900">Remove Background</label>
                            <p className="text-xs text-gray-500">Remove backgrounds from icons for transparent PNG files</p>
                        </div>
                        <label className="relative inline-flex items-center cursor-pointer">
                            <input type="checkbox" checked={removeBackground} onChange={(e) => setRemoveBackground(e.target.checked)} className="sr-only peer" />
                            <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
                        </label>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-900 mb-2">Output Format</label>
                        <select value={outputFormat} onChange={(e) => setOutputFormat(e.target.value)} className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300">
                            <option value="png">PNG</option>
                            <option value="svg">SVG</option>
                        </select>
                        <p className="text-xs text-gray-500 mt-1">Choose between standard PNG and vector SVG format</p>
                    </div>
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                        <div className="flex items-center">
                            <svg className="w-5 h-5 text-blue-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" /></svg>
                            <div>
                                <p className="text-sm font-medium text-blue-800">Export Format: ZIP file</p>
                                <p className="text-sm text-blue-700">Icon Count: {iconCount} icons</p>
                            </div>
                        </div>
                    </div>
                </div>
                <div className="flex justify-end space-x-3 mt-6">
                    <button onClick={onClose} className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300">Cancel</button>
                    <button onClick={onConfirm} className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">Download ZIP</button>
                </div>
            </div>
        </div>
    );
};

export default ExportModal;
