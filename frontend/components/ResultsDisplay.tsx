import React, { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { Download } from "lucide-react";
import GifModal, { GifModalProgress } from "./GifModal";
import {
  UIState,
  ServiceResult,
  GenerationResponse,
  GenerationMode,
  GifAsset,
  GifProgressUpdate,
  Icon,
} from "../lib/types";

interface ResultsDisplayProps {
  mode: GenerationMode;
  uiState: UIState;
  generateVariations: boolean;
  isGenerating: boolean;
  overallProgress: number;
  calculateTimeRemaining: () => string;
  errorMessage: string;
  streamingResults: { [key: string]: ServiceResult };
  showResultsPanes: boolean;
  getIconAnimationClass: (serviceId: string, iconIndex: number) => string;
  animatingIcons: { [key: string]: number };
  exportGeneration: (
    requestId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  currentResponse: GenerationResponse | null;
  isTrialResult: boolean;
  moreIconsVisible: { [key: string]: boolean };
  showMoreIconsForm: (uniqueId: string) => void;
  hideMoreIconsForm: (uniqueId: string) => void;
  generateMoreIcons: (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  generateMoreIllustrations: (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  moreIconsDescriptions: { [key: string]: string[] };
  setMoreIconsDescriptions: React.Dispatch<
    React.SetStateAction<{ [key: string]: string[] }>
  >;
  getServiceDisplayName: (serviceId: string) => string;
  setIsGenerating: (isGenerating: boolean) => void;
  setMode: (mode: GenerationMode) => void;
  setInputType: (inputType: string) => void;
  setReferenceImage: (file: File | null) => void;
  setImagePreview: (preview: string) => void;
  availableCoins: number;
  trialCoins: number;
  gifRefreshToken: number;
}

interface GifModalState {
  isOpen: boolean;
  serviceId: string;
  serviceName: string;
  generationIndex: number;
  icons: Icon[];
  requestId: string;
}

const ResultsDisplay: React.FC<ResultsDisplayProps> = ({
  mode,
  uiState,
  generateVariations,
  isGenerating,
  overallProgress,
  calculateTimeRemaining,
  errorMessage,
  streamingResults,
  showResultsPanes,
  getIconAnimationClass,
  animatingIcons,
  exportGeneration,
  currentResponse,
  isTrialResult,
  moreIconsVisible,
  showMoreIconsForm,
  hideMoreIconsForm,
  generateMoreIcons,
  generateMoreIllustrations,
  moreIconsDescriptions,
  setMoreIconsDescriptions,
  getServiceDisplayName,
  setIsGenerating,
  setMode,
  setInputType,
  setReferenceImage,
  setImagePreview,
  availableCoins,
  trialCoins,
  gifRefreshToken,
}) => {
  // State for full-size image preview modal
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [gifModalState, setGifModalState] = useState<GifModalState | null>(null);
  const [selectedGifIcons, setSelectedGifIcons] = useState<Set<string>>(
    () => new Set<string>(),
  );
  const [gifError, setGifError] = useState<string | null>(null);
  const [gifProgress, setGifProgress] = useState<GifModalProgress>({
    status: "idle",
    message: "",
    total: 0,
    completed: 0,
    percent: 0,
  });
  const [gifResults, setGifResults] = useState<GifAsset[]>([]);
  const [isGifSubmitting, setIsGifSubmitting] = useState(false);
  const gifEventSourceRef = useRef<EventSource | null>(null);
  const actionButtonBaseClass =
    "px-2 sm:px-4 py-2 bg-[#ffffff] text-[#3C4BFF] font-medium rounded-2xl shadow-sm hover:shadow-md transition-all flex items-center justify-center border border-[#E6E8FF] hover:bg-[#F5F6FF] active:shadow-sm focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40";
  const gifSelectedCount = selectedGifIcons.size;
  const gifEstimatedCost = gifSelectedCount * 2;
  const insufficientGifBalance =
    gifEstimatedCost > 0 && availableCoins < gifEstimatedCost && trialCoins <= 0;

  const getGenerationResults = (generationNumber: number) => {
    return Object.entries(streamingResults)
      .filter(([, result]) => result.generationIndex === generationNumber)
      .map(([serviceId, result]) => ({ serviceId, ...result }));
  };

  const handleImageClick = (base64Data: string) => {
    if (mode === "illustrations" || mode === "mockups" || mode === "labels") {
      setPreviewImage(base64Data);
    }
  };

  const closePreview = () => {
    setPreviewImage(null);
  };

  const cleanupGifEventSource = () => {
    if (gifEventSourceRef.current) {
      gifEventSourceRef.current.close();
      gifEventSourceRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      cleanupGifEventSource();
    };
  }, []);

  const resetGifState = () => {
    setSelectedGifIcons(new Set<string>());
    setGifProgress({
      status: "idle",
      message: "",
      total: 0,
      completed: 0,
      percent: 0,
    });
    setGifResults([]);
    setGifError(null);
    setIsGifSubmitting(false);
  };

  const closeGifModal = () => {
    resetGifState();
    cleanupGifEventSource();
    setGifModalState(null);
  };

  const appendGifAssets = (assets?: GifAsset[]) => {
    if (!assets || assets.length === 0) {
      return;
    }
    setGifResults((prev) => {
      const merged = new Map<string, GifAsset>();
      prev.forEach((asset) => {
        if (asset.iconId) {
          merged.set(asset.iconId, asset);
        } else {
          merged.set(`${asset.filePath}-${asset.fileName}`, asset);
        }
      });
      assets.forEach((asset) => {
        if (asset.iconId) {
          merged.set(asset.iconId, asset);
        }
      });
      return Array.from(merged.values());
    });
  };

  const handleGifUpdate = (update: GifProgressUpdate) => {
    const total = update.totalIcons || gifModalState?.icons.length || gifProgress.total;
    const completed = typeof update.completedIcons === "number" ? update.completedIcons : gifProgress.completed;
    const percent = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : gifProgress.percent;
    setGifProgress({
      status: update.status,
      message: update.message,
      total,
      completed,
      percent,
    });
    appendGifAssets(update.gifs);
  };

  const attachGifEventSource = (gifRequestId: string) => {
    cleanupGifEventSource();
    const eventSource = new EventSource(`/api/icons/gif/stream/${gifRequestId}`);
    gifEventSourceRef.current = eventSource;

    eventSource.addEventListener("gif_progress", (event) => {
      try {
        handleGifUpdate(JSON.parse(event.data));
      } catch (error) {
        console.error("Failed to parse GIF progress event", error);
      }
    });

    eventSource.addEventListener("gif_complete", (event) => {
      try {
        handleGifUpdate(JSON.parse(event.data));
        setIsGifSubmitting(false);
      } catch (error) {
        console.error("Failed to parse GIF completion event", error);
      } finally {
        cleanupGifEventSource();
      }
    });

    eventSource.addEventListener("gif_error", (event) => {
      try {
        const update: GifProgressUpdate = JSON.parse(event.data);
        setGifError(update.message || "Failed to generate GIFs. Please try again.");
        handleGifUpdate(update);
      } catch (error) {
        setGifError("Failed to generate GIFs. Please try again.");
      } finally {
        setIsGifSubmitting(false);
        cleanupGifEventSource();
      }
    });

    eventSource.onerror = () => {
      setGifError("GIF generation connection lost. Please try again.");
      setIsGifSubmitting(false);
      cleanupGifEventSource();
    };
  };

  const openGifModal = (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
    icons: Icon[],
  ) => {
    if (!currentResponse?.requestId) {
      alert("GIF generation is only available after the request completes.");
      return;
    }
    resetGifState();
    setGifModalState({
      isOpen: true,
      serviceId,
      serviceName,
      generationIndex,
      icons,
      requestId: currentResponse.requestId,
    });
  };

  const toggleGifSelection = (iconId: string) => {
    setSelectedGifIcons((prev) => {
      const next = new Set(prev);
      if (next.has(iconId)) {
        next.delete(iconId);
      } else {
        next.add(iconId);
      }
      return next;
    });
  };

  const handleGenerateGifs = async () => {
    if (!gifModalState) {
      setGifError("Please pick a generation first.");
      return;
    }
    if (selectedGifIcons.size === 0) {
      setGifError("Select at least one icon to animate.");
      return;
    }

    setIsGifSubmitting(true);
    setGifError(null);
    setGifProgress((prev) => ({
      ...prev,
      status: "starting",
      message: "Submitting GIF generation request...",
      total: selectedGifIcons.size,
      percent: 5,
    }));

    try {
      const response = await fetch("/api/icons/gif/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          requestId: gifModalState.requestId,
          serviceName: gifModalState.serviceId,
          generationIndex: gifModalState.generationIndex,
          iconIds: Array.from(selectedGifIcons),
        }),
      });
      const payload = await response.json().catch(() => ({}));

      if (!response.ok || payload.status === "error" || !payload.gifRequestId) {
        const message = payload.message || "Failed to start GIF generation.";
        setGifError(message);
        setIsGifSubmitting(false);
        return;
      }

      setGifProgress((prev) => ({
        ...prev,
        status: "in_progress",
        message: "Waiting for animation service...",
        total: payload.totalIcons || prev.total,
        percent: prev.percent < 10 ? 10 : prev.percent,
      }));

      attachGifEventSource(payload.gifRequestId);
    } catch (error) {
      console.error("Failed to start GIF generation", error);
      setGifError("Failed to start GIF generation. Please try again.");
      setIsGifSubmitting(false);
    }
  };

  const prepareReferenceFromBase64 = (
    base64Data: string,
    filename: string,
    targetMode: GenerationMode,
  ) => {
    try {
      const cleanBase64 = base64Data.includes(",")
        ? base64Data.split(",").pop() ?? base64Data
        : base64Data;
      const binaryString = atob(cleanBase64);
      const length = binaryString.length;
      const bytes = new Uint8Array(length);
      for (let i = 0; i < length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: "image/png" });
      const file = new File([blob], filename, {
        type: "image/png",
        lastModified: Date.now(),
      });
      const previewUrl = URL.createObjectURL(blob);

      setMode(targetMode);
      setInputType("image");

      setTimeout(() => {
        setReferenceImage(file);
        setImagePreview(previewUrl);
      }, 0);

      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (error) {
      console.error("Error preparing reference image:", error);
      alert("Failed to prepare reference image. Please try again.");
    }
  };

  const handleGenerateIconsFromMockup = (base64Data: string) => {
    prepareReferenceFromBase64(base64Data, "mockup-reference.png", "icons");
  };

  const handleUseResultAsReference = (
    result: ServiceResult,
    targetMode: GenerationMode,
    iconBase64Override?: string,
  ) => {
    const base64Source =
      iconBase64Override || result.originalGridImageBase64 || result.icons?.[0]?.base64Data;

    if (!base64Source) {
      alert("Reference image is not available yet. Please wait for generation to finish.");
      return;
    }

    let filename = "reference.png";
    if (targetMode === "mockups") {
      filename = "icon-grid-for-mockups.png";
    } else if (targetMode === "labels") {
      filename = "icon-grid-for-labels.png";
    } else if (targetMode === "icons") {
      filename = "mockup-reference.png";
    }

    prepareReferenceFromBase64(base64Source, filename, targetMode);
  };

  const getResponseServiceResults = (serviceId: string) => {
    switch (serviceId) {
      case "falai":
        return currentResponse?.falAiResults;
      case "recraft":
        return currentResponse?.recraftResults;
      case "photon":
        return currentResponse?.photonResults;
      case "gpt":
        return currentResponse?.gptResults;
      case "gpt15":
        return currentResponse?.gpt15Results;
      case "banana":
        return currentResponse?.bananaResults;
      default:
        return undefined;
    }
  };

  const getDisplayIcons = (result: ServiceResult, baseServiceId: string) => {
    if (!isTrialResult || !currentResponse) {
      return result.icons;
    }

    const serviceResults = getResponseServiceResults(baseServiceId);
    const matchingResult = serviceResults?.find(
      (serviceResult) => serviceResult.generationIndex === result.generationIndex,
    );
    return matchingResult?.icons?.length ? matchingResult.icons : result.icons;
  };

  const renderGenerationResults = (generationNumber: number) => {
    const results = getGenerationResults(generationNumber);
    return results.map((result, index) => {
      const baseServiceId = result.serviceId.replace(/-gen\d+$/, "");
      const serviceName = getServiceDisplayName(baseServiceId);
      const displayIcons = getDisplayIcons(result, baseServiceId);
      const referenceIconBase64 =
        isTrialResult && displayIcons?.length ? displayIcons[0].base64Data : undefined;

      return (
        <div key={result.serviceId} data-oid="o-woppu">
          {index > 0 && (
            <div className="border-t border-gray-200 my-6" data-oid=".rbhqb3" />
          )}
          <div className="mb-4" data-oid="d3ydm7r">
            <div
              className="flex items-center justify-between"
              data-oid="eo088-:"
            >
              <h3
                className="text-lg font-medium text-gray-900 flex items-center"
                data-oid="wndlt.f"
              >
                {result.status === "success" ? (
                  <span className="mr-2" data-oid="qbnu-fi">
                    ✅
                  </span>
                ) : result.status === "error" ? (
                  <span className="mr-2" data-oid="qbnu-fi">
                    ❌
                  </span>
                ) : (
                  <div className="mr-2 h-4 w-4 animate-spin rounded-full border-b-2 border-gray-900" />
                )}
                {result.status}
                {result.generationTimeMs > 0 && (
                  <span
                    className="text-sm text-gray-500 ml-2"
                    data-oid="q4emod9"
                  >
                    ({(result.generationTimeMs / 1000).toFixed(1)}s)
                  </span>
                )}
              </h3>
              {result.status === "success" && displayIcons.length > 0 && (
                <div className="flex items-center gap-2 sm:gap-3">
                  {mode === "icons" && (
                    <>
                      <button
                        onClick={() =>
                          handleUseResultAsReference(result, "mockups", referenceIconBase64)
                        }
                        className={`${actionButtonBaseClass} gap-1`}
                        title="Generate UI Mockup from these icons"
                      >
                        <span className="text-xs font-bold">UI</span>
                      </button>
                      <button
                        onClick={() =>
                          handleUseResultAsReference(result, "labels", referenceIconBase64)
                        }
                        className={`${actionButtonBaseClass} gap-1`}
                        title="Generate Labels from these icons"
                      >
                        <span className="text-xs font-bold">T</span>
                      </button>
                      <button
                        onClick={() =>
                          openGifModal(
                            baseServiceId,
                            serviceName,
                            result.generationIndex,
                            displayIcons,
                          )
                        }
                        className={`${actionButtonBaseClass} gap-1`}
                        title="Create GIFs from these icons"
                      >
                        <span className="text-xs font-bold">GIF</span>
                      </button>
                    </>
                  )}
                  {mode === "mockups" && (
                    <button
                      onClick={() =>
                        handleUseResultAsReference(result, "icons", referenceIconBase64)
                      }
                      className={`${actionButtonBaseClass} gap-1`}
                      title="Generate Icons from this mockup"
                    >
                      <span className="text-xs font-bold">Icon</span>
                    </button>
                  )}
                  <button
                    onClick={() =>
                      exportGeneration(
                        currentResponse?.requestId || "",
                        baseServiceId,
                        result.generationIndex,
                      )
                    }
                    className={`${actionButtonBaseClass} gap-2`}
                    title="Export"
                    data-oid="xt-nyai"
                  >
                    <Download className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
            <p className="text-sm text-gray-600 mt-1" data-oid="6-u8r69">
              {result.message}
            </p>
          </div>

          {showResultsPanes && displayIcons && displayIcons.length > 0 && (
            <div 
              className={
                mode === "icons" || mode === "labels"
                  ? "grid gap-4 grid-cols-2 sm:grid-cols-[repeat(auto-fit,minmax(160px,1fr))]"
                  : mode === "illustrations"
                  ? "grid grid-cols-1 sm:grid-cols-2 gap-6"
                  : "flex justify-center items-center"
              } 
              data-oid=".ge-1o5"
            >
              {displayIcons.map((icon, iconIndex) => (
                <div
                  key={iconIndex}
                  className={`relative group transform ${getIconAnimationClass(result.serviceId, iconIndex)} ${
                    mode === "icons" || mode === "labels"
                      ? "hover:scale-105 hover:z-20 flex justify-center"
                      : "hover:scale-105 transition-transform duration-200"
                  }`}
                  data-oid="m76b0.p"
                >
                  <div
                    className={
                      mode === "illustrations"
                        ? "aspect-[5/4] w-full max-w-[450px]"
                        : mode === "mockups"
                        ? "aspect-video w-full max-w-[800px]"
                        : mode === "labels"
                        ? "w-full max-w-[320px]"
                        : ""
                    }
                  >
                    <img
                      src={`data:image/png;base64,${icon.base64Data}`}
                      alt={
                        mode === "icons"
                          ? `Generated Icon ${iconIndex + 1}`
                          : mode === "illustrations"
                          ? `Generated Illustration ${iconIndex + 1}`
                          : mode === "labels"
                          ? `Generated Label ${iconIndex + 1}`
                          : `Generated UI Mockup`
                      }
                      onClick={() => handleImageClick(icon.base64Data)}
                      className={
                        mode === "icons"
                          ? "w-full h-auto max-w-[128px] rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200"
                          : mode === "labels"
                          ? "w-full h-auto max-w-[280px] rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200"
                          : "w-full h-full object-contain rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200 cursor-pointer"
                      }
                      data-oid="3jhfiim"
                    />
                  </div>
                  <div
                    className={`absolute inset-0 bg-gradient-to-r from-blue-400 to-purple-500 rounded-lg transition-opacity duration-700 pointer-events-none ${animatingIcons[result.serviceId] > iconIndex ? "opacity-0" : "opacity-20"}`}
                    data-oid="bit9s0x"
                  />
                </div>
              ))}
            </div>
          )}

          {result.status === "success" && uiState === "results" && mode === "mockups" && (
            <div
              className="mt-6 p-6 bg-white/60 backdrop-blur-lg rounded-2xl shadow-lg border border-blue-200/30"
            >
              <div
                className="flex items-center justify-between mb-4"
              >
                <h4
                  className="text-base font-semibold text-slate-800"
                >
                  Use Mockup as Reference
                </h4>
                <button
                  onClick={() => {
                    // Get the first icon (mockup image) from this result
                    if (result.icons && result.icons.length > 0) {
                      handleGenerateIconsFromMockup(result.icons[0].base64Data);
                    }
                  }}
                  className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-105 shadow-md hover:shadow-lg transition-all duration-200"
                >
                  <div className="flex items-center justify-center space-x-2">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                    <span>Generate Icons</span>
                  </div>
                </button>
              </div>
              <p className="text-xs text-slate-500">
                Use this mockup as a reference to generate matching icon packs
              </p>
            </div>
          )}

          {result.status === "success" && uiState === "results" && (mode === "icons" || mode === "illustrations") && (
            <div
              className="mt-6 p-6 bg-white/60 backdrop-blur-lg rounded-2xl shadow-lg border border-purple-200/30"
              data-oid="ovhlhfz"
            >
              <div
                className="flex items-center justify-between mb-4"
                data-oid="uso00lt"
              >
                <h4
                  className="text-base font-semibold text-slate-800"
                  data-oid="d1sbto:"
                >
                  Generate More With Same Style
                </h4>
                {!moreIconsVisible[result.serviceId] && (
                  <button
                    onClick={() => showMoreIconsForm(result.serviceId)}
                    className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-105 shadow-md hover:shadow-lg transition-all duration-200"
                    data-oid="or9y-ww"
                  >
                    <div className="flex items-center justify-center space-x-2">
                      <span>Generate More</span>
                      <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                        <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                        <span>1</span>
                      </span>
                    </div>
                  </button>
                )}
              </div>
              {moreIconsVisible[result.serviceId] && (
                <div className="space-y-4" data-oid="vyc4_1h">
                  <p className="text-xs text-slate-500" data-oid="83178c6">
                    {mode === "icons" 
                      ? "Describe up to 9 new icons (leave empty for creative variations):"
                      : "Describe up to 4 new illustrations (leave empty for creative variations):"}
                  </p>
                  <div className={mode === "icons" ? "grid grid-cols-3 gap-3" : "grid grid-cols-2 gap-3"} data-oid="05:gpsz">
                    {Array.from({ length: mode === "icons" ? 9 : 4 }, (_, i) => (
                      <input
                        key={i}
                        type="text"
                        placeholder={mode === "icons" ? `Icon ${i + 1}` : `Illustration ${i + 1}`}
                        value={
                          moreIconsDescriptions[result.serviceId]?.[i] || ""
                        }
                        onChange={(e) => {
                          const count = mode === "icons" ? 9 : 4;
                          const newDescriptions = [
                            ...(moreIconsDescriptions[result.serviceId] ||
                              new Array(count).fill("")),
                          ];
                          newDescriptions[i] = e.target.value;
                          setMoreIconsDescriptions((prev) => ({
                            ...prev,
                            [result.serviceId]: newDescriptions,
                          }));
                        }}
                        className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition-all duration-200"
                        data-oid="dkp-80."
                      />
                    ))}
                  </div>
                  <div className="flex space-x-3 pt-2" data-oid="5:ovvgt">
                    <button
                      onClick={() => {
                        if (mode === "icons") {
                          generateMoreIcons(
                            baseServiceId,
                            serviceName,
                            result.generationIndex,
                          );
                        } else {
                          generateMoreIllustrations(
                            baseServiceId,
                            serviceName,
                            result.generationIndex,
                          );
                        }
                      }}
                      disabled={isGenerating}
                      className={`w-full py-3 px-5 rounded-xl text-white font-semibold ${isGenerating ? "bg-slate-400 cursor-not-allowed" : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-105 shadow-lg hover:shadow-xl"} transition-all duration-200`}
                      data-oid="xku30oy"
                    >
                      {isGenerating ? (
                        <div
                          className="flex items-center justify-center space-x-2"
                          data-oid="wr:qqx5"
                        >
                          <div
                            className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"
                            data-oid="klkpq:y"
                          ></div>
                          <span data-oid="-o8k1u8">Generating...</span>
                        </div>
                      ) : (
                        <div className="flex items-center justify-center space-x-2">
                          <span>{mode === "icons" ? "Generate 9 More Icons" : "Generate 4 More Illustrations"}</span>
                          <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                            <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                            <span>1</span>
                          </span>
                        </div>
                      )}
                    </button>
                    <button
                      onClick={() => hideMoreIconsForm(result.serviceId)}
                      className="px-4 py-2 bg-slate-200 text-slate-700 rounded-xl text-sm font-semibold hover:bg-slate-300 transition-colors duration-200"
                      data-oid="rw.qmye"
                    >
                      Cancel
                    </button>
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
    <div className="w-full flex-1 p-4 lg:p-8 flex flex-col" data-oid="zbnho:w">
      {isGenerating && (
        <div className="mb-6" data-oid="dy:7hpm">
          <div
            className="w-full bg-gray-200 rounded-full h-0.5"
            data-oid="q9qnu-0"
          >
            <div
              className="bg-purple-600 h-0.5 rounded-full transition-all duration-300"
              style={{ width: `${overallProgress}%` }}
              data-oid="d-c5u0v"
            />
          </div>
          {/*<p className="text-center text-sm text-gray-600 mt-2" data-oid="2x02tua">*/}
          {/*    {overallProgress < 100*/}
          {/*        ? `Generating icons... Estimated time remaining: ${calculateTimeRemaining()}`*/}
          {/*        : 'Finalizing results...'}*/}
          {/*</p>*/}
        </div>
      )}
      <div className="flex-1 flex flex-col lg:flex-row lg:space-x-8 space-y-8 lg:space-y-0" data-oid=".0me_fy">
        <div
          className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-purple-200/50 flex-1 relative"
          data-oid="pzu54n5"
        >
          <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
          <div
            className="p-8 h-full flex flex-col relative z-10"
            data-oid="pqst9it"
          >
            <div
              className="flex items-center justify-between mb-6"
              data-oid="dv9qzvx"
            >
              <h2
                className="text-2xl font-bold text-slate-900"
                data-oid="rn9b4_h"
              >
                {mode === "icons"
                  ? "Your Icons"
                  : mode === "illustrations"
                  ? "Your Illustrations"
                  : mode === "labels"
                  ? "Your Labels"
                  : "Your Mockup"}
              </h2>
            </div>
            <div className="flex-1 overflow-y-auto" data-oid="fr-8:os">
              {uiState === "initial" && (
                <div
                  className="h-full flex items-center justify-center"
                  data-oid="wrg3w6c"
                >
                  <div className="text-center" data-oid="k7r4nph">
                    <svg
                      className="mx-auto h-16 w-16 text-gray-400 mb-4"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      data-oid="zwyv.vw"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1}
                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                        data-oid="jboyff2"
                      />
                    </svg>
                    <p className="text-gray-500" data-oid="gi9rui3">
                      {mode === "mockups"
                        ? "Generated UI mockup will appear here"
                        : mode === "labels"
                        ? "Generated labels will appear here"
                        : "Generated icons will appear here"}
                    </p>
                  </div>
                </div>
              )}
              {uiState === "error" && (
                <div
                  className="h-full flex items-center justify-center"
                  data-oid="33s1ijk"
                >
                  <div
                    className="bg-red-50 border border-red-200 rounded-lg p-4 max-w-sm"
                    data-oid="2z75dtq"
                  >
                    <div className="flex items-center" data-oid="d6_n28j">
                      <svg
                        className="h-5 w-5 text-red-400 mr-2"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        data-oid="ep8gn1t"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                          data-oid="12on8e0"
                        />
                      </svg>
                      <div data-oid=":de2emg">
                        <h3
                          className="text-sm font-medium text-red-800"
                          data-oid="_vh8b7."
                        >
                          Generation Failed
                        </h3>
                        <p
                          className="text-sm text-red-700 mt-1"
                          data-oid="sk6-y9n"
                        >
                          {errorMessage}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {(uiState === "streaming" || uiState === "results") && (
                <div className="space-y-6" data-oid="g25wa45">
                  {renderGenerationResults(1)}
                </div>
              )}
              {uiState === "results" &&
                (mode === "icons" || mode === "illustrations") &&
                isTrialResult &&
                (currentResponse?.icons?.length ?? 0) > 0 && (
                  <div className="mt-8 relative group overflow-hidden rounded-2xl">
                    <div className="absolute inset-0 bg-gradient-to-r from-amber-200 via-orange-100 to-amber-50 opacity-50"></div>
                    <div className="relative p-6 sm:p-8 border border-amber-200/60 rounded-2xl bg-white/40 backdrop-blur-sm">
                      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
                        <div className="space-y-3 flex-1">
                          <div className="flex items-center gap-2">
                            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg">
                              <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                            </div>
                            <h3 className="text-lg font-bold text-slate-800">
                              Unlock Full Potential
                            </h3>
                          </div>
                          <p className="text-slate-600 text-sm leading-relaxed">
                            Loving these results? Get <span className="font-semibold text-amber-700">Premium Coins</span> to access:
                          </p>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-2 gap-x-4 text-sm">
                            {["Crisp SVG & HQ Exports", "GIF Animations", "Extra Variations", "No watermarks"].map((feature, i) => (
                              <div key={i} className="flex items-center gap-2 text-slate-700">
                                <svg className="w-4 h-4 text-green-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                </svg>
                                <span>{feature}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                        <div className="flex-shrink-0 w-full sm:w-auto">
                          <Link
                            href="/store"
                            className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-3 rounded-xl text-sm font-bold text-white bg-gradient-to-r from-amber-500 to-orange-600 hover:from-amber-600 hover:to-orange-700 shadow-lg hover:shadow-xl transform hover:-translate-y-0.5 transition-all duration-200 gap-2 group-hover:ring-2 group-hover:ring-amber-500/20"
                          >
                            <span>Visit Store</span>
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 8l4 4m0 0l-4 4m4-4H3" />
                            </svg>
                          </Link>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
            </div>
          </div>
        </div>
        {generateVariations && (
          <div
            className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-purple-200/50 flex-1 relative"
            data-oid="mj9c878"
          >
            <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
            <div
              className="p-8 h-full flex flex-col relative z-10"
              data-oid="o17l-tp"
            >
              <div
                className="flex items-center justify-between mb-6"
                data-oid="qd67dxc"
              >
                <h2
                  className="text-2xl font-bold text-slate-900"
                  data-oid="pz2eo.j"
                >
                  Variations
                </h2>
              </div>
              <div className="flex-1 overflow-y-auto" data-oid="ocx--ar">
                {uiState === "initial" && (
                  <div
                    className="h-full flex items-center justify-center"
                    data-oid="22__9nu"
                  >
                    <div className="text-center" data-oid="d0c1s_8">
                      <svg
                        className="mx-auto h-16 w-16 text-gray-400 mb-4"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        data-oid="wo5.-8x"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={1}
                          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                          data-oid="2eg74fx"
                        />
                      </svg>
                      <p className="text-gray-500" data-oid="2c96zzz">
                        {mode === "mockups"
                          ? "UI mockup variation will appear here"
                          : mode === "labels"
                          ? "Label variations will appear here"
                          : "Icon variations will appear here"}
                      </p>
                    </div>
                  </div>
                )}
                {(uiState === "streaming" || uiState === "results") && (
                  <div className="space-y-6" data-oid=":qzs.na">
                    {renderGenerationResults(2)}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* GIF generation modal */}
      {gifModalState && gifModalState.isOpen && (
        <GifModal
          title={`${gifModalState.serviceName} · Generation ${gifModalState.generationIndex}`}
          icons={gifModalState.icons.map((icon, index) => {
            const fallbackId = `${gifModalState.serviceId}-${index}`;
            const iconId = icon.id || fallbackId;
            const selectableId = icon.id;
            const isSelectable = Boolean(selectableId);
            return {
              id: iconId,
              imageSrc: `data:image/png;base64,${icon.base64Data}`,
              description: icon.description || `Icon ${index + 1}`,
              selectable: isSelectable,
              isSelected: Boolean(selectableId && selectedGifIcons.has(selectableId)),
              onToggle: selectableId ? () => toggleGifSelection(selectableId) : undefined,
            };
          })}
          selectedCount={gifSelectedCount}
          estimatedCost={gifEstimatedCost}
          availableCoins={availableCoins}
          trialCoins={trialCoins}
          insufficientBalance={insufficientGifBalance}
          progress={gifProgress}
          gifResults={gifResults}
          gifRefreshToken={gifRefreshToken}
          gifError={gifError}
          isSubmitting={isGifSubmitting}
          onClose={closeGifModal}
          onGenerate={handleGenerateGifs}
        />
      )}

      {/* Full-size image preview modal */}
      {previewImage && (
        <div
          className="fixed inset-0 bg-black bg-opacity-90 z-50 flex items-center justify-center p-4"
          onClick={closePreview}
        >
          <div className="relative max-w-7xl max-h-full">
            <button
              onClick={closePreview}
              className="absolute top-4 right-4 text-white bg-black bg-opacity-50 rounded-full p-2 hover:bg-opacity-75 transition-all duration-200 z-10"
              aria-label="Close preview"
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
            <img
              src={`data:image/png;base64,${previewImage}`}
              alt="Full size preview"
              className="max-w-full max-h-[90vh] object-contain rounded-lg"
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default ResultsDisplay;
