import React, { useState, useEffect } from "react";
import Image from "next/image";
import { useAuth } from "../context/AuthContext";
import { GenerationMode } from "@/lib/types";

interface GeneratorFormProps {
  mode: GenerationMode;
  setMode: (value: GenerationMode) => void;
  inputType: string;
  setInputType: (value: string) => void;
  labelText: string;
  setLabelText: (value: string) => void;
  generateVariations: boolean;
  setGenerateVariations: (value: boolean) => void;

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
  mode,
  setMode,
  inputType,
  setInputType,
  labelText,
  setLabelText,
  generateVariations,
  setGenerateVariations,

  generalDescription,
  setGeneralDescription,
  individualDescriptions,
  setIndividualDescriptions,
  referenceImage,
  imagePreview,
  isGenerating,
  generateIcons,
  handleImageSelect,
  removeImage,
  fileInputRef,
  formatFileSize,
}) => {
  const { authState } = useAuth();
  const [isDragOver, setIsDragOver] = useState(false);
  const isAuthenticated = authState.authenticated;

  // Automatically disable variations when user only has trial coins
  useEffect(() => {
    if (authState.user) {
      const regularCoins = authState.user.coins || 0;
      const trialCoins = authState.user.trialCoins || 0;
      const isTrialOnly = regularCoins === 0 && trialCoins > 0;
      
      if (isTrialOnly && generateVariations) {
        setGenerateVariations(false);
      }
    }
  }, [authState.user, generateVariations, setGenerateVariations]);

  const handleDragEnter = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    const file = e.dataTransfer.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith("image/")) {
      alert("Please select a valid image file.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      alert("File size must be less than 10MB.");
      return;
    }
    
    const event = {
        target: {
            files: e.dataTransfer.files
        }
    } as unknown as React.ChangeEvent<HTMLInputElement>

    handleImageSelect(event);
  };

  const renderIconFields = () => {
    // Don't render individual fields for mockups or labels mode
    if (mode === "mockups" || mode === "labels") return null;
    
    const count = mode === "illustrations" ? 4 : 9;
    const label = mode === "illustrations" ? "Illustration" : "Icon";
    
    if (isNaN(count)) return null;

    const fields = [];
    for (let i = 0; i < count; i++) {
      fields.push(
        <input
          key={i}
          type="text"
          placeholder={`${label} ${i + 1} description (optional)`}
          value={individualDescriptions[i] || ""}
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
      <div className={mode === "illustrations" ? "grid grid-cols-2 gap-3" : "grid grid-cols-3 gap-3"}>
        {fields}
      </div>
    );
  };

  return (
    <div className="w-full lg:w-1/3 p-4 lg:p-8">
      <div
        className="bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50 h-full overflow-y-auto relative"
      >
        <div
          className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"
        ></div>
        <div className="relative z-10">
          {/* Mode Tabs */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-6">
            <button
              type="button"
              onClick={() => setMode("icons")}
              disabled={isGenerating}
              className={`px-4 py-3 rounded-lg text-sm font-semibold transition-all duration-200 ${
                mode === "icons"
                  ? "bg-gradient-to-r from-blue-600 to-purple-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              Icons
            </button>
            <button
              type="button"
              onClick={() => setMode("illustrations")}
              disabled={isGenerating}
              className={`px-4 py-3 rounded-lg text-sm font-semibold transition-all duration-200 ${
                mode === "illustrations"
                  ? "bg-gradient-to-r from-purple-600 to-pink-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              Illustrations
            </button>
            <button
              type="button"
              onClick={() => setMode("mockups")}
              disabled={isGenerating}
              className={`relative overflow-hidden px-4 py-3 rounded-lg text-sm font-semibold transition-all duration-200 ${
                mode === "mockups"
                  ? "bg-gradient-to-r from-blue-600 to-pink-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              UI
            </button>
            <button
              type="button"
              onClick={() => setMode("labels")}
              disabled={isGenerating}
              className={`px-4 py-3 rounded-lg text-sm font-semibold transition-all duration-200 ${
                mode === "labels"
                  ? "bg-gradient-to-r from-emerald-500 to-sky-500 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              Labels
            </button>
          </div>

          <h2
            className="text-2xl font-bold text-slate-900 mb-8"
          >
            {mode === "icons"
              ? "Icon Pack Generator"
              : mode === "illustrations"
              ? "Illustration Generator"
              : mode === "labels"
              ? "Label Generator"
              : "UI Mockup Generator"}
          </h2>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!isAuthenticated) {
                return;
              }
              if (isGenerating) {
                return;
              }
              generateIcons();
            }}
            className="space-y-8"
          >
            <div>
              <label
                className="block text-lg font-semibold text-slate-900 mb-6"
              >
                Choose input type
              </label>
              <div
                className="bg-slate-100 p-1.5 rounded-2xl flex"
              >
                <button
                  type="button"
                  onClick={() => setInputType("text")}
                  className={`flex-1 px-3 md:px-5 py-4 rounded-xl text-sm font-semibold transition-all duration-300 flex items-center justify-center md:space-x-2 ${inputType === "text" ? "bg-white text-slate-900 shadow-lg shadow-slate-200/50" : "text-slate-600 hover:text-slate-900 hover:bg-white/50"}`}>
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    />
                  </svg>
                    <span className="hidden md:inline">Text Description</span>
                </button>
                <button
                  type="button"
                  onClick={() => setInputType("image")}
                  className={`flex-1 px-3 md:px-5 py-4 rounded-xl text-sm font-semibold transition-all duration-300 flex items-center justify-center md:space-x-2 ${inputType === "image" ? "bg-white text-slate-900 shadow-lg shadow-slate-200/50" : "text-slate-600 hover:text-slate-900 hover:bg-white/50"}`} >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                    />
                  </svg>
                    <span className="hidden md:inline">Reference Image</span>
                </button>
            </div>
          </div>

            {mode === "labels" && (
              <div>
                <label className="block text-lg font-semibold text-slate-900 mb-4">
                  Label Text
                </label>
                <input
                  type="text"
                  value={labelText}
                  onChange={(e) => setLabelText(e.target.value)}
                  placeholder="Enter the exact text that should appear on the label"
                  className="w-full px-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-base placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent focus:bg-white transition-all duration-200"
                />
              </div>
            )}

            <div>
              {inputType === "text" ? (
                <div>
                  <label
                    className="block text-lg font-semibold text-slate-900 mb-4"
                  >
                    General Theme Description
                  </label>
                  <textarea
                    rows={5}
                    value={generalDescription}
                    onChange={(e) => setGeneralDescription(e.target.value)}
                    required={inputType === "text"}
                    className="w-full px-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-base placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition-all duration-200 resize-none"
                    placeholder={
                      mode === "illustrations" 
                        ? "Describe the general theme for your illustrations... (e.g. little fox adventures, children's book theme, etc.)" 
                        : mode === "mockups"
                        ? "Describe the style for your UI mockup... (e.g., light blue-white soft neumorphic, dark glassmorphic etc.)"
                        : mode === "labels"
                        ? "Describe the general theme for your label... (e.g., sophisticated vintage apothecary, futuristic neon tech, organic botanical line art, etc.)"
                        : "Describe the general theme for your icon pack... (e.g., minimalist business icons, colorful social media icons, etc.)"
                    }
                  />
                </div>
              ) : (
                <div>
                  <label
                    className="block text-lg font-semibold text-slate-900 mb-4"
                  >
                    Reference Image
                  </label>
                  <div 
                    className={`relative block w-full border-2 ${isDragOver ? 'border-purple-600' : 'border-dashed border-slate-300'} rounded-lg p-6 text-center cursor-pointer transition-all duration-300`}
                    onDragEnter={handleDragEnter}
                    onDragLeave={handleDragLeave}
                    onDragOver={handleDragOver}
                    onDrop={handleDrop}
                  >
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      onChange={handleImageSelect}
                      className="hidden"
                    />
                    {imagePreview ? (
                      <div>
                        <img
                          src={imagePreview}
                          alt="Reference preview"
                          className="h-32 mx-auto object-cover rounded-lg"
                        />
                        <div className="mt-4 text-sm text-slate-600">
                          <p>{referenceImage?.name}</p>
                          <p className="text-xs text-slate-400">
                            {referenceImage ? formatFileSize(referenceImage.size) : ""}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={removeImage}
                          className="mt-2 text-sm font-semibold text-red-500 hover:text-red-700"
                        >
                          Remove
                        </button>
                      </div>
                    ) : (
                      <div className="text-center">
                        <svg className="mx-auto h-12 w-12 text-slate-400" stroke="currentColor" fill="none" viewBox="0 0 48 48" aria-hidden="true">
                          <path d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                        <p className="mt-2 block text-sm font-semibold text-slate-900">
                          Drop your image here or
                        </p>
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          className="mt-2 rounded-md bg-gradient-to-r from-blue-600 to-purple-600 px-3.5 py-2.5 text-sm font-semibold text-white shadow-sm hover:from-blue-700 hover:to-purple-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                        >
                          Browse
                        </button>
                        <p className="mt-2 text-xs text-slate-500">PNG, JPG, WEBP up to 10MB</p>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            {(mode === "icons" || mode === "illustrations") && (
              <div>
                <label
                  className="block text-lg font-semibold text-slate-900 mb-4"
                >
                  Individual {mode === "icons" ? "Icon" : "Illustration"} Descriptions (Optional)
                </label>
                {renderIconFields()}
              </div>
            )}

            {mode !== "mockups" && (
              <div className="flex items-center justify-between">
                <label
                  className="text-lg font-semibold text-slate-900"
                  htmlFor="variations-switch"
                >
                  Additional Variation
                </label>
                <div className="flex items-center space-x-2">
                  {(() => {
                    const regularCoins = authState.user?.coins || 0;
                    const trialCoins = authState.user?.trialCoins || 0;
                    const isTrialOnly = regularCoins === 0 && trialCoins > 0;
                    
                    if (isTrialOnly) {
                      return (
                        <span className="flex items-center space-x-1 rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700">
                          <span>Not available with trial coin</span>
                        </span>
                      );
                    } else if (generateVariations) {
                      return (
                        <span className="flex items-center space-x-1 rounded-full bg-gray-200 px-2 py-0.5 text-xs font-semibold text-gray-700">
                          <span>+1</span>
                          <Image
                            src="/images/coin.webp"
                            alt="Coin"
                            width={16}
                            height={16}
                          />
                        </span>
                      );
                    }
                    return null;
                  })()}
                  <button
                    id="variations-switch"
                    type="button"
                    role="switch"
                    aria-checked={generateVariations}
                    disabled={(() => {
                      const regularCoins = authState.user?.coins || 0;
                      const trialCoins = authState.user?.trialCoins || 0;
                      return regularCoins === 0 && trialCoins > 0;
                    })()}
                    onClick={() => {
                      const regularCoins = authState.user?.coins || 0;
                      const trialCoins = authState.user?.trialCoins || 0;
                      const isTrialOnly = regularCoins === 0 && trialCoins > 0;
                      
                      if (!isTrialOnly) {
                        setGenerateVariations(!generateVariations);
                      }
                    }}
                    className={`${
                      (() => {
                        const regularCoins = authState.user?.coins || 0;
                        const trialCoins = authState.user?.trialCoins || 0;
                        const isTrialOnly = regularCoins === 0 && trialCoins > 0;
                        
                        if (isTrialOnly) {
                          return 'bg-gray-300 cursor-not-allowed';
                        }
                        return generateVariations ? 'bg-purple-600' : 'bg-gray-200';
                      })()
                    } relative inline-flex h-6 w-11 flex-shrink-0 rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2`}
                  >
                    <span
                      aria-hidden="true"
                      className={`${
                        generateVariations ? 'translate-x-5' : 'translate-x-0'
                      } pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out`}
                    />
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={isGenerating || !isAuthenticated}
              onClick={() => {}}
              className={`w-full py-4 px-6 rounded-2xl text-white font-semibold ${
                isGenerating || !isAuthenticated
                  ? "bg-slate-400 cursor-not-allowed"
                  : mode === "illustrations"
                  ? "bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
                  : mode === "mockups"
                  ? "bg-gradient-to-r from-blue-600 to-pink-600 hover:from-blue-700 hover:to-pink-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
                        : mode === "labels"
                  ? "bg-gradient-to-r from-emerald-500 to-sky-500 hover:from-emerald-600 hover:to-sky-600 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
                  : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
              } transition-all duration-200`}
            >
              {isGenerating ? (
                <div
                  className="flex items-center justify-center space-x-2"
                >
                  <div
                    className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"
                  ></div>
                  <span>Generating...</span>
                </div>
              ) : (
                <div className="flex items-center justify-center space-x-2">
                  <span>
                    {isAuthenticated 
                      ? (mode === "icons" ? "Generate Icons" : mode === "illustrations" ? "Generate Illustrations" : "Generate UI Mockup")
                      : "Sign in to Generate"}
                  </span>
                  {isAuthenticated && authState.user && (
                    <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                      {(() => {
                        // For mockups, cost is always 1 (variations don't cost extra)
                        const cost = mode === "mockups" ? 1 : (generateVariations ? 2 : 1);
                        const regularCoins = authState.user.coins || 0;
                        const trialCoins = authState.user.trialCoins || 0;
                        
                        // Prioritize regular coins (same logic as backend)
                        if (regularCoins >= cost) {
                          return (
                            <>
                              <Image
                                src="/images/coin.webp"
                                alt="Coins"
                                width={16}
                                height={16}
                              />
                              <span>{cost}</span>
                            </>
                          );
                        } else if (trialCoins > 0) {
                          return (
                            <>
                              <div className="w-4 h-4 bg-green-500 rounded-full flex items-center justify-center">
                                <span className="text-xs font-bold text-white">T</span>
                              </div>
                              <span>Trial</span>
                            </>
                          );
                        } else {
                          // Fallback - show required coins even if user doesn't have them
                          return (
                            <>
                              <Image
                                src="/images/coin.webp"
                                alt="Coins"
                                width={16}
                                height={16}
                              />
                              <span>{cost}</span>
                            </>
                          );
                        }
                      })()}
                    </span>
                  )}
                </div>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default GeneratorForm;
