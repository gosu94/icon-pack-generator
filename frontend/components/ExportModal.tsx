import React, { useState, useEffect } from "react";
import { Switch } from "@/components/ui/switch";
import { GenerationMode } from "../lib/types";

interface ExportModalProps {
  show: boolean;
  onClose: () => void;
  onConfirm: (formats: string[], sizes?: number[]) => void;
  iconCount: number;
  mode: GenerationMode;
}

const ExportModal: React.FC<ExportModalProps> = ({
  show,
  onClose,
  onConfirm,
  iconCount,
  mode,
}) => {
  const [formats, setFormats] = useState(
    mode === "icons"
      ? {
          svg: true,
          png: true,
          ico: true,
          webp: true,
        }
      : {
          png: true,
          webp: true,
        }
  );

  useEffect(() => {
    setFormats(
      mode === "icons"
        ? {
            svg: true,
            png: true,
            ico: true,
            webp: true,
          }
        : {
            png: true,
            webp: true,
          }
    );
  }, [mode]);

  const handleFormatChange = (format: keyof typeof formats) => {
    setFormats((prev) => ({ ...prev, [format]: !prev[format] }));
  };

  

  const handleConfirm = () => {
    const selectedFormats = Object.entries(formats)
      .filter(([, isSelected]) => isSelected)
      .map(([format]) => format);

    if (mode === "illustrations") {
      const selectedSizes = [500];
      onConfirm(selectedFormats, selectedSizes);
    } else {
      onConfirm(selectedFormats);
    }
  };

  if (!show) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-medium text-gray-900">Export {mode === "icons" ? "Icon" : "Illustration"} Pack</h3>
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
            Select the formats you need for your {mode === "icons" ? "icon" : "illustration"} pack.
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
              <div className="w-full space-y-4">
                <div>
                  <p className="text-sm font-semibold text-blue-900 mb-3">
                    {iconCount} {mode === "icons" ? "icons" : "illustrations"} • Choose your formats
                  </p>
                  
                  <div className="grid grid-cols-2 gap-4">
                    {Object.keys(formats).map((format) => (
                      <div key={format} className="flex items-center space-x-2">
                        <Switch
                          id={format}
                          checked={formats[format as keyof typeof formats]}
                          onCheckedChange={() => handleFormatChange(format as keyof typeof formats)}
                        />
                        <label htmlFor={format} className="text-sm font-medium text-gray-700 uppercase cursor-pointer">
                          {format}
                        </label>
                      </div>
                    ))}
                  </div>
                </div>

                
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
            onClick={handleConfirm}
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