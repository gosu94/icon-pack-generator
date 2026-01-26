import React, { useState, useEffect, useRef, useCallback } from "react";
import { createPortal } from "react-dom";
import Image from "next/image";
import Link from "next/link";
import { useAuth } from "../context/AuthContext";
import { GenerationMode } from "@/lib/types";
import ImageCropper from "./ImageCropper";
import type { CropRect } from "@/app/dashboard/hooks/useDashboardFormState";

interface GeneratorFormProps {
  mode: GenerationMode;
  setMode: (value: GenerationMode) => void;
  generateForMode: (value: GenerationMode) => void;
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
  uiReferenceImage: File | null;
  uiImagePreview: string;
  uiCrop: CropRect | null;
  setUiCrop: (value: CropRect | null) => void;
  isGenerating: boolean;
  handleImageSelect: (event: React.ChangeEvent<HTMLInputElement>) => void;
  removeImage: () => void;
  fileInputRef: React.RefObject<HTMLInputElement>;
  uiFileInputRef: React.RefObject<HTMLInputElement>;
  handleUiImageSelect: (event: React.ChangeEvent<HTMLInputElement>) => void;
  handleUiImageFromGallery: (imageUrl: string, fileName?: string) => Promise<void>;
  removeUiImage: () => void;
  formatFileSize: (bytes: number) => string;
  enhancePrompt: boolean;
  setEnhancePrompt: (value: boolean) => void;
  baseModel: string;
  setBaseModel: (value: string) => void;
  variationModel: string;
  setVariationModel: (value: string) => void;
}

const GeneratorForm: React.FC<GeneratorFormProps> = ({
  mode,
  setMode,
  generateForMode,
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
  uiReferenceImage,
  uiImagePreview,
  uiCrop,
  setUiCrop,
  isGenerating,
  handleImageSelect,
  removeImage,
  fileInputRef,
  uiFileInputRef,
  handleUiImageSelect,
  handleUiImageFromGallery,
  removeUiImage,
  formatFileSize,
  enhancePrompt,
  setEnhancePrompt,
  baseModel,
  setBaseModel,
  variationModel,
  setVariationModel,
}) => {
  const { authState } = useAuth();
  const [isDragOver, setIsDragOver] = useState(false);
  const isAuthenticated = authState.authenticated;
  const regularCoins = authState.user?.coins ?? 0;
  const trialCoins = authState.user?.trialCoins ?? 0;
  const isTrialOnly = regularCoins === 0 && trialCoins > 0;
  const showReferenceBanner =
    inputType === "image" && (mode === "icons" || mode === "illustrations");
  const isMockupTab = mode === "mockups" || mode === "ui-elements";
  const isUiElementsMode = mode === "ui-elements";
  const isMockupsMode = mode === "mockups";
  const [uiModal, setUiModal] = useState<"none" | "gallery" | "crop">("none");
  const enableUiModalDebug = true;
  const previousUiPreviewRef = useRef<string>("");
  const lastUiModalOpenAtRef = useRef(0);
  const isUiGalleryOpen = uiModal === "gallery";
  const isUiCropOpen = uiModal === "crop";
  const [uiGalleryItems, setUiGalleryItems] = useState<
    Array<{
      id: number;
      imageUrl: string;
      description?: string;
      mockupType?: string;
      requestId?: string;
    }>
  >([]);
  const [uiGalleryLoading, setUiGalleryLoading] = useState(false);
  const [uiGalleryError, setUiGalleryError] = useState("");
  const [isClient, setIsClient] = useState(false);

  // Automatically disable variations when user only has trial coins
  useEffect(() => {
    if (isTrialOnly && generateVariations) {
      setGenerateVariations(false);
    }
  }, [isTrialOnly, generateVariations, setGenerateVariations]);

  useEffect(() => {
    setIsClient(true);
  }, []);

  useEffect(() => {
    const previousPreview = previousUiPreviewRef.current;
    if (uiImagePreview && uiImagePreview !== previousPreview && !isUiGalleryOpen) {
      lastUiModalOpenAtRef.current = Date.now();
      if (enableUiModalDebug) {
        console.debug("[ui-modal] auto-open crop", {
          previousPreview,
          nextPreview: uiImagePreview,
          uiModal,
        });
      }
      setUiModal("crop");
    }
    if (!uiImagePreview && uiModal === "crop") {
      if (enableUiModalDebug) {
        console.debug("[ui-modal] clear preview -> close crop modal");
      }
      setUiModal("none");
    }
    previousUiPreviewRef.current = uiImagePreview;
  }, [uiImagePreview, isUiGalleryOpen]);

  const fetchUiGalleryItems = useCallback(async () => {
    if (uiGalleryLoading) {
      return;
    }
    setUiGalleryLoading(true);
    setUiGalleryError("");
    try {
      const response = await fetch("/api/gallery/mockups", {
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error("Failed to fetch mockups.");
      }
      const data = await response.json();
      const filtered = Array.isArray(data)
        ? data.filter((item) => item?.mockupType !== "elements")
        : [];
      setUiGalleryItems(filtered);
    } catch (error) {
      console.error("Failed to fetch mockups gallery", error);
      setUiGalleryError("Failed to load mockups. Please try again.");
    } finally {
      setUiGalleryLoading(false);
    }
  }, [uiGalleryLoading]);

  useEffect(() => {
    if (isUiGalleryOpen) {
      void fetchUiGalleryItems();
    }
  }, [isUiGalleryOpen, fetchUiGalleryItems]);

  const renderPortal = useCallback(
    (content: React.ReactNode) =>
      isClient ? createPortal(content, document.body) : null,
    [isClient],
  );

  const openUiModal = useCallback((next: "gallery" | "crop") => {
    lastUiModalOpenAtRef.current = Date.now();
    if (enableUiModalDebug) {
      console.debug("[ui-modal] open", { next, uiModal });
    }
    setUiModal(next);
  }, [enableUiModalDebug, uiModal]);

  const closeUiModal = useCallback((force = false) => {
    if (!force) {
      const elapsed = Date.now() - lastUiModalOpenAtRef.current;
      if (elapsed < 200) {
        if (enableUiModalDebug) {
          console.debug("[ui-modal] ignore close (debounced)", { elapsed, uiModal });
        }
        return;
      }
    }
    if (enableUiModalDebug) {
      console.debug("[ui-modal] close", { force, uiModal });
    }
    setUiModal("none");
  }, [enableUiModalDebug, uiModal]);

  useEffect(() => {
    if (enableUiModalDebug) {
      console.debug("[ui-modal] state", uiModal);
    }
  }, [uiModal, enableUiModalDebug]);

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
    if (isMockupTab || mode === "labels") return null;
    
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
        className="bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50 relative lg:h-full lg:overflow-y-auto"
      >
        <div
          className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"
        ></div>
        <div className="relative z-10">
          {/* Mode Tabs */}
          <div className="grid grid-cols-4 gap-3 mb-6">
            <button
              type="button"
              onClick={() => setMode("icons")}
              disabled={isGenerating}
              title="Icons"
              className={`px-3 py-3 rounded-lg transition-all duration-200 flex items-center justify-center ${
                mode === "icons"
                  ? "bg-gradient-to-r from-blue-600 to-purple-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              <svg
                className="w-5 h-5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1v-4zM14 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z"
                />
              </svg>
            </button>
            <button
              type="button"
              onClick={() => setMode("illustrations")}
              disabled={isGenerating}
              title="Illustrations"
              className={`px-3 py-3 rounded-lg transition-all duration-200 flex items-center justify-center ${
                mode === "illustrations"
                  ? "bg-gradient-to-r from-purple-600 to-pink-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              <svg
                className="w-5 h-5"
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
            </button>
            <button
              type="button"
              onClick={() => setMode("mockups")}
              disabled={isGenerating}
              title="UI"
              className={`px-3 py-3 rounded-lg transition-all duration-200 flex items-center justify-center ${
                isMockupTab
                  ? "bg-gradient-to-r from-blue-600 to-pink-600 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              <svg
                className="w-5 h-5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2"
                />
              </svg>
            </button>
            <button
              type="button"
              onClick={() => setMode("labels")}
              disabled={isGenerating}
              title="Labels"
              className={`px-3 py-3 rounded-lg transition-all duration-200 flex items-center justify-center ${
                mode === "labels"
                  ? "bg-gradient-to-r from-emerald-500 to-sky-500 text-white shadow-md"
                  : isGenerating
                  ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              <svg
                className="w-5 h-5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
                />
              </svg>
            </button>
          </div>

          {isMockupTab ? (
            <div className="mb-8 flex justify-center">
              <div className="inline-flex rounded-2xl border border-slate-200 bg-white p-1 shadow-sm">
                <button
                  type="button"
                  onClick={() => setMode("mockups")}
                  disabled={isGenerating}
                  className={`px-5 py-2 text-sm font-semibold rounded-xl transition-all ${
                    mode === "mockups"
                      ? "bg-slate-900 text-white shadow"
                      : isGenerating
                      ? "text-slate-400 cursor-not-allowed"
                      : "text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                  }`}
                >
                  Mockups
                </button>
                <button
                  type="button"
                  onClick={() => setMode("ui-elements")}
                  disabled={isGenerating}
                  className={`px-5 py-2 text-sm font-semibold rounded-xl transition-all ${
                    mode === "ui-elements"
                      ? "bg-slate-900 text-white shadow"
                      : isGenerating
                      ? "text-slate-400 cursor-not-allowed"
                      : "text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                  }`}
                >
                  Elements
                </button>
              </div>
            </div>
          ) : (
            <h2 className="text-2xl font-bold text-slate-900 mb-8">
              {mode === "icons"
                ? "Icon Pack Generator"
                : mode === "illustrations"
                ? "Illustration Generator"
                : "Label Generator"}
            </h2>
          )}

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!isAuthenticated || isGenerating) {
                return;
              }
              const targetMode = isMockupTab ? "mockups" : mode;
              generateForMode(targetMode);
            }}
            className="space-y-8"
          >
            {!isUiElementsMode && (
              <div>
                <div className="bg-slate-100 p-1.5 rounded-2xl flex">
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
                      <span className="hidden md:inline">Text</span>
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
                      <span className="hidden md:inline">Image</span>
                  </button>
                </div>
              </div>
            )}

            {!isUiElementsMode && mode === "labels" && (
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

            {!isUiElementsMode && (
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
                          : isMockupsMode
                          ? "Describe the style for your UI mockup... (e.g., light blue-white soft neumorphic, dark glassmorphic etc.)"
                          : mode === "labels"
                          ? "Describe the general theme for your label... (e.g., sophisticated vintage apothecary, futuristic neon tech, organic botanical line art, etc.)"
                          : "Describe the general theme for your icon pack... (e.g., minimalist business icons, colorful social media icons, etc.)"
                      }
                    />
                    {mode === "icons" && (
                      <label
                        className={`mt-3 flex items-center justify-between gap-3 rounded-xl border ${
                          enhancePrompt ? "border-blue-200 bg-white shadow-sm" : "border-slate-200 bg-white/80"
                        } px-3 py-2 transition-all duration-200 cursor-pointer select-none`}
                      >
                        <div className="text-xs leading-tight">
                          <span className="block text-sm font-semibold text-slate-900">Enhance prompt</span>
                          <span className="text-slate-500">
                            Let our AI model expand your theme with rich stylistic details
                          </span>
                        </div>
                        <div className="relative inline-flex items-center">
                          <input
                            type="checkbox"
                            className="sr-only peer"
                            checked={enhancePrompt}
                            onChange={(e) => setEnhancePrompt(e.target.checked)}
                            disabled={isGenerating}
                          />
                          <div
                            className={`h-6 w-11 rounded-full transition-all duration-200 ${
                              enhancePrompt ? "bg-blue-600" : "bg-slate-300"
                            } ${isGenerating ? "opacity-50" : ""}`}
                          ></div>
                          <div
                            className={`absolute left-1 top-1 h-4 w-4 rounded-full bg-white transition-transform duration-200 ${
                              enhancePrompt ? "translate-x-5" : ""
                            }`}
                          ></div>
                        </div>
                      </label>
                    )}
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
                    {showReferenceBanner && (
                      <div className="mt-4 flex items-start gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-700">
                        <svg
                          className="mt-0.5 h-5 w-5 flex-shrink-0 text-blue-500"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                          />
                        </svg>
                        <p>
                          For clearer {mode === "illustrations" ? "illustrations" : "icons"}, add individual descriptions alongside your reference image so each result stays distinct.
                        </p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {isUiElementsMode && (
              <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 space-y-4">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">
                    Generate UI
                  </h3>
                  <p className="text-sm text-slate-500">
                    Upload a UI screenshot and crop the area to extract reusable UI elements.
                  </p>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-slate-800 mb-2">
                    UI Reference Image
                  </label>
                  <div className="rounded-lg border border-dashed border-slate-300 bg-white px-4 py-4">
                    <input
                      ref={uiFileInputRef}
                      type="file"
                      accept="image/*"
                      onChange={handleUiImageSelect}
                      className="hidden"
                    />
                    {!uiImagePreview ? (
                      <div className="flex flex-col items-center text-center gap-3">
                        <p className="text-sm text-slate-600">
                          Drop a UI screenshot here or browse.
                        </p>
                        <div className="flex flex-wrap justify-center gap-3">
                          <button
                            type="button"
                            onClick={() => uiFileInputRef.current?.click()}
                            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                          >
                            Browse
                          </button>
                          {isAuthenticated && (
                            <button
                              type="button"
                              onClick={() => openUiModal("gallery")}
                              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                            >
                              Choose from gallery
                            </button>
                          )}
                        </div>
                        <p className="text-xs text-slate-400">
                          PNG, JPG, WEBP up to 10MB
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        <div className="flex items-center justify-between text-sm text-slate-600">
                          <span>{uiReferenceImage?.name}</span>
                          <span className="text-xs text-slate-400">
                            {uiReferenceImage ? formatFileSize(uiReferenceImage.size) : ""}
                          </span>
                        </div>
                        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
                          <div className="flex items-center gap-3">
                            <div className="h-16 w-24 overflow-hidden rounded-md border border-slate-200 bg-white">
                              {uiImagePreview && (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img
                                  src={uiImagePreview}
                                  alt="UI reference preview"
                                  className="h-full w-full object-cover"
                                />
                              )}
                            </div>
                            <div className="text-xs text-slate-500">
                              <p className="font-semibold text-slate-700">
                                {uiCrop ? "Crop selection ready" : "Crop selection not set"}
                              </p>
                              <p>Select a crop with up to 4 elements.</p>
                            </div>
                          </div>
                          <div className="flex items-center justify-between">
                            <button
                              type="button"
                              onClick={() => openUiModal("crop")}
                              className="text-sm font-semibold text-slate-900 hover:text-slate-700"
                            >
                              {uiCrop ? "Edit crop selection" : "Set crop selection"}
                            </button>
                            <button
                              type="button"
                              onClick={() => setUiCrop(null)}
                              className="text-xs font-semibold text-purple-600 hover:text-purple-700"
                            >
                              Clear selection
                            </button>
                          </div>
                        </div>
                        <div className="flex flex-wrap items-center gap-3">
                          {isAuthenticated && (
                            <button
                              type="button"
                              onClick={() => openUiModal("gallery")}
                              className="text-sm font-semibold text-slate-700 hover:text-slate-900"
                            >
                              Choose another from gallery
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={removeUiImage}
                            className="text-sm font-semibold text-red-500 hover:text-red-700"
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                <button
                  type="button"
                  disabled={isGenerating || !isAuthenticated || !uiReferenceImage}
                  onClick={() => generateForMode("ui-elements")}
                  className={`w-full py-3 px-5 rounded-xl text-white font-semibold ${
                    isGenerating || !isAuthenticated || !uiReferenceImage
                      ? "bg-slate-400 cursor-not-allowed"
                      : "bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
                  } transition-all duration-200`}
                >
                  <div className="flex items-center justify-center space-x-2">
                    <span>
                      {isAuthenticated ? "Generate UI Elements" : "Sign in to Generate"}
                    </span>
                    {isAuthenticated && authState.user && (
                      <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                        {(() => {
                          const cost = 1;
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
                          }
                          if (trialCoins > 0) {
                            return (
                              <>
                                <div className="w-4 h-4 bg-green-500 rounded-full flex items-center justify-center">
                                  <span className="text-xs font-bold text-white">T</span>
                                </div>
                                <span>Trial</span>
                              </>
                            );
                          }
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
                        })()}
                      </span>
                    )}
                  </div>
                </button>
              </div>
            )}

            {isUiCropOpen &&
              uiImagePreview &&
              renderPortal(
                <div
                  className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 px-4 py-8"
                  onClick={() => closeUiModal()}
                >
                  <div
                    className="w-full max-w-3xl rounded-2xl bg-white shadow-2xl"
                    onClick={(event) => event.stopPropagation()}
                  >
                    <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
                      <div>
                        <h4 className="text-lg font-semibold text-slate-900">
                          Crop UI Elements
                        </h4>
                        <p className="text-sm text-slate-500">
                          Select a rectangle containing up to 4 elements for best results.
                        </p>
                        <p className="text-xs text-slate-400 mt-1">
                          Use a background color different from the elements so they don&apos;t get treated as transparent.
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => closeUiModal(true)}
                        className="text-sm font-semibold text-slate-500 hover:text-slate-700"
                      >
                        Close
                      </button>
                    </div>
                    <div className="px-6 py-5 space-y-3">
                      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                        <ImageCropper
                          src={uiImagePreview}
                          crop={uiCrop}
                          onCropChange={setUiCrop}
                          disabled={isGenerating}
                        />
                      </div>
                      <div className="flex items-center justify-between text-xs text-slate-500">
                        <span>
                          Drag to select the crop area. Leave some space around each
                          element.
                        </span>
                        <button
                          type="button"
                          onClick={() => setUiCrop(null)}
                          className="text-purple-600 hover:text-purple-700 font-semibold"
                        >
                          Clear selection
                        </button>
                      </div>
                    </div>
                    <div className="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4">
                      <button
                        type="button"
                        onClick={() => closeUiModal(true)}
                        className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                      >
                        Done
                      </button>
                    </div>
                  </div>
                </div>,
              )}

            {isUiGalleryOpen &&
              renderPortal(
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 px-4 py-8">
                  <div className="w-full max-w-4xl rounded-2xl bg-white shadow-2xl">
                    <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
                      <div>
                        <h4 className="text-lg font-semibold text-slate-900">
                          Choose a Mockup
                        </h4>
                        <p className="text-sm text-slate-500">
                          Pick a mockup from your gallery to extract UI elements.
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => closeUiModal(true)}
                        className="text-sm font-semibold text-slate-500 hover:text-slate-700"
                      >
                        Close
                      </button>
                    </div>
                    <div className="px-6 py-5">
                      {uiGalleryLoading ? (
                        <p className="text-sm text-slate-500">Loading mockups...</p>
                      ) : uiGalleryError ? (
                        <p className="text-sm text-red-500">{uiGalleryError}</p>
                      ) : uiGalleryItems.length === 0 ? (
                        <p className="text-sm text-slate-500">
                          No mockups available yet. Generate a mockup first.
                        </p>
                      ) : (
                        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                          {uiGalleryItems.map((item) => (
                            <button
                              key={`${item.id}-${item.imageUrl}`}
                              type="button"
                              onClick={() => {
                                openUiModal("crop");
                                void handleUiImageFromGallery(
                                  item.imageUrl,
                                  item.description || "mockup.png",
                                );
                              }}
                              className="group flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white text-left shadow-sm transition hover:border-slate-300 hover:shadow-md"
                            >
                              <div className="aspect-[4/3] w-full overflow-hidden bg-slate-50">
                                {/* eslint-disable-next-line @next/next/no-img-element */}
                                <img
                                  src={item.imageUrl}
                                  alt={item.description || "Mockup"}
                                  className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
                                />
                              </div>
                              <div className="flex flex-col gap-1 p-3">
                                <span className="text-xs font-semibold text-slate-700">
                                  {item.description || "Untitled mockup"}
                                </span>
                                <span className="text-[11px] uppercase tracking-wide text-slate-400">
                                  {item.mockupType || "mockup"}
                                </span>
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4">
                      <button
                        type="button"
                        onClick={() => closeUiModal(true)}
                        className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                      >
                        Done
                      </button>
                    </div>
                  </div>
                </div>,
              )}

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

            {mode === "icons" && inputType !== "image" && (
              <div className="flex items-center justify-between">
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <label className="text-lg font-semibold text-slate-900">
                      Generation model
                    </label>
                    <div className="relative group">
                      <button
                        type="button"
                        className="text-slate-500 hover:text-slate-800 transition-colors"
                        aria-label="More info about models"
                      >
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
                            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                          />
                        </svg>
                      </button>
                      <div className="absolute left-1/2 top-6 z-20 w-80 max-w-xs -translate-x-1/2 rounded-xl border border-slate-200 bg-white p-4 text-xs text-slate-600 shadow-xl opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-200">
                        <p className="font-semibold text-slate-900 mb-2 text-sm">
                          Standard vs Pro
                        </p>
                        <p className="mb-3">
                          Standard produces simpler, cleaner icons. Pro adds
                          richer materials, deeper contrast, and more refined
                          lighting for premium polish.
                        </p>
                        <div className="grid grid-cols-2 gap-3">
                          <div className="text-center">
                            <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                              Standard
                            </span>
                            <Image
                              src="/images/new-model/old_icon1.webp"
                              alt="Standard model sample"
                              width={120}
                              height={120}
                              className="mx-auto mt-1 rounded-lg border border-slate-200 object-cover"
                            />
                          </div>
                          <div className="text-center">
                            <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                              Pro
                            </span>
                            <Image
                              src="/images/new-model/new_icon1.webp"
                              alt="Pro model sample"
                              width={120}
                              height={120}
                              className="mx-auto mt-1 rounded-lg border border-slate-200 object-cover"
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <select
                  value={baseModel}
                  onChange={(e) => setBaseModel(e.target.value)}
                  disabled={isGenerating}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-purple-400 focus:outline-none focus:ring-2 focus:ring-purple-200 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <option value="standard">Standard</option>
                  <option value="pro">Pro</option>
                </select>
              </div>
            )}

            {!isMockupTab && (
              <div className="flex items-center justify-between">
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <label
                      className="text-lg font-semibold text-slate-900"
                      htmlFor="variations-switch"
                    >
                      Additional Variations
                    </label>
                  </div>
                </div>
                <div className="flex items-center space-x-2">
                  {isTrialOnly ? (
                    <span className="flex items-center space-x-1 rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700">
                      <span>Not available with trial coin</span>
                    </span>
                  ) : (
                    generateVariations && (
                      <span className="flex items-center space-x-1 rounded-full bg-gray-200 px-2 py-0.5 text-xs font-semibold text-gray-700">
                        <span>+1</span>
                        <Image
                          src="/images/coin.webp"
                          alt="Coin"
                          width={16}
                          height={16}
                        />
                      </span>
                    )
                  )}
                  <button
                    id="variations-switch"
                    type="button"
                    role="switch"
                    aria-checked={generateVariations}
                    disabled={isTrialOnly}
                    onClick={() => {
                      if (!isTrialOnly) {
                        setGenerateVariations(!generateVariations);
                      }
                    }}
                    className={`${
                      isTrialOnly
                        ? "bg-gray-300 cursor-not-allowed"
                        : generateVariations
                        ? "bg-purple-600"
                        : "bg-gray-200"
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

            {mode === "icons" && generateVariations && !isTrialOnly && inputType !== "image" && (
              <div className="mt-3 flex items-center justify-between rounded-xl border border-slate-200 bg-white/70 px-3 py-2">
                <span className="text-sm font-semibold text-slate-800">
                  Additional variations model
                </span>
                <select
                  value={variationModel}
                  onChange={(e) => setVariationModel(e.target.value)}
                  disabled={isGenerating}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-purple-400 focus:outline-none focus:ring-2 focus:ring-purple-200 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <option value="standard">Standard</option>
                  <option value="pro">Pro</option>
                </select>
              </div>
            )}

            {!isUiElementsMode && (
              <>
                {isTrialOnly && (mode === "icons" || mode === "illustrations") && (
                  <div className="flex items-start gap-2 rounded-xl border border-blue-200/80 bg-blue-50/80 px-3 py-3 text-xs text-blue-900">
                    <svg
                      className="mt-0.5 h-4 w-4 flex-shrink-0 text-blue-500"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                      />
                    </svg>
                    <div>
                      <p className="text-xs font-semibold text-blue-900">
                        Trial coin limitation
                      </p>
                      <p className="text-[11px] text-blue-800">
                        {mode === "icons"
                          ? "Trial generations include a watermark and are stored for 30 days only. "
                          : "Trial generations include a watermark and are stored for 30 days only. "}
                          Visit the{" "}
                          <Link
                              href="/store"
                              className="font-semibold underline decoration-dotted underline-offset-2 hover:text-blue-900"
                          >
                              store
                          </Link>{" "}
                          to buy coins and remove it in your gallery.
                      </p>
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
                      : isMockupTab
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
                          ? (mode === "icons" ? "Generate Icons" : mode === "illustrations" ? "Generate Illustrations" : mode === "labels" ? "Generate Label" : "Generate UI Mockup")
                          : "Sign in to Generate"}
                      </span>
                      {isAuthenticated && authState.user && (
                        <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                          {(() => {
                            // For mockups, cost is always 1 (variations don't cost extra)
                            const cost = isMockupTab ? 1 : (generateVariations ? 2 : 1);

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
              </>
            )}
          </form>
        </div>
      </div>
    </div>
  );
};

export default GeneratorForm;
