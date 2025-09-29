"use client";

import { useState, useRef, useEffect } from "react";
import {
  Icon,
  ServiceResult,
  StreamingResults,
  GenerationResponse,
  UIState,
} from "../../lib/types";
import Navigation from "../../components/Navigation";
import GeneratorForm from "../../components/GeneratorForm";
import ResultsDisplay from "../../components/ResultsDisplay";
import ExportModal from "../../components/ExportModal";
import ProgressModal from "../../components/ProgressModal";
import { useAuth } from "../../context/AuthContext";

export default function Page() {
  const { authState, checkAuthenticationStatus } = useAuth();

  // Form state
  const [inputType, setInputType] = useState("text");
  
  const [generateVariations, setGenerateVariations] = useState(false);
  const [generalDescription, setGeneralDescription] = useState("");
  const [individualDescriptions, setIndividualDescriptions] = useState<
    string[]
  >([]);
  const [referenceImage, setReferenceImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string>("");

  // UI state
  const [uiState, setUiState] = useState<UIState>("initial");
  const [errorMessage, setErrorMessage] = useState("");
  const [isGenerating, setIsGenerating] = useState(false);

  // Generation data
  const [currentIcons, setCurrentIcons] = useState<Icon[]>([]);
  const [currentRequest, setCurrentRequest] = useState<any>(null);
  const [currentResponse, setCurrentResponse] =
    useState<GenerationResponse | null>(null);
  const [streamingResults, setStreamingResults] = useState<StreamingResults>(
    {},
  );
  const [showResultsPanes, setShowResultsPanes] = useState(false);

  // Modal state
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [exportContext, setExportContext] = useState<any>(null);
  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  // Generate more state
  const [moreIconsVisible, setMoreIconsVisible] = useState<{
    [key: string]: boolean;
  }>({});
  const [moreIconsDescriptions, setMoreIconsDescriptions] = useState<{
    [key: string]: string[];
  }>({});

  // Recovery state for SSE disconnections
  const [pendingRequestId, setPendingRequestId] = useState<string | null>(null);
  const currentEventSourceRef = useRef<EventSource | null>(null);
  const isRecoveredRef = useRef<boolean>(false);

  // Animation state
  const [animatingIcons, setAnimatingIcons] = useState<{
    [key: string]: number;
  }>({});
  const [animationTimers, setAnimationTimers] = useState<{
    [key: string]: NodeJS.Timeout[];
  }>({});

  // Unified progress timer
  const [overallProgress, setOverallProgress] = useState(0);
  const [totalDuration, setTotalDuration] = useState(0);
  const overallProgressTimerRef = useRef<NodeJS.Timeout | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setIndividualDescriptions(new Array(9).fill(""));
  }, []);

  // Handle generate more mode from gallery
  useEffect(() => {
    const generateMoreMode = sessionStorage.getItem("generateMoreMode");
    const gridImageUrl = sessionStorage.getItem("generatedGridImage");
    
    if (generateMoreMode === "true" && gridImageUrl) {
      // Switch to image tab
      setInputType("image");
      
      // Convert the blob URL to a File object and set it as reference image
      fetch(gridImageUrl)
        .then((response) => response.blob())
        .then((blob) => {
          const file = new File([blob], "grid-composition.png", { 
            type: "image/png",
            lastModified: Date.now()
          });
          
          setReferenceImage(file);
          setImagePreview(gridImageUrl);
          
          // Clean up sessionStorage
          sessionStorage.removeItem("generateMoreMode");
          sessionStorage.removeItem("generatedGridImage");
        })
        .catch((error) => {
          console.error("Error setting up grid image:", error);
          // Clean up on error
          sessionStorage.removeItem("generateMoreMode");
          sessionStorage.removeItem("generatedGridImage");
        });
    }
  }, []);

  useEffect(() => {
    return () => {
      if (overallProgressTimerRef.current) {
        clearInterval(overallProgressTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    return () => {
      Object.values(animationTimers).forEach((timers) => {
        timers.forEach((timer) => clearTimeout(timer));
      });
    };
  }, [animationTimers]);

  // Cleanup SSE connection on unmount
  useEffect(() => {
    return () => {
      if (currentEventSourceRef.current) {
        currentEventSourceRef.current.close();
        currentEventSourceRef.current = null;
      }
    };
  }, []);

  // Page visibility listener for generation recovery
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        // User returned to page, check for pending generations
        handleGenerationRecovery();
      }
    };

    const handleFocus = () => {
      // Also check when window gets focus (for cases where visibility API might not work)
      handleGenerationRecovery();
    };

    // Check immediately on mount in case user refreshed during generation
    handleGenerationRecovery();

    // Listen for page visibility changes
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleFocus);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);

  const startIconAnimation = (serviceId: string, iconCount: number) => {
    if (animationTimers[serviceId]) {
      animationTimers[serviceId].forEach((timer) => clearTimeout(timer));
    }
    setAnimatingIcons((prev) => ({ ...prev, [serviceId]: 0 }));
    const timers: NodeJS.Timeout[] = [];
    for (let i = 0; i < iconCount; i++) {
      const timer = setTimeout(() => {
        setAnimatingIcons((prev) => ({ ...prev, [serviceId]: i + 1 }));
      }, i * 150);
      timers.push(timer);
    }
    setAnimationTimers((prev) => ({ ...prev, [serviceId]: timers }));
  };

  const clearIconAnimation = (serviceId: string) => {
    if (animationTimers[serviceId]) {
      animationTimers[serviceId].forEach((timer) => clearTimeout(timer));
      setAnimationTimers((prev) => {
        const newTimers = { ...prev };
        delete newTimers[serviceId];
        return newTimers;
      });
    }
    setAnimatingIcons((prev) => {
      const newAnimating = { ...prev };
      delete newAnimating[serviceId];
      return newAnimating;
    });
  };

  const getIconAnimationClass = (serviceId: string, iconIndex: number) => {
    const visibleCount = animatingIcons[serviceId] || 0;
    const isVisible = iconIndex < visibleCount;
    return isVisible
      ? "opacity-100 scale-100 transition-all duration-500 ease-out"
      : "opacity-0 scale-75 transition-all duration-500 ease-out";
  };

  const fileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        const base64 = (reader.result as string).split(",")[1];
        resolve(base64);
      };
      reader.onerror = (error) => reject(error);
    });
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  const getServiceDisplayName = (serviceId: string): string => {
    const serviceNames: { [key: string]: string } = {
      flux: "Flux-Pro",
      recraft: "Recraft V3",
      photon: "Luma Photon",
      gpt: "GPT Image",
      imagen: "Imagen 4",
    };
    return serviceNames[serviceId] || serviceId;
  };

  // localStorage utilities for generation state persistence
  const saveGenerationState = (requestId: string, request: any) => {
    try {
      const generationState = {
        requestId,
        request,
        timestamp: Date.now(),
      };
      localStorage.setItem('pendingGeneration', JSON.stringify(generationState));
      setPendingRequestId(requestId);
    } catch (error) {
      console.warn('Failed to save generation state to localStorage:', error);
    }
  };

  const getGenerationState = () => {
    try {
      const storedState = localStorage.getItem('pendingGeneration');
      if (storedState) {
        const parsed = JSON.parse(storedState);
        // Only return state if it's less than 2 hours old
        if (Date.now() - parsed.timestamp < 2 * 60 * 60 * 1000) {
          return parsed;
        } else {
          // Clean up old state
          localStorage.removeItem('pendingGeneration');
        }
      }
    } catch (error) {
      console.warn('Failed to get generation state from localStorage:', error);
    }
    return null;
  };

  const clearGenerationState = () => {
    try {
      localStorage.removeItem('pendingGeneration');
      setPendingRequestId(null);
    } catch (error) {
      console.warn('Failed to clear generation state from localStorage:', error);
    }
  };

  // Check generation status using the new endpoint
  const checkGenerationStatus = async (requestId: string): Promise<any> => {
    try {
      const response = await fetch(`/status/${requestId}`, {
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
  };

  // Recovery function to handle completed generations after SSE disconnect
  const handleGenerationRecovery = async () => {
    const savedState = getGenerationState();
    if (!savedState) return;

    const { requestId, request } = savedState;

    try {
      const statusResult = await checkGenerationStatus(requestId);
      
      if (statusResult.status === "completed" && statusResult.data) {
        // Close any existing SSE connection since we're recovering completed results
        if (currentEventSourceRef.current) {
          currentEventSourceRef.current.close();
          currentEventSourceRef.current = null;
        }
 
        // Restore the original request data
        setCurrentRequest(request);
        
        // Process the completed response similar to handleGenerationComplete
        const completedResponse = statusResult.data;
        
        // Extract icons and set up state as if generation just completed
        if (completedResponse.icons && completedResponse.icons.length > 0) {
          setCurrentIcons(completedResponse.icons);
          setCurrentResponse(completedResponse);
          
          // Build streaming results from the completed response
          // Infer which services were enabled by checking which results exist
          const updatedStreamingResults: any = {};
          
          // Map completed results to streaming format
          const serviceMapping = {
            falAiResults: "flux",
            recraftResults: "recraft", 
            photonResults: "photon",
            gptResults: "gpt",
            imagenResults: "imagen"
          };
          
          // Build enabled services map from actual response data
          const inferredEnabledServices: { [key: string]: boolean } = {};
          
          Object.entries(serviceMapping).forEach(([responseKey, serviceId]) => {
            const serviceResults = completedResponse[responseKey];
            if (serviceResults && Array.isArray(serviceResults)) {
              // Service is enabled if it has results and they're not all "disabled"
              const hasEnabledResults = serviceResults.some((result: any) => result.status !== "disabled");
              if (hasEnabledResults) {
                inferredEnabledServices[serviceId] = true;
                
                serviceResults.forEach((result: any) => {
                  if (result.status !== "disabled") {
                    const uniqueId = `${serviceId}-gen${result.generationIndex || 1}`;
                    updatedStreamingResults[uniqueId] = {
                      icons: result.icons || [],
                      generationTimeMs: result.generationTimeMs || 0,
                      status: result.status,
                      message: result.message,
                      generationIndex: result.generationIndex || 1,
                      originalGridImageBase64: result.originalGridImageBase64,
                    };
                  }
                });
              }
            }
          });
          
          setStreamingResults(updatedStreamingResults);
          
          // Set UI to results state
          setUiState("results");
          setIsGenerating(false);
          setShowResultsPanes(true);
          setOverallProgress(100);
          
          // Start animations
          setTimeout(() => {
            Object.entries(updatedStreamingResults).forEach(([serviceId, result]) => {
              if (result && typeof result === 'object' && 'status' in result && 'icons' in result) {
                if (result.status === "success" && Array.isArray(result.icons) && result.icons.length > 0) {
                  startIconAnimation(serviceId, result.icons.length);
                }
              }
            });
          }, 300);
          
        // Mark as recovered to prevent SSE errors from showing
        isRecoveredRef.current = true;
        
        // Clear the saved state since we've recovered successfully
        clearGenerationState();
        
        // Update authentication status
        checkAuthenticationStatus();
        }
      } else if (statusResult.status === "in_progress") {
        // Keep the saved state, user can check again later
      } else {
        // Clear the outdated saved state
        clearGenerationState();
      }
    } catch (error) {
      console.error("Error during generation recovery:", error);
    }
  };

  const calculateTimeRemaining = () => {
    if (overallProgress >= 100) return "0s";
    const remainingMs = totalDuration * (1 - overallProgress / 100);
    const remainingSeconds = Math.round(remainingMs / 1000);
    return `${remainingSeconds}s`;
  };

  const handleImageSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      setReferenceImage(null);
      setImagePreview("");
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
    setReferenceImage(file);
    const reader = new FileReader();
    reader.onload = (e) => {
      setImagePreview(e.target?.result as string);
    };
    reader.readAsDataURL(file);
  };

  const removeImage = () => {
    setReferenceImage(null);
    setImagePreview("");
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const validateForm = (): boolean => {
    if (inputType === "text" && !generalDescription.trim()) {
      setErrorMessage("Please provide a general description.");
      return false;
    }
    if (inputType === "image" && !referenceImage) {
      setErrorMessage("Please select a reference image.");
      return false;
    }
    
    const cost = generateVariations ? 2 : 1;
    if (authState.user) {
      const regularCoins = authState.user.coins || 0;
      const trialCoins = authState.user.trialCoins || 0;
      
      // Check if user has enough regular coins, or has trial coins (trial coins work regardless of cost)
      const hasEnoughRegularCoins = regularCoins >= cost;
      const hasTrialCoins = trialCoins > 0;
      
      if (!hasEnoughRegularCoins && !hasTrialCoins) {
        setErrorMessage(`Insufficient coins. You need ${cost} coin${cost > 1 ? 's' : ''} to generate icons, or you can use your trial coin for a limited experience.`);
        return false;
      }
    }
    
    return true;
  };

  const generateIcons = async () => {
    if (!validateForm()) {
      setUiState("error");
      return;
    }
    setIsGenerating(true);
    setUiState("streaming");
    setStreamingResults({});
    setShowResultsPanes(false);
    setOverallProgress(0);
    
    // Reset recovery flag for new generation
    isRecoveredRef.current = false;
    if (overallProgressTimerRef.current) {
      clearInterval(overallProgressTimerRef.current);
    }
    Object.keys(animationTimers).forEach((serviceId) => {
      clearIconAnimation(serviceId);
    });
    let duration = 40000;
    if (inputType === "image") {
      duration = 70000;
    }
    setTotalDuration(duration);
    const increment = 100 / (duration / 100);
    overallProgressTimerRef.current = setInterval(() => {
      setOverallProgress((prev) => {
        const newProgress = prev + increment;
        if (newProgress >= 100) {
          if (overallProgressTimerRef.current) {
            clearInterval(overallProgressTimerRef.current);
          }
          return 100;
        }
        return newProgress;
      });
    }, 100);
    const formData: any = {
            iconCount: 9,
      generationsPerService: generateVariations ? 2 : 1,
      individualDescriptions: individualDescriptions.filter((desc) =>
        desc.trim(),
      ),
    };
    if (inputType === "text") {
      formData.generalDescription = generalDescription.trim();
    } else if (inputType === "image" && referenceImage) {
      try {
        formData.referenceImageBase64 = await fileToBase64(referenceImage);
      } catch (error) {
        console.error("Error converting image to base64:", error);
        setErrorMessage("Failed to process reference image");
        setUiState("error");
        setIsGenerating(false);
        if (overallProgressTimerRef.current)
          clearInterval(overallProgressTimerRef.current);
        return;
      }
    }
    setCurrentRequest({ ...formData });
    try {
      const response = await fetch("/generate-stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(formData),
      });
      if (!response.ok) {
        const responseText = await response.text();
        console.error("❌ Generation request failed:", response.status, responseText);
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      const { requestId, enabledServices } = data;
      
      // Save generation state for recovery (without internal server config)
      saveGenerationState(requestId, formData);
      
      initializeStreamingResults(enabledServices);
      
      // Close any existing EventSource before creating a new one
      if (currentEventSourceRef.current) {
        currentEventSourceRef.current.close();
      }
      
      const eventSource = new EventSource(`/stream/${requestId}`);
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
          // Only show error if we haven't already recovered successfully
          if (!isRecoveredRef.current) {
            setErrorMessage(update.message || "Generation failed");
            setUiState("error");
            setIsGenerating(false);
            if (overallProgressTimerRef.current)
              clearInterval(overallProgressTimerRef.current);
          }
          eventSource.close();
          currentEventSourceRef.current = null;
        } catch (error) {
          console.error("Error parsing error update:", error);
          // Only show error if we haven't already recovered successfully
          if (!isRecoveredRef.current) {
            setErrorMessage("Generation failed with unknown error");
            setUiState("error");
            setIsGenerating(false);
            if (overallProgressTimerRef.current)
              clearInterval(overallProgressTimerRef.current);
          }
          eventSource.close();
          currentEventSourceRef.current = null;
        }
      });
      eventSource.onerror = (error) => {
        console.error("EventSource error:", error);
        // Only show connection error if we haven't already recovered successfully
        if (!isRecoveredRef.current) {
          setTimeout(() => {
            // Double-check we haven't recovered in the meantime
            if (!isRecoveredRef.current) {
              setErrorMessage("Connection error. Please try again.");
              setUiState("error");
              setIsGenerating(false);
              if (overallProgressTimerRef.current)
                clearInterval(overallProgressTimerRef.current);
            }
          }, 100); // Small delay to ensure any concurrent recovery completes
        }
        
        eventSource.close();
        currentEventSourceRef.current = null;
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
      if (overallProgressTimerRef.current)
        clearInterval(overallProgressTimerRef.current);
    }
  };

  const initializeStreamingResults = (enabledServices: {
    [key: string]: boolean;
  }) => {
    const newResults: StreamingResults = {};
    const allServices = [
      { id: "flux", name: "Flux-Pro" },
      { id: "recraft", name: "Recraft V3" },
      { id: "photon", name: "Luma Photon" },
      { id: "gpt", name: "" },
      { id: "imagen", name: "Imagen 4" },
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
  };

  const handleServiceUpdate = (update: any) => {
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
  };

  const handleGenerationComplete = (update: any) => {
    if (overallProgressTimerRef.current) {
      clearInterval(overallProgressTimerRef.current);
    }
    setOverallProgress(100);
    setShowResultsPanes(true);
    
    setStreamingResults((latestStreamingResults) => {
      // Always use the final icons from the completion update if available
      // This ensures trial limitations are properly applied
      const finalIcons = update.icons && update.icons.length > 0 ? update.icons : null;
      
      if (finalIcons) {
        setCurrentIcons(finalIcons);
        
        // Update streaming results with the final limited icons
        const updatedStreamingResults = { ...latestStreamingResults };
        
        // Map final icons back to their respective services
        Object.keys(updatedStreamingResults).forEach((serviceKey) => {
          const baseServiceId = serviceKey.replace(/-gen\d+$/, "");
          // Filter icons from final response that belong to this service
          const serviceIcons = finalIcons.filter((icon: any) => {
            const iconService = icon.serviceSource?.toLowerCase();
            return iconService === baseServiceId || 
                   (baseServiceId === "flux" && (iconService === "falai" || iconService === "flux-pro"));
          });
          
          if (serviceIcons.length > 0) {
            const isTrialMode = update.message && update.message.includes("Trial Mode");
            updatedStreamingResults[serviceKey] = {
              ...updatedStreamingResults[serviceKey],
              icons: serviceIcons,
              message: updatedStreamingResults[serviceKey].message + 
                      (isTrialMode ? " (Trial: 5 of 9 icons)" : "")
            };
          }
        });
        
        // Build grouped results from updated streaming results
        const groupedResults = {
          falAiResults: [] as ServiceResult[],
          recraftResults: [] as ServiceResult[],
          photonResults: [] as ServiceResult[],
          gptResults: [] as ServiceResult[],
          imagenResults: [] as ServiceResult[],
        };
        
        Object.entries(updatedStreamingResults).forEach(([serviceKey, result]) => {
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
            case "imagen":
              groupedResults.imagenResults.push(result);
              break;
          }
        });
        
        setCurrentResponse({
          icons: finalIcons,
          ...groupedResults,
          requestId: update.requestId,
        });
        
        // Start animations with the correct icon count
        setTimeout(() => {
          Object.entries(updatedStreamingResults).forEach(
            ([serviceId, result]) => {
              if (result.status === "success" && result.icons && result.icons.length > 0) {
                startIconAnimation(serviceId, result.icons.length);
              }
            },
          );
        }, 300);
        
      } else {
        // Fallback to streaming results if no final icons in update
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
          imagenResults: [] as ServiceResult[],
        };
        Object.entries(latestStreamingResults).forEach(([serviceKey, result]) => {
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
            case "imagen":
              groupedResults.imagenResults.push(result);
              break;
          }
        });
        setCurrentResponse({
          icons: allIcons,
          ...groupedResults,
          requestId: update.requestId,
        });
      }
      
      setUiState("results");
      setIsGenerating(false);
      
      // Clear the pending generation state since it completed successfully
      clearGenerationState();
      
      // Clear EventSource reference since generation completed
      if (currentEventSourceRef.current) {
        currentEventSourceRef.current = null;
      }
      
      // Mark as completed to prevent any delayed SSE errors
      isRecoveredRef.current = true;
      
      // Refresh coins after successful generation
      setTimeout(() => {
        checkAuthenticationStatus();
      }, 3000); // 3-second delay to ensure backend transaction is committed
      
      return latestStreamingResults;
    });
  };

  const exportGeneration = (
    requestId: string,
    serviceName: string,
    generationIndex: number,
  ) => {
    setExportContext({ requestId, serviceName, generationIndex });
    setShowExportModal(true);
  };

  const confirmExport = (formats: string[]) => {
    if (exportContext) {
      const { requestId, serviceName, generationIndex } = exportContext;
      const fileName = `icon-pack-${requestId}-${serviceName}-gen${generationIndex}.zip`;
      const exportData = {
        requestId: requestId,
        serviceName: serviceName,
        generationIndex: generationIndex,
        formats: formats,
      };
      setShowExportModal(false);
      downloadZip(exportData, fileName);
    }
  };

  const downloadZip = async (exportData: any, fileName: string) => {
    setShowProgressModal(true);
    setExportProgress({
      step: 1,
      message: "Preparing export request...",
      percent: 25,
    });
    try {
      setTimeout(() => {
        setExportProgress({
          step: 2,
          message: "Converting icons to multiple formats and sizes...",
          percent: 50,
        });
      }, 500);
      const response = await fetch("/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(exportData),
      });
      setExportProgress({
        step: 3,
        message: "Creating ZIP file...",
        percent: 75,
      });
      if (!response.ok)
        throw new Error(`HTTP error! status: ${response.status}`);
      const blob = await response.blob();
      setExportProgress({
        step: 4,
        message: "Finalizing download...",
        percent: 100,
      });
      setTimeout(() => {
        setShowProgressModal(false);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.style.display = "none";
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        // Icon pack downloaded successfully
      }, 1000);
    } catch (error) {
      console.error("Error exporting icons:", error);
      setShowProgressModal(false);
      setErrorMessage("Failed to export icons. Please try again.");
      setUiState("error");
    }
  };

  // Removed toast functions - now using progress UI instead of alerts

  const showMoreIconsForm = (uniqueId: string) => {
    setMoreIconsVisible((prev) => ({ ...prev, [uniqueId]: true }));
    setMoreIconsDescriptions((prev) => ({
      ...prev,
      [uniqueId]: new Array(9).fill(""),
    }));
  };

  const hideMoreIconsForm = (uniqueId: string) => {
    setMoreIconsVisible((prev) => ({ ...prev, [uniqueId]: false }));
    setMoreIconsDescriptions((prev) => ({
      ...prev,
      [uniqueId]: new Array(9).fill(""),
    }));
  };

  const generateMoreIcons = async (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
  ) => {
    // Check if user has enough coins (regular coins or trial coins)
    if (authState.user) {
      const regularCoins = authState.user.coins || 0;
      const trialCoins = authState.user.trialCoins || 0;
      
      if (regularCoins < 1 && trialCoins === 0) {
        setErrorMessage("Insufficient coins. You need 1 coin to generate more icons.");
        setUiState("error");
        return;
      }
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
    if (overallProgressTimerRef.current) {
      clearInterval(overallProgressTimerRef.current);
    }

    let duration = 35000; // Default duration
    setTotalDuration(duration);
    const increment = 100 / (duration / 100);
    overallProgressTimerRef.current = setInterval(() => {
      setOverallProgress((prev) => {
        const newProgress = prev + increment;
        if (newProgress >= 100) {
          if (overallProgressTimerRef.current) {
            clearInterval(overallProgressTimerRef.current);
          }
          return 100;
        }
        return newProgress;
      });
    }, 100);

    // Show progress for the specific generation
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
      generationIndex: generationIndex, // Include generation index
    };

    try {
      const response = await fetch("/generate-more", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(moreIconsRequest),
      });
      if (!response.ok)
        throw new Error(`HTTP error! status: ${response.status}`);
      const data = await response.json();

      if (data.status === "success") {
        // Update current icons list
        setCurrentIcons((prev) => prev.concat(data.newIcons));

        // Update streaming results
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

        // Animate new icons
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
        // Refresh coins after successful more icons generation
        checkAuthenticationStatus();
      } else {
        // Show error in streaming results
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
      if (overallProgressTimerRef.current) {
        clearInterval(overallProgressTimerRef.current);
      }
      setOverallProgress(100); // Ensure progress is complete
    }
  };

  const getServiceResults = (
    serviceId: string,
    generationIndex: number,
  ): ServiceResult | null => {
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
      case "imagen":
        resultsArray = currentResponse.imagenResults;
        break;
      default:
        return null;
    }
    if (resultsArray && resultsArray.length > 0) {
      return (
        resultsArray.find((r) => r.generationIndex === generationIndex) || null
      );
    }
    return null;
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation useLoginPage={true} />
      <div className="flex flex-col lg:flex-row lg:h-screen">
        <GeneratorForm
          inputType={inputType}
          setInputType={setInputType}
          generateVariations={generateVariations}
          setGenerateVariations={setGenerateVariations}
          
          generalDescription={generalDescription}
          setGeneralDescription={setGeneralDescription}
          individualDescriptions={individualDescriptions}
          setIndividualDescriptions={setIndividualDescriptions}
          referenceImage={referenceImage}
          imagePreview={imagePreview}
          isGenerating={isGenerating}
          generateIcons={generateIcons}
          handleImageSelect={handleImageSelect}
          removeImage={removeImage}
          fileInputRef={fileInputRef}
          formatFileSize={formatFileSize}
        />

        <ResultsDisplay
          uiState={uiState}
          generateVariations={generateVariations}
          isGenerating={isGenerating}
          overallProgress={overallProgress}
          calculateTimeRemaining={calculateTimeRemaining}
          errorMessage={errorMessage}
          streamingResults={streamingResults}
          showResultsPanes={showResultsPanes}
          getIconAnimationClass={getIconAnimationClass}
          animatingIcons={animatingIcons}
          exportGeneration={exportGeneration}
          currentResponse={currentResponse}
          moreIconsVisible={moreIconsVisible}
          showMoreIconsForm={showMoreIconsForm}
          hideMoreIconsForm={hideMoreIconsForm}
          generateMoreIcons={generateMoreIcons}
          moreIconsDescriptions={moreIconsDescriptions}
          setMoreIconsDescriptions={setMoreIconsDescriptions}
          getServiceDisplayName={getServiceDisplayName}
          setIsGenerating={setIsGenerating}
        />
      </div>
      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmExport}
        iconCount={
          exportContext
            ? streamingResults[
                `${exportContext.serviceName}-gen${exportContext.generationIndex}`
              ]?.icons?.length || 0
            : 0
        }
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />
    </div>
  );
}