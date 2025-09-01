import React from "react";

interface ExportModalProps {
  show: boolean;
  onClose: () => void;
  onConfirm: () => void;
  iconCount: number;
}

const ExportModal: React.FC<ExportModalProps> = ({
  show,
  onClose,
  onConfirm,
  iconCount,
}) => {
  if (!show) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-medium text-gray-900">Export Icon Pack</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>
        
        <div className="space-y-4">
          <p className="text-gray-600 text-sm">
            Download a comprehensive icon pack with multiple formats and sizes.
          </p>
          
          <div className="bg-gradient-to-r from-blue-50 to-purple-50 rounded-lg p-4 border border-blue-100">
            <div className="flex items-start">
              <svg
                className="w-6 h-6 text-blue-500 mr-3 mt-0.5 flex-shrink-0"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10"
                />
              </svg>
              <div className="space-y-2">
                <p className="text-sm font-semibold text-blue-900">
                  Comprehensive Icon Pack
                </p>
                <p className="text-sm text-blue-800">
                  {iconCount} icons • Multiple formats included
                </p>
                
                <div className="grid grid-cols-1 gap-2 mt-3">
                  <div className="flex items-center text-xs text-blue-700">
                    <span className="w-2 h-2 bg-blue-400 rounded-full mr-2"></span>
                    <strong>SVG:</strong>&nbsp;Vector format (scalable)
                  </div>
                  <div className="flex items-center text-xs text-blue-700">
                    <span className="w-2 h-2 bg-green-400 rounded-full mr-2"></span>
                    <strong>PNG:</strong>&nbsp;16px, 32px, 64px, 128px, 256px
                  </div>
                  <div className="flex items-center text-xs text-blue-700">
                    <span className="w-2 h-2 bg-purple-400 rounded-full mr-2"></span>
                    <strong>ICO:</strong>&nbsp;All sizes (16px-256px) for apps & favicons
                  </div>
                </div>
                
                <p className="text-xs text-blue-600 mt-2">
                  ✓ All icons have transparent backgrounds<br/>
                  ✓ Ready for web and app development
                </p>
              </div>
            </div>
          </div>
        </div>
        
        <div className="flex justify-end space-x-3 mt-6">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
          >
            Download ZIP
          </button>
        </div>
      </div>
    </div>
  );
};

export default ExportModal;