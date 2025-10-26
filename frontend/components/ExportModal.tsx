import React, { useState, useEffect } from "react";
import Image from "next/image";
import { Switch } from "@/components/ui/switch";
import { GenerationMode } from "../lib/types";

interface ExportModalProps {
  show: boolean;
  onClose: () => void;
  onConfirm: (formats: string[], sizes?: number[], vectorizeSvg?: boolean) => void;
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
          png: true,
          ico: true,
          webp: true,
        }
      : {
          png: true,
          webp: true,
        }
  );
  const [vectorizeSvg, setVectorizeSvg] = useState(false);

  useEffect(() => {
    setFormats(
      mode === "icons"
        ? {
            png: true,
            ico: true,
            webp: true,
          }
        : {
            png: true,
            webp: true,
        }
    );
    setVectorizeSvg(false);
  }, [mode]);

  useEffect(() => {
    if (!show) {
      setVectorizeSvg(false);
    }
  }, [show]);

  const handleFormatChange = (format: keyof typeof formats) => {
    setFormats((prev) => ({ ...prev, [format]: !prev[format] }));
  };

  

  const handleConfirm = () => {
    const selectedFormats = Object.entries(formats)
      .filter(([, isSelected]) => isSelected)
      .map(([format]) => format);

    if (mode === "illustrations") {
      const selectedSizes = [1024];
      onConfirm(selectedFormats, selectedSizes);
    } else if (mode === "mockups") {
      // For mockups, use original size (1920x1080 for 16:9)
      const selectedSizes = [1920];
      onConfirm(selectedFormats, selectedSizes);
    } else {
      const shouldVectorize = (mode === "icons" || mode === "labels") ? vectorizeSvg : false;
      onConfirm(selectedFormats, undefined, shouldVectorize);
    }
  };

  if (!show) return null;

  const totalVectorCost = Math.ceil(Math.max(iconCount, 1) / 9);
  const labelVectorCost = 1;
  const showVectorOption = mode === "icons" || mode === "labels";

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-medium text-gray-900">
            Export {mode === "icons" ? "Icon" : mode === "illustrations" ? "Illustration" : mode === "labels" ? "Label" : "UI Mockup"} Pack
          </h3>
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
            Select the formats you need for your {mode === "icons" ? "icon" : mode === "illustrations" ? "illustration" : mode === "labels" ? "label" : "UI mockup"} pack.
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
                    {iconCount} {mode === "icons" ? "icons" : mode === "illustrations" ? "illustrations" : mode === "labels" ? "labels" : "mockups"} • Choose your formats
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
            {showVectorOption && (
              <div className="rounded-lg border border-blue-100 bg-gradient-to-r from-purple-50 to-blue-50 p-4 mt-3">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 space-y-2">
                    <div className="flex items-center justify-between">
                      <p className="text-sm font-semibold text-indigo-900">
                        Vectorized SVG export
                      </p>
                      <span className="flex items-center space-x-1 rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-semibold text-indigo-800">
                        <span>
                          +{mode === "icons" ? totalVectorCost : labelVectorCost}
                        </span>
                        <Image src="/images/coin.webp" alt="Coin" width={16} height={16} />
                      </span>
                    </div>
                    <p className="text-xs text-indigo-700">
                      {mode === "icons"
                        ? `Toggle to receive crisp, editable SVGs processed with AI. Costs 1 coin per 9 icons (this export: ${totalVectorCost} coin${totalVectorCost === 1 ? "" : "s"}).`
                        : "Toggle to receive a crisp, editable SVG version processed with AI. Costs 1 coin for this export."}
                    </p>
                    <div className="flex items-start space-x-2 rounded-md bg-indigo-200/60 px-3 py-2">
                      <span className="mt-0.5 text-indigo-900" aria-hidden="true">
                        ⚠️
                      </span>
                      <p className="text-xs text-indigo-900">
                        Important: vectorized icons may lose minor details during conversion.
                      </p>
                    </div>
                  </div>
                  <Switch
                    id="vectorize-svg"
                    checked={vectorizeSvg}
                    onCheckedChange={() => setVectorizeSvg((prev) => !prev)}
                  />
                </div>
              </div>
            )}
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
