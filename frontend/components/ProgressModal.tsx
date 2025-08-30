import React from "react";

interface ProgressModalProps {
  show: boolean;
  progress: { step: number; message: string; percent: number };
}

const ProgressModal: React.FC<ProgressModalProps> = ({ show, progress }) => {
  if (!show) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <div className="text-center">
          <div className="mb-4">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          </div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">
            Processing Export
          </h3>
          <p className="text-gray-600 mb-4">{progress.message}</p>
          <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
            <div
              className="bg-blue-600 h-2 rounded-full transition-all duration-300"
              style={{ width: `${progress.percent}%` }}
            />
          </div>
          <div className="flex justify-between text-sm text-gray-500">
            <span>Step {progress.step}</span>
            <span>of 4</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProgressModal;
