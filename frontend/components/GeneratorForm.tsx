import React from 'react';

interface GeneratorFormProps {
    inputType: string;
    setInputType: (value: string) => void;
    iconCount: string;
    setIconCount: (value: string) => void;
    generalDescription: string;
    setGeneralDescription: (value: string) => void;
    individualDescriptions: string[];
    setIndividualDescriptions: (value: string[]) => void;
    referenceImage: File | null;
    imagePreview: string;
    isGenerating: boolean;
    generateIcons: () => void;
    handleImageSelect: (event: React.ChangeEvent<HTMLInputElement>) => void;
    removeImage: () => void;
    fileInputRef: React.RefObject<HTMLInputElement>;
    formatFileSize: (bytes: number) => string;
}

const GeneratorForm: React.FC<GeneratorFormProps> = ({
    inputType, setInputType,
    iconCount, setIconCount,
    generalDescription, setGeneralDescription,
    individualDescriptions, setIndividualDescriptions,
    referenceImage, imagePreview,
    isGenerating, generateIcons,
    handleImageSelect, removeImage, fileInputRef, formatFileSize
}) => {

    const renderIconFields = () => {
        const count = parseInt(iconCount);
        if (isNaN(count)) return null;

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
        return <div className="grid grid-cols-3 gap-3">{fields}</div>;
    };

    return (
        <div className="w-1/3 p-6">
            <div className="bg-white rounded-2xl p-6 shadow-sm h-full overflow-y-auto">
                <h2 className="text-xl font-medium text-gray-900 mb-6">Icon Pack Generator</h2>
                
                <form onSubmit={(e) => { e.preventDefault(); generateIcons(); }} className="space-y-6">
                    <div>
                        <label className="block text-sm font-medium text-gray-900 mb-4">Choose input type</label>
                        <div className="bg-gray-100 p-1 rounded-xl flex">
                            <button type="button" onClick={() => setInputType('text')} className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${inputType === 'text' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}>
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                <span>Text Description</span>
                            </button>
                            <button type="button" onClick={() => setInputType('image')} className={`flex-1 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 flex items-center justify-center space-x-2 ${inputType === 'image' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}>
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                                <span>Reference Image</span>
                            </button>
                        </div>
                    </div>

                    <div>
                        {inputType === 'text' ? (
                            <div>
                                <label className="block text-sm font-medium text-gray-900 mb-2">General Theme Description</label>
                                <textarea rows={4} value={generalDescription} onChange={(e) => setGeneralDescription(e.target.value)} required={inputType === 'text'} className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300" placeholder="Describe the general theme for your icon pack..." />
                            </div>
                        ) : (
                            <div>
                                <label className="block text-sm font-medium text-gray-900 mb-2">Reference Image</label>
                                <div className="space-y-3">
                                    <input ref={fileInputRef} type="file" accept="image/*" required={inputType === 'image'} onChange={handleImageSelect} className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300" />
                                    <div className="text-xs text-gray-500">Upload an image to use as style reference. The AI will create icons matching the style of your reference image.</div>
                                    {imagePreview && (
                                        <div className="mt-3">
                                            <label className="block text-sm font-medium text-gray-900 mb-2">Preview:</label>
                                            <div className="border rounded p-2 bg-gray-50 flex items-center space-x-3">
                                                <img src={imagePreview} alt="Reference preview" className="h-20 w-20 object-cover rounded" />
                                                <div className="flex-1">
                                                    <p className="text-sm text-gray-600">{referenceImage?.name}</p>
                                                    <p className="text-xs text-gray-400">{referenceImage ? formatFileSize(referenceImage.size) : ''}</p>
                                                </div>
                                                <button type="button" onClick={removeImage} className="text-red-500 hover:text-red-700 text-sm">Remove</button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-900 mb-2">Number of Icons</label>
                        <select value={iconCount} onChange={(e) => setIconCount(e.target.value)} required className="w-full px-3 py-2 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-300">
                            <option value="">Choose...</option>
                            <option value="9">9 Icons (3x3 grid)</option>
                            <option value="18">18 Icons (2x 3x3 grids)</option>
                        </select>
                    </div>

                    {iconCount && (
                        <div>
                            <label className="block text-sm font-medium text-gray-900 mb-3">Individual Icon Descriptions (Optional)</label>
                            {renderIconFields()}
                        </div>
                    )}

                    <button type="submit" disabled={isGenerating} className={`w-full py-3 px-4 rounded-md text-white font-medium ${isGenerating ? 'bg-gray-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'} transition-colors duration-200`}>
                        {isGenerating ? (
                            <div className="flex items-center justify-center space-x-2">
                                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                                <span>Generating...</span>
                            </div>
                        ) : ( 'Generate Icons' )}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default GeneratorForm;
