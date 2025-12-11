import { useCallback, useEffect, useRef, useState } from "react";
import {
  GenerationMode,
  GenerationResponse,
  Icon,
  ServiceResult,
  StreamingResults,
  UIState,
} from "@/lib/types";
import {
  DashboardFormState,
} from "./useDashboardFormState";
import {
  IconAnimationController,
} from "./useIconAnimations";

const MAX_ARTIFICIAL_PROGRESS = 95;

interface AuthStateLike {
  user?: {
    coins?: number;
    trialCoins?: number;
  };
}

interface UseGenerationFlowParams {
  mode: GenerationMode;
  setMode: (mode: GenerationMode) => void;
  authState: AuthStateLike | null | undefined;
  checkAuthenticationStatus: () => Promise<void>;
  formState: DashboardFormState;
  iconAnimations: IconAnimationController;
}

export function useGenerationFlow({
  mode,
  setMode,
  authState,
  checkAuthenticationStatus,
  formState,
  iconAnimations,
}: UseGenerationFlowParams) {
  const {
    inputType,
    setInputType,
    generateVariations,
    generalDescription,
    labelText,
    individualDescriptions,
    referenceImage,
    setReferenceImage,
    setImagePreview,
    fileToBase64,
    validateForm,
    enhancePrompt,
  } = formState;
  const {
    animatingIcons,
    setAnimatingIcons,
    startIconAnimation,
    clearAllAnimations,
    getIconAnimationClass,
  } = iconAnimations;

  const [uiState, setUiState] = useState<UIState>("initial");
  const [errorMessage, setErrorMessage] = useState("");
  const [isGenerating, setIsGenerating] = useState(false);
  const [currentIcons, setCurrentIcons] = useState<Icon[]>([]);
  const [currentRequest, setCurrentRequest] = useState<any>(null);
  const [currentResponse, setCurrentResponse] =
    useState<GenerationResponse | null>(null);
  const [streamingResults, setStreamingResults] = useState<StreamingResults>(
    {},
  );
  const [showResultsPanes, setShowResultsPanes] = useState(false);
  const [isTrialResult, setIsTrialResult] = useState(false);

  const [moreIconsVisible, setMoreIconsVisible] = useState<
    Record<string, boolean>
  >({});
  const [moreIconsDescriptions, setMoreIconsDescriptions] = useState<
    Record<string, string[]>
  >({});

  const [overallProgress, setOverallProgress] = useState(0);
  const [totalDuration, setTotalDuration] = useState(0);

  const [pendingRequestId, setPendingRequestId] = useState<string | null>(null);
  const currentEventSourceRef = useRef<EventSource | null>(null);
  const isRecoveredRef = useRef<boolean>(false);
  const overallProgressTimerRef = useRef<NodeJS.Timeout | null>(null);

  const stopOverallProgressTimer = useCallback(() => {
    if (overallProgressTimerRef.current) {
      clearInterval(overallProgressTimerRef.current);
      overallProgressTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    setCurrentIcons([]);
    setCurrentRequest(null);
    setCurrentResponse(null);
    setStreamingResults({});
    setUiState("initial");
    setErrorMessage("");
    setShowResultsPanes(false);
    setIsTrialResult(false);
    setMoreIconsVisible({});
    setMoreIconsDescriptions({});
    clearAllAnimations();
    setAnimatingIcons({});
    setOverallProgress(0);
    setPendingRequestId(null);

  }, [clearAllAnimations, mode, setAnimatingIcons]);

  useEffect(() => {
    return () => {
      stopOverallProgressTimer();
    };
  }, [stopOverallProgressTimer]);

  useEffect(() => {
    return () => {
      if (currentEventSourceRef.current) {
        currentEventSourceRef.current.close();
        currentEventSourceRef.current = null;
      }
    };
  }, []);

  const formatServiceName = useCallback((serviceId: string): string => {
    const serviceNames: { [key: string]: string } = {
      flux: "Flux-Pro",
      recraft: "Recraft V3",
      photon: "Luma Photon",
      gpt: "GPT Image",
      banana: "Nano Banana",
    };
    return serviceNames[serviceId] || serviceId;
  }, []);

  const calculateTimeRemaining = useCallback(() => {
    if (overallProgress >= MAX_ARTIFICIAL_PROGRESS) return "0s";
    const remainingMs = totalDuration * (1 - overallProgress / 100);
    const remainingSeconds = Math.round(remainingMs / 1000);
    return `${remainingSeconds}s`;
  }, [overallProgress, totalDuration]);

  const startOverallProgressTimer = useCallback(
    (duration: number) => {
      stopOverallProgressTimer();
      setTotalDuration(duration);
      const increment = MAX_ARTIFICIAL_PROGRESS / (duration / 100);
      overallProgressTimerRef.current = setInterval(() => {
        setOverallProgress((prev) => {
          const newProgress = prev + increment;
          if (newProgress >= MAX_ARTIFICIAL_PROGRESS) {
            stopOverallProgressTimer();
            return MAX_ARTIFICIAL_PROGRESS;
          }
          return newProgress;
        });
      }, 100);
    },
    [stopOverallProgressTimer],
  );

  const saveGenerationState = useCallback(
    (requestId: string, request: any) => {
      try {
        const generationState = {
          requestId,
          request,
          mode,
          timestamp: Date.now(),
        };
        localStorage.setItem(
          "pendingGeneration",
          JSON.stringify(generationState),
        );
        setPendingRequestId(requestId);
      } catch (error) {
        console.warn("Failed to save generation state to localStorage:", error);
      }
    },
    [mode],
  );

  const getGenerationState = useCallback(() => {
    try {
      const storedState = localStorage.getItem("pendingGeneration");
      if (storedState) {
        const parsed = JSON.parse(storedState);
        if (Date.now() - parsed.timestamp < 2 * 60 * 60 * 1000) {
          return parsed;
        }
        localStorage.removeItem("pendingGeneration");
      }
    } catch (error) {
      console.warn("Failed to get generation state from localStorage:", error);
    }
    return null;
  }, []);

  const clearGenerationState = useCallback(() => {
    try {
      localStorage.removeItem("pendingGeneration");
      setPendingRequestId(null);
    } catch (error) {
      console.warn("Failed to clear generation state from localStorage:", error);
    }
  }, []);

  const checkGenerationStatus = useCallback(
    async (requestId: string, generationMode: GenerationMode): Promise<any> => {
      try {
        const endpoint =
          generationMode === "illustrations"
            ? `/api/illustrations/generate/status/${requestId}`
            : generationMode === "mockups"
            ? `/api/mockups/generate/status/${requestId}`
            : generationMode === "labels"
            ? `/api/labels/generate/status/${requestId}`
            : `/status/${requestId}`;
        const response = await fetch(endpoint, {
          method: "GET",
          credentials: "include",
        });
        if (!response.ok) {
          if (response.status === 404) {
            return { status: "not_found" };
          }
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
      } catch (error) {
        console.error("Error checking generation status:", error);
        return { status: "error", message: "Failed to check status" };
      }
    },
    [],
  );

  const initializeStreamingResults = useCallback(
    (enabledServices: { [key: string]: boolean }) => {
      const newResults: StreamingResults = {};
      const allServices = [
        { id: "flux", name: "Flux-Pro" },
        { id: "recraft", name: "Recraft V3" },
        { id: "photon", name: "Luma Photon" },
        { id: "gpt", name: "" },
        { id: "banana", name: "Nano Banana" },
      ];

      const enabledServicesList = allServices.filter(
        (service) => enabledServices[service.id],
      );
      const generationsNum = generateVariations ? 2 : 1;
      enabledServicesList.forEach((service) => {
        for (let genIndex = 1; genIndex <= generationsNum; genIndex++) {
          const uniqueId = `${service.id}-gen${genIndex}`;
          newResults[uniqueId] = {
            icons: [],
            generationTimeMs: 0,
            status: "started",
            message: "Progressing..",
            generationIndex: genIndex,
          };
        }
      });
      setStreamingResults(newResults);
    },
    [generateVariations],
  );

  const handleServiceUpdate = useCallback((update: any) => {
    const serviceId = update.serviceName;
    setStreamingResults((prev) => {
      const current = prev[serviceId] || {};
      const updated = {
        ...current,
        status: update.status,
        message: update.message || current.message,
        generationTimeMs: update.generationTimeMs || current.generationTimeMs,
      };
      if (update.status === "success") {
        updated.icons = update.icons || [];
        updated.originalGridImageBase64 = update.originalGridImageBase64;
        updated.generationIndex = update.generationIndex;
      }
      return { ...prev, [serviceId]: updated };
    });
  }, []);

  const getServiceDisplayName = useCallback(
    (serviceId: string) => {
      const baseServiceId = serviceId.replace(/-gen\d+$/, "");
      return formatServiceName(baseServiceId);
    },
    [formatServiceName],
  );

  const handleGenerationComplete = useCallback(
    (update: any) => {
      stopOverallProgressTimer();
      setOverallProgress(100);
      setShowResultsPanes(true);

      const isTrialMode =
        typeof update.trialMode === "boolean"
          ? update.trialMode
          : typeof update.message === "string" &&
            update.message.toLowerCase().includes("trial");
      setIsTrialResult(Boolean(isTrialMode));

      setStreamingResults((latestStreamingResults) => {
        const finalIcons =
          update.icons && update.icons.length > 0 ? update.icons : null;

        if (finalIcons) {
          setCurrentIcons(finalIcons);

          const updatedStreamingResults = { ...latestStreamingResults };

          Object.keys(updatedStreamingResults).forEach((serviceKey) => {
            const baseServiceId = serviceKey.replace(/-gen\d+$/, "");
            const serviceIcons = finalIcons.filter((icon: any) => {
              const iconService = icon.serviceSource?.toLowerCase();
              return (
                iconService === baseServiceId ||
                (baseServiceId === "flux" &&
                  (iconService === "falai" || iconService === "flux-pro"))
              );
            });

            if (serviceIcons.length > 0) {
              updatedStreamingResults[serviceKey] = {
                ...updatedStreamingResults[serviceKey],
                icons: serviceIcons,
                message:
                  updatedStreamingResults[serviceKey].message +
                  (isTrialMode ? " (Trial: 5 of 9 icons)" : ""),
              };
            }
          });

          const groupedResults = {
            falAiResults: [] as ServiceResult[],
            recraftResults: [] as ServiceResult[],
            photonResults: [] as ServiceResult[],
            gptResults: [] as ServiceResult[],
            bananaResults: [] as ServiceResult[],
          };

          Object.entries(updatedStreamingResults).forEach(
            ([serviceKey, result]) => {
              const baseServiceId = serviceKey.replace(/-gen\d+$/, "");
              switch (baseServiceId) {
                case "flux":
                  groupedResults.falAiResults.push(result);
                  break;
                case "recraft":
                  groupedResults.recraftResults.push(result);
                  break;
                case "photon":
                  groupedResults.photonResults.push(result);
                  break;
                case "gpt":
                  groupedResults.gptResults.push(result);
                  break;
                case "banana":
                  groupedResults.bananaResults.push(result);
                  break;
              }
            },
          );

          setCurrentResponse({
            icons: finalIcons,
            ...groupedResults,
            requestId: update.requestId,
            trialMode: Boolean(isTrialMode),
          });

          setTimeout(() => {
            Object.entries(updatedStreamingResults).forEach(
              ([serviceId, result]) => {
                if (
                  result.status === "success" &&
                  result.icons &&
                  result.icons.length > 0
                ) {
                  startIconAnimation(serviceId, result.icons.length);
                }
              },
            );
          }, 300);
        } else {
          setTimeout(() => {
            Object.entries(latestStreamingResults).forEach(
              ([serviceId, result]) => {
                if (result.status === "success" && result.icons.length > 0) {
                  startIconAnimation(serviceId, result.icons.length);
                }
              },
            );
          }, 300);

          let allIcons: Icon[] = [];
          Object.values(latestStreamingResults).forEach((result) => {
            if (result.icons) {
              allIcons = allIcons.concat(result.icons);
            }
          });
          setCurrentIcons(allIcons);

          const groupedResults = {
            falAiResults: [] as ServiceResult[],
            recraftResults: [] as ServiceResult[],
            photonResults: [] as ServiceResult[],
            gptResults: [] as ServiceResult[],
            bananaResults: [] as ServiceResult[],
          };
          Object.entries(latestStreamingResults).forEach(
            ([serviceKey, result]) => {
              const baseServiceId = serviceKey.replace(/-gen\d+$/, "");
              switch (baseServiceId) {
                case "flux":
                  groupedResults.falAiResults.push(result);
                  break;
                case "recraft":
                  groupedResults.recraftResults.push(result);
                  break;
                case "photon":
                  groupedResults.photonResults.push(result);
                  break;
                case "gpt":
                  groupedResults.gptResults.push(result);
                  break;
                case "banana":
                  groupedResults.bananaResults.push(result);
                  break;
              }
            },
          );
          setCurrentResponse({
            icons: allIcons,
            ...groupedResults,
            requestId: update.requestId,
            trialMode: Boolean(isTrialMode),
          });
        }

        setUiState("results");
        setIsGenerating(false);
        clearGenerationState();
        if (currentEventSourceRef.current) {
          currentEventSourceRef.current = null;
        }
        isRecoveredRef.current = true;

        setTimeout(() => {
          void checkAuthenticationStatus();
        }, 3000);

        return latestStreamingResults;
      });
    },
    [
      checkAuthenticationStatus,
      clearGenerationState,
      startIconAnimation,
      stopOverallProgressTimer,
    ],
  );

  const handleGenerationRecovery = useCallback(async () => {
    const savedState = getGenerationState();
    if (!savedState) return;

    const { requestId, request, mode: savedMode } = savedState;
    const generationMode = savedMode || mode;

    try {
      const statusResult = await checkGenerationStatus(
        requestId,
        generationMode,
      );

      if (statusResult.status === "completed" && statusResult.data) {
        if (currentEventSourceRef.current) {
          currentEventSourceRef.current.close();
          currentEventSourceRef.current = null;
        }

        setCurrentRequest(request);
        if (savedMode && savedMode !== mode) {
          setMode(savedMode);
        }

        const completedResponse = statusResult.data;
        setIsTrialResult(Boolean(completedResponse.trialMode));

        const convertLabelToIcon = (label: any) => ({
          id: label.id,
          base64Data: label.base64Data,
          description: label.labelText,
          serviceSource: label.serviceSource || "gpt",
        });

        const items =
          generationMode === "illustrations"
            ? completedResponse.illustrations || []
            : generationMode === "labels"
            ? (completedResponse.labels || []).map(convertLabelToIcon)
            : completedResponse.icons || [];

        if (items.length > 0) {
          setCurrentIcons(items);
          setCurrentResponse({ ...completedResponse, icons: items });

          const updatedStreamingResults: any = {};

          const serviceMapping =
            generationMode === "illustrations"
              ? {
                  bananaResults: {
                    serviceId: "banana",
                    itemsKey: "illustrations",
                  },
                }
              : generationMode === "labels"
              ? { gptResults: { serviceId: "gpt", itemsKey: "labels" } }
              : {
                  falAiResults: { serviceId: "flux", itemsKey: "icons" },
                  recraftResults: { serviceId: "recraft", itemsKey: "icons" },
                  photonResults: { serviceId: "photon", itemsKey: "icons" },
                  gptResults: { serviceId: "gpt", itemsKey: "icons" },
                  bananaResults: { serviceId: "banana", itemsKey: "icons" },
                };

          Object.entries(serviceMapping).forEach(([responseKey, config]) => {
            const serviceResults = completedResponse[responseKey];
            if (serviceResults && Array.isArray(serviceResults)) {
              const hasEnabledResults = serviceResults.some(
                (result: any) => result.status !== "disabled",
              );
              if (hasEnabledResults) {
                serviceResults.forEach((result: any) => {
                  if (result.status !== "disabled") {
                    const uniqueId = `${config.serviceId}-gen${
                      result.generationIndex || 1
                    }`;
                    const resultItems =
                      config.itemsKey === "illustrations"
                        ? result.illustrations || []
                        : config.itemsKey === "labels"
                        ? (result.labels || []).map(convertLabelToIcon)
                        : result.icons || [];

                    updatedStreamingResults[uniqueId] = {
                      icons: resultItems,
                      generationTimeMs: result.generationTimeMs || 0,
                      status: result.status,
                      message: result.message,
                      generationIndex: result.generationIndex || 1,
                      originalGridImageBase64:
                        result.originalGridImageBase64,
                    };
                  }
                });
              }
            }
          });

          setStreamingResults(updatedStreamingResults);
          setUiState("results");
          setIsGenerating(false);
          setShowResultsPanes(true);
          setOverallProgress(100);

          setTimeout(() => {
            Object.entries(updatedStreamingResults).forEach(
              ([serviceId, result]) => {
                if (
                  result &&
                  typeof result === "object" &&
                  "status" in result &&
                  "icons" in result &&
                  result.status === "success" &&
                  Array.isArray(result.icons) &&
                  result.icons.length > 0
                ) {
                  startIconAnimation(serviceId, result.icons.length);
                }
              },
            );
          }, 300);

          isRecoveredRef.current = true;
          clearGenerationState();
          void checkAuthenticationStatus();
        }
      } else if (statusResult.status === "in_progress") {
        if (uiState === "initial" || uiState === "error") {
          setUiState("streaming");
          setIsGenerating(true);
          setErrorMessage("");
        }
      } else {
        clearGenerationState();
      }
    } catch (error) {
      console.error("Error during generation recovery:", error);
    }
  }, [
    checkAuthenticationStatus,
    checkGenerationStatus,
    clearGenerationState,
    getGenerationState,
    mode,
    setMode,
    startIconAnimation,
    uiState,
  ]);

  useEffect(() => {
    const generateMoreMode = sessionStorage.getItem("generateMoreMode");
    const gridImageUrl = sessionStorage.getItem("generatedGridImage");
    const generationMode = sessionStorage.getItem("generationMode");

    if (generateMoreMode === "true" && gridImageUrl) {
      if (generationMode === "illustrations") {
        setMode("illustrations");
      } else if (generationMode === "icons") {
        setMode("icons");
      } else if (generationMode === "mockups") {
        setMode("mockups");
      } else if (generationMode === "labels") {
        setMode("labels");
      }

      setInputType("image");

      fetch(gridImageUrl)
        .then((response) => response.blob())
        .then((blob) => {
          const file = new File([blob], "grid-composition.png", {
            type: "image/png",
            lastModified: Date.now(),
          });

          setReferenceImage(file);
          setImagePreview(gridImageUrl);

          sessionStorage.removeItem("generateMoreMode");
          sessionStorage.removeItem("generatedGridImage");
          sessionStorage.removeItem("generationMode");
        })
        .catch((error) => {
          console.error("Error setting up grid image:", error);
          sessionStorage.removeItem("generateMoreMode");
          sessionStorage.removeItem("generatedGridImage");
          sessionStorage.removeItem("generationMode");
        });
    }
  }, [setImagePreview, setInputType, setMode, setReferenceImage]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        void handleGenerationRecovery();
      }
    };

    const handleFocus = () => {
      void handleGenerationRecovery();
    };

    void handleGenerationRecovery();

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("focus", handleFocus);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("focus", handleFocus);
    };
  }, [handleGenerationRecovery]);

  const generateIcons = useCallback(async () => {
    if (
      !validateForm(mode, authState, (msg) => {
        setErrorMessage(msg);
      })
    ) {
      setUiState("error");
      return;
    }

    setIsGenerating(true);
    setUiState("streaming");
    setStreamingResults({});
    setShowResultsPanes(false);
    setIsTrialResult(false);
    setOverallProgress(0);

    isRecoveredRef.current = false;
    clearAllAnimations();

    let duration = 40000;
    if (inputType === "image") {
      duration = 70000;
    }
    startOverallProgressTimer(duration);

    const count =
      mode === "illustrations"
        ? 4
        : mode === "mockups"
        ? 1
        : mode === "labels"
        ? 1
        : 9;
    let formData: any;

    if (mode === "illustrations") {
      formData = {
        illustrationCount: count,
        generationsPerService: generateVariations ? 2 : 1,
        individualDescriptions: individualDescriptions.filter((desc) =>
          desc.trim(),
        ),
      };
    } else if (mode === "mockups") {
      formData = {
        mockupCount: 1,
        generationsPerService: 2,
      };
    } else if (mode === "labels") {
      formData = {
        labelText: labelText.trim(),
        generationsPerService: generateVariations ? 2 : 1,
      };
    } else {
      formData = {
        iconCount: count,
        generationsPerService: generateVariations ? 2 : 1,
        individualDescriptions: individualDescriptions.filter((desc) =>
          desc.trim(),
        ),
      };
    }

    if (inputType === "text") {
      if (mode === "mockups") {
        formData.description = generalDescription.trim();
      } else if (mode === "labels") {
        formData.generalTheme = generalDescription.trim();
      } else {
        formData.generalDescription = generalDescription.trim();
        if (mode === "icons") {
          formData.enhancePrompt = enhancePrompt;
        }
      }
    } else if (inputType === "image" && referenceImage) {
      try {
        formData.referenceImageBase64 = await fileToBase64(referenceImage);
      } catch (error) {
        console.error("Error converting image to base64:", error);
        setErrorMessage("Failed to process reference image");
        setUiState("error");
        setIsGenerating(false);
        stopOverallProgressTimer();
        return;
      }
    }

    setCurrentRequest({ ...formData });

    try {
      const endpoint =
        mode === "illustrations"
          ? "/api/illustrations/generate/stream/start"
          : mode === "mockups"
          ? "/api/mockups/generate/stream/start"
          : mode === "labels"
          ? "/api/labels/generate/stream/start"
          : "/generate-stream";
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(formData),
      });
      if (!response.ok) {
        const responseText = await response.text();
        console.error(
          "❌ Generation request failed:",
          response.status,
          responseText,
        );
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      const { requestId, enabledServices } = data;

      saveGenerationState(requestId, formData);
      initializeStreamingResults(enabledServices);

      if (currentEventSourceRef.current) {
        currentEventSourceRef.current.close();
      }

      const streamEndpoint =
        mode === "illustrations"
          ? `/api/illustrations/generate/stream/${requestId}`
          : mode === "mockups"
          ? `/api/mockups/generate/stream/${requestId}`
          : mode === "labels"
          ? `/api/labels/generate/stream/${requestId}`
          : `/stream/${requestId}`;
      const eventSource = new EventSource(streamEndpoint);
      currentEventSourceRef.current = eventSource;

      eventSource.addEventListener("service_update", (event) => {
        try {
          handleServiceUpdate(JSON.parse(event.data));
        } catch (error) {
          console.error("Error parsing service update:", error);
        }
      });
      eventSource.addEventListener("generation_complete", (event) => {
        try {
          handleGenerationComplete(JSON.parse(event.data));
          eventSource.close();
          currentEventSourceRef.current = null;
        } catch (error) {
          console.error("Error parsing completion update:", error);
          eventSource.close();
          currentEventSourceRef.current = null;
        }
      });
      eventSource.addEventListener("generation_error", (event) => {
        try {
          const update = JSON.parse(event.data);
          if (!isRecoveredRef.current) {
            setErrorMessage(update.message || "Generation failed");
            setUiState("error");
            setIsGenerating(false);
            stopOverallProgressTimer();
          }
          eventSource.close();
          currentEventSourceRef.current = null;
        } catch (error) {
          console.error("Error parsing error update:", error);
          if (!isRecoveredRef.current) {
            setErrorMessage("Generation failed with unknown error");
            setUiState("error");
            setIsGenerating(false);
            stopOverallProgressTimer();
          }
          eventSource.close();
          currentEventSourceRef.current = null;
        }
      });
      eventSource.onerror = (error) => {
        console.error("EventSource error:", error);

        eventSource.close();
        currentEventSourceRef.current = null;

        if (!isRecoveredRef.current) {
          handleGenerationRecovery()
            .then(() => {
              setTimeout(() => {
                if (!isRecoveredRef.current) {
                  setErrorMessage("Connection error. Please try again.");
                  setUiState("error");
                  setIsGenerating(false);
                  stopOverallProgressTimer();
                }
              }, 500);
            })
            .catch((recoveryError) => {
              console.error("Recovery attempt failed:", recoveryError);
              setErrorMessage("Connection error. Please try again.");
              setUiState("error");
              setIsGenerating(false);
              stopOverallProgressTimer();
            });
        }
      };
    } catch (error) {
      console.error("❌ Error starting generation:", error);
      if (error instanceof Error) {
        console.error("❌ Error message:", error.message);
        console.error("❌ Error stack:", error.stack);
      }
      setErrorMessage("Failed to start generation. Please try again.");
      setUiState("error");
      setIsGenerating(false);
      stopOverallProgressTimer();
    }
  }, [
    authState,
    clearAllAnimations,
    generalDescription,
    generateVariations,
    handleGenerationComplete,
    handleGenerationRecovery,
    handleServiceUpdate,
    individualDescriptions,
    initializeStreamingResults,
    inputType,
    labelText,
    mode,
    referenceImage,
    saveGenerationState,
    setErrorMessage,
    setShowResultsPanes,
    startOverallProgressTimer,
    stopOverallProgressTimer,
    validateForm,
  ]);

  const showMoreIconsForm = useCallback(
    (uniqueId: string) => {
      const count = mode === "illustrations" ? 4 : 9;
      setMoreIconsVisible((prev) => ({ ...prev, [uniqueId]: true }));
      setMoreIconsDescriptions((prev) => ({
        ...prev,
        [uniqueId]: new Array(count).fill(""),
      }));
    },
    [mode],
  );

  const hideMoreIconsForm = useCallback(
    (uniqueId: string) => {
      const count = mode === "illustrations" ? 4 : 9;
      setMoreIconsVisible((prev) => ({ ...prev, [uniqueId]: false }));
      setMoreIconsDescriptions((prev) => ({
        ...prev,
        [uniqueId]: new Array(count).fill(""),
      }));
    },
    [mode],
  );

  const getServiceResults = useCallback(
    (serviceId: string, generationIndex: number): ServiceResult | null => {
      if (!currentResponse) return null;
      let resultsArray: ServiceResult[] | undefined;
      switch (serviceId) {
        case "flux":
          resultsArray = currentResponse.falAiResults;
          break;
        case "recraft":
          resultsArray = currentResponse.recraftResults;
          break;
        case "photon":
          resultsArray = currentResponse.photonResults;
          break;
        case "gpt":
          resultsArray = currentResponse.gptResults;
          break;
        case "banana":
          resultsArray = currentResponse.bananaResults;
          break;
        default:
          return null;
      }
      if (resultsArray && resultsArray.length > 0) {
        return (
          resultsArray.find((r) => r.generationIndex === generationIndex) ||
          null
        );
      }
      return null;
    },
    [currentResponse],
  );

  const generateMoreIcons = useCallback(
    async (serviceId: string, serviceName: string, generationIndex: number) => {
      const user = authState?.user;
      const regularCoins = user?.coins ?? 0;
      const trialCoins = user?.trialCoins ?? 0;

      if (regularCoins < 1 && trialCoins === 0) {
        setErrorMessage(
          "Insufficient coins. You need 1 coin to generate more icons.",
        );
        setUiState("error");
        return;
      }

      const uniqueId = `${serviceId}-gen${generationIndex}`;
      const descriptions = moreIconsDescriptions[uniqueId] || [];
      const serviceResults = getServiceResults(serviceId, generationIndex);
      if (!serviceResults?.originalGridImageBase64) {
        setErrorMessage("Original image not found for this service");
        setUiState("error");
        return;
      }

      setIsGenerating(true);
      setOverallProgress(0);

      const duration = 35000;
      startOverallProgressTimer(duration);

      setStreamingResults((prev) => ({
        ...prev,
        [uniqueId]: {
          ...prev[uniqueId],
          status: "started",
          message: "Generating more icons...",
        },
      }));

      const moreIconsRequest = {
        originalRequestId: currentResponse?.requestId,
        serviceName: serviceId,
        originalImageBase64: serviceResults.originalGridImageBase64,
        generalDescription: currentRequest?.generalDescription,
        iconDescriptions: descriptions,
        iconCount: 9,
        seed: serviceResults.seed,
        generationIndex,
      };

      try {
        const response = await fetch("/generate-more", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify(moreIconsRequest),
        });
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();

        if (data.status === "success") {
          setCurrentIcons((prev) => prev.concat(data.newIcons));

          const previousIconCount =
            streamingResults[uniqueId]?.icons?.length || 0;
          setStreamingResults((prev) => ({
            ...prev,
            [uniqueId]: {
              ...prev[uniqueId],
              status: "success",
              message: "More icons generated successfully",
              icons: [...(prev[uniqueId]?.icons || []), ...data.newIcons],
            },
          }));

          setTimeout(() => {
            setAnimatingIcons((prev) => ({
              ...prev,
              [uniqueId]: previousIconCount,
            }));
            for (let i = 0; i < data.newIcons.length; i++) {
              setTimeout(() => {
                setAnimatingIcons((prev) => ({
                  ...prev,
                  [uniqueId]: previousIconCount + i + 1,
                }));
              }, i * 150);
            }
          }, 200);

          hideMoreIconsForm(uniqueId);
          void checkAuthenticationStatus();
        } else {
          setStreamingResults((prev) => ({
            ...prev,
            [uniqueId]: {
              ...prev[uniqueId],
              status: "error",
              message: data.message || "Failed to generate more icons",
            },
          }));
        }
      } catch (error) {
        console.error("Error generating more icons:", error);
        setStreamingResults((prev) => ({
          ...prev,
          [uniqueId]: {
            ...prev[uniqueId],
            status: "error",
            message: "Failed to generate more icons. Please try again.",
          },
        }));
      } finally {
        setIsGenerating(false);
        stopOverallProgressTimer();
        setOverallProgress(100);
      }
    },
    [
      authState,
      checkAuthenticationStatus,
      currentRequest,
      currentResponse,
      getServiceResults,
      hideMoreIconsForm,
      moreIconsDescriptions,
      setAnimatingIcons,
      startOverallProgressTimer,
      stopOverallProgressTimer,
      streamingResults,
    ],
  );

  const generateMoreIllustrations = useCallback(
    async (serviceId: string, serviceName: string, generationIndex: number) => {
      const user = authState?.user;
      const regularCoins = user?.coins ?? 0;
      const trialCoins = user?.trialCoins ?? 0;

      if (regularCoins < 1 && trialCoins === 0) {
        setErrorMessage(
          "Insufficient coins. You need 1 coin to generate more illustrations.",
        );
        setUiState("error");
        return;
      }

      const uniqueId = `${serviceId}-gen${generationIndex}`;
      const descriptions = moreIconsDescriptions[uniqueId] || [];
      const serviceResults = getServiceResults(serviceId, generationIndex);
      if (!serviceResults?.originalGridImageBase64) {
        setErrorMessage("Original image not found for this service");
        setUiState("error");
        return;
      }

      setIsGenerating(true);
      setOverallProgress(0);

      const duration = 35000;
      startOverallProgressTimer(duration);

      setStreamingResults((prev) => ({
        ...prev,
        [uniqueId]: {
          ...prev[uniqueId],
          status: "started",
          message: "Generating more illustrations...",
        },
      }));

      const moreIllustrationsRequest = {
        originalRequestId: currentResponse?.requestId,
        serviceName: serviceId,
        originalImageBase64: serviceResults.originalGridImageBase64,
        generalDescription: currentRequest?.generalDescription,
        illustrationDescriptions: descriptions,
        seed: serviceResults.seed,
        generationIndex,
      };

      try {
        const response = await fetch("/api/illustrations/generate/more", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify(moreIllustrationsRequest),
        });
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();

        if (data.status === "success") {
          setCurrentIcons((prev) => prev.concat(data.newIllustrations));

          const previousIconCount =
            streamingResults[uniqueId]?.icons?.length || 0;
          setStreamingResults((prev) => ({
            ...prev,
            [uniqueId]: {
              ...prev[uniqueId],
              status: "success",
              message: "More illustrations generated successfully",
              icons: [
                ...(prev[uniqueId]?.icons || []),
                ...data.newIllustrations,
              ],
            },
          }));

          setTimeout(() => {
            setAnimatingIcons((prev) => ({
              ...prev,
              [uniqueId]: previousIconCount,
            }));
            for (let i = 0; i < data.newIllustrations.length; i++) {
              setTimeout(() => {
                setAnimatingIcons((prev) => ({
                  ...prev,
                  [uniqueId]: previousIconCount + i + 1,
                }));
              }, i * 150);
            }
          }, 200);

          hideMoreIconsForm(uniqueId);
          void checkAuthenticationStatus();
        } else {
          setStreamingResults((prev) => ({
            ...prev,
            [uniqueId]: {
              ...prev[uniqueId],
              status: "error",
              message:
                data.message || "Failed to generate more illustrations",
            },
          }));
        }
      } catch (error) {
        console.error("Error generating more illustrations:", error);
        setStreamingResults((prev) => ({
          ...prev,
          [uniqueId]: {
            ...prev[uniqueId],
            status: "error",
            message: "Failed to generate more illustrations. Please try again.",
          },
        }));
      } finally {
        setIsGenerating(false);
        stopOverallProgressTimer();
        setOverallProgress(100);
      }
    },
    [
      authState,
      checkAuthenticationStatus,
      currentRequest,
      currentResponse,
      getServiceResults,
      hideMoreIconsForm,
      moreIconsDescriptions,
      setAnimatingIcons,
      startOverallProgressTimer,
      stopOverallProgressTimer,
      streamingResults,
    ],
  );

  return {
    uiState,
    setUiState,
    errorMessage,
    setErrorMessage,
    isGenerating,
    setIsGenerating,
    currentResponse,
    isTrialResult,
    streamingResults,
    showResultsPanes,
    overallProgress,
    calculateTimeRemaining,
    generateIcons,
    generateMoreIcons,
    generateMoreIllustrations,
    moreIconsVisible,
    showMoreIconsForm,
    hideMoreIconsForm,
    moreIconsDescriptions,
    setMoreIconsDescriptions,
    getServiceDisplayName,
    animatingIcons,
    getIconAnimationClass,
  };
}
