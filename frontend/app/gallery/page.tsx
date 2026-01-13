"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import Navigation from "../../components/Navigation";
import ExportModal from "../../components/ExportModal";
import ProgressModal from "../../components/ProgressModal";
import GifModal, { GifModalProgress } from "@/components/GifModal";
import { Download, Sparkles, Trash2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { GifAsset, GifProgressUpdate } from "@/lib/types";

// Local Icon type for the gallery page, matching the backend DTO
interface Icon {
  id: number;
  iconId: string;
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
  iconType: string;
  theme: string;
  watermarked?: boolean;
}

interface Illustration {
  id: number;
  imageUrl: string;
  description: string;
  requestId: string;
  illustrationType: string;
  theme: string;
  watermarked?: boolean;
}

interface Mockup {
  id: number;
  imageUrl: string;
  description: string;
  requestId: string;
  mockupType: string;
  theme: string;
}

interface LabelItem {
  id: number;
  imageUrl: string;
  filePath?: string;
  labelText: string;
  requestId: string;
  labelType: string;
  serviceSource: string;
  theme: string;
}

interface IconGroup {
  original: Icon[];
  variation: Icon[];
  gifs: Icon[];
}

type GroupedIcons = Record<string, IconGroup>;

interface GifModalState {
  requestId: string;
  iconType: "original" | "variation";
  icons: Icon[];
}

type GroupedIllustrations = Record<string, { original: Illustration[]; variation: Illustration[] }>;
type GroupedMockups = Record<string, { original: Mockup[]; variation: Mockup[] }>;
type GroupedLabels = Record<string, { original: LabelItem[]; variation: LabelItem[] }>;

type GridGenerationMode = "icons" | "mockups" | "labels";

const GRID_SIZE = 3;
const ICON_SIZE = 300;
const LINE_WIDTH = 2;
const GRID_LINE_COLOR = "rgba(0, 0, 0, 0.2)";
const TOTAL_GRID_CELLS = GRID_SIZE * GRID_SIZE;

const canvasToBlob = (canvas: HTMLCanvasElement): Promise<Blob> =>
  new Promise((resolve, reject) => {
    if (canvas.toBlob) {
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob);
        } else {
          reject(new Error("Failed to convert grid canvas to blob."));
        }
      }, "image/png");
    } else {
      try {
        const dataUrl = canvas.toDataURL("image/png");
        const base64 = dataUrl.split(",")[1];
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        resolve(new Blob([bytes], { type: "image/png" }));
      } catch (error) {
        reject(error instanceof Error ? error : new Error("Failed to convert canvas to blob."));
      }
    }
  });

const loadBlobAsImage = (blob: Blob) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const objectUrl = URL.createObjectURL(blob);
    const image = new Image();
    image.decoding = "async";
    image.onload = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("Failed to decode icon image."));
    };
    image.src = objectUrl;
  });

const fetchImageElement = async (imageUrl: string): Promise<HTMLImageElement> => {
  const response = await fetch(imageUrl, { credentials: "include" });
  if (!response.ok) {
    throw new Error(`Failed to fetch icon image: ${response.status}`);
  }
  const blob = await response.blob();
  return loadBlobAsImage(blob);
};

const drawPlaceholder = (ctx: CanvasRenderingContext2D, x: number, y: number) => {
  ctx.fillStyle = "#f3f4f6";
  ctx.fillRect(x, y, ICON_SIZE, ICON_SIZE);
  ctx.strokeStyle = "#d1d5db";
  ctx.lineWidth = 2;
  ctx.strokeRect(x + 1, y + 1, ICON_SIZE - 2, ICON_SIZE - 2);
  ctx.fillStyle = "#9ca3af";
  ctx.font = "bold 36px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("?", x + ICON_SIZE / 2, y + ICON_SIZE / 2);
};

const drawImageInCell = (
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement,
  x: number,
  y: number,
) => {
  const originalWidth = image.naturalWidth || image.width;
  const originalHeight = image.naturalHeight || image.height;
  if (!originalWidth || !originalHeight) {
    drawPlaceholder(ctx, x, y);
    return;
  }

  const scale = Math.min(ICON_SIZE / originalWidth, ICON_SIZE / originalHeight);
  const scaledWidth = originalWidth * scale;
  const scaledHeight = originalHeight * scale;
  const offsetX = x + (ICON_SIZE - scaledWidth) / 2;
  const offsetY = y + (ICON_SIZE - scaledHeight) / 2;

  ctx.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight);
};

const composeGridFromIconUrls = async (iconUrls: string[]): Promise<Blob> => {
  const imageCache = new Map<string, Promise<HTMLImageElement | null>>();
  const images = await Promise.all(
    iconUrls.map((url) => {
      if (!imageCache.has(url)) {
        imageCache.set(
          url,
          fetchImageElement(url).catch((error) => {
            console.warn("Failed to load icon for grid", error);
            return null;
          }),
        );
      }
      return imageCache.get(url)!;
    }),
  );

  const totalWidth = GRID_SIZE * ICON_SIZE + (GRID_SIZE - 1) * LINE_WIDTH;
  const totalHeight = totalWidth;
  const canvas = document.createElement("canvas");
  canvas.width = totalWidth;
  canvas.height = totalHeight;
  const ctx = canvas.getContext("2d");

  if (!ctx) {
    throw new Error("Unable to create drawing context for grid.");
  }

  ctx.clearRect(0, 0, totalWidth, totalHeight);

  images.forEach((image, index) => {
    const row = Math.floor(index / GRID_SIZE);
    const col = index % GRID_SIZE;
    const x = col * (ICON_SIZE + LINE_WIDTH);
    const y = row * (ICON_SIZE + LINE_WIDTH);

    if (image) {
      drawImageInCell(ctx, image, x, y);
    } else {
      drawPlaceholder(ctx, x, y);
    }
  });

  ctx.strokeStyle = GRID_LINE_COLOR;
  ctx.lineWidth = LINE_WIDTH;
  for (let i = 1; i < GRID_SIZE; i++) {
    const linePosition = i * (ICON_SIZE + LINE_WIDTH) - LINE_WIDTH / 2;
    ctx.beginPath();
    ctx.moveTo(linePosition, 0);
    ctx.lineTo(linePosition, totalHeight);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(0, linePosition);
    ctx.lineTo(totalWidth, linePosition);
    ctx.stroke();
  }

  return canvasToBlob(canvas);
};

export default function GalleryPage() {
  const router = useRouter();
  const { authState, checkAuthenticationStatus } = useAuth();
  const [groupedIcons, setGroupedIcons] = useState<GroupedIcons>({});
  const [groupedIllustrations, setGroupedIllustrations] = useState<GroupedIllustrations>({});
  const [groupedMockups, setGroupedMockups] = useState<GroupedMockups>({});
  const [groupedLabels, setGroupedLabels] = useState<GroupedLabels>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRequest, setSelectedRequest] = useState<string | null>(null);
  const [galleryType, setGalleryType] = useState<string | null>(null);
  const [isRemovingWatermark, setIsRemovingWatermark] = useState(false);

  // Export state
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [iconsToExport, setIconsToExport] = useState<Icon[]>([]);
  const [illustrationsToExport, setIllustrationsToExport] = useState<Illustration[]>([]);
  const [mockupsToExport, setMockupsToExport] = useState<Mockup[]>([]);
  const [labelsToExport, setLabelsToExport] = useState<LabelItem[]>([]);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteRequestId, setDeleteRequestId] = useState<string | null>(null);
  const [deleteTargetType, setDeleteTargetType] = useState<string | null>(null);
  const [deleteGenerationType, setDeleteGenerationType] = useState<string | null>(null);
  const [isDeletingRequest, setIsDeletingRequest] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const gifEventSourceRef = useRef<EventSource | null>(null);
  const [gifRefreshToken, setGifRefreshToken] = useState(0);
  const [gifModalState, setGifModalState] = useState<GifModalState | null>(null);
  const [selectedGifIcons, setSelectedGifIcons] = useState<Set<string>>(() => new Set<string>());
  const [gifProgress, setGifProgress] = useState<GifModalProgress>({
    status: "idle",
    message: "",
    total: 0,
    completed: 0,
    percent: 0,
  });
  const [gifResults, setGifResults] = useState<GifAsset[]>([]);
  const [gifError, setGifError] = useState<string | null>(null);
  const [isGifSubmitting, setIsGifSubmitting] = useState(false);
  const availableCoins = authState?.user?.coins ?? 0;
  const trialCoins = authState?.user?.trialCoins ?? 0;

  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  const actionButtonBaseClass =
    "px-2 sm:px-4 py-2 bg-[#ffffff] text-[#3C4BFF] font-medium rounded-2xl shadow-sm hover:shadow-md transition-all flex items-center justify-center border border-[#E6E8FF] hover:bg-[#F5F6FF] active:shadow-sm focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40";

  const truncateHeading = (value: string) =>
    value.length > 35 ? `${value.slice(0, 34)}...` : value;

  // Preview state
  const [previewImage, setPreviewImage] = useState<string | null>(null);

  const handleImageClick = (imageUrl: string) => {
    if (galleryType === "illustrations" || galleryType === "mockups" || galleryType === "labels") {
      setPreviewImage(imageUrl);
    }
  };

  const closePreview = () => {
    setPreviewImage(null);
  };

  const gifSelectedCount = selectedGifIcons.size;
  const gifEstimatedCost = gifSelectedCount * 2;
  const insufficientGifBalance =
    gifEstimatedCost > 0 && availableCoins < gifEstimatedCost && trialCoins <= 0;

  const cleanupGifEventSource = () => {
    if (gifEventSourceRef.current) {
      gifEventSourceRef.current.close();
      gifEventSourceRef.current = null;
    }
  };

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
      const keyFor = (asset: GifAsset) => asset.iconId || asset.filePath || asset.fileName;
      prev.forEach((asset) => {
        const key = keyFor(asset);
        if (key) {
          merged.set(key, asset);
        }
      });
      assets.forEach((asset) => {
        const key = keyFor(asset);
        if (key) {
          merged.set(key, asset);
        }
      });
      return Array.from(merged.values());
    });
  };

  const handleGifUpdate = (update: GifProgressUpdate) => {
    const total = update.totalIcons || gifModalState?.icons.length || gifProgress.total;
    const completed =
      typeof update.completedIcons === "number" ? update.completedIcons : gifProgress.completed;
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

  const appendError = (message: string) => {
    setGifError(message);
    setIsGifSubmitting(false);
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
      appendError("GIF generation connection lost. Please try again.");
      cleanupGifEventSource();
    };
  };

  const openGifModal = (iconType: "original" | "variation") => {
    if (!selectedRequest) {
      setGifError("Please select a request first.");
      return;
    }
    const group = groupedIcons[selectedRequest];
    if (!group) {
      setGifError("Icons not found for this request.");
      return;
    }
    const icons = iconType === "original" ? group.original : group.variation;
    if (!icons || icons.length === 0) {
      setGifError("No icons available for GIF generation.");
      return;
    }
    resetGifState();
    setGifModalState({
      requestId: selectedRequest,
      iconType,
      icons,
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
      appendError("Please select icons to animate.");
      return;
    }
    if (selectedGifIcons.size === 0) {
      appendError("Select at least one icon to animate.");
      return;
    }

    setIsGifSubmitting(true);
    setGifError(null);
    setGifProgress((prev) => ({
      ...prev,
      status: "starting",
      message: "Submitting GIF generation request...",
      total: selectedGifIcons.size,
      percent: Math.max(prev.percent, 5),
    }));

    try {
      const response = await fetch("/api/icons/gif/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          requestId: gifModalState.requestId,
          iconIds: Array.from(selectedGifIcons),
        }),
      });
      const payload = await response.json().catch(() => ({}));

      if (!response.ok || payload.status === "error" || !payload.gifRequestId) {
        const message = payload.message || "Failed to start GIF generation.";
        appendError(message);
        return;
      }

      setGifProgress((prev) => ({
        ...prev,
        status: "in_progress",
        message: "Waiting for animation service...",
        total: payload.totalIcons || prev.total,
        percent: Math.max(prev.percent, 10),
      }));

      attachGifEventSource(payload.gifRequestId);
    } catch (error) {
      console.error("Failed to start GIF generation", error);
      appendError("Failed to start GIF generation. Please try again.");
    }
  };

  useEffect(() => {
    return () => {
      cleanupGifEventSource();
    };
  }, []);

  useEffect(() => {
    const interval = setInterval(() => {
      setGifRefreshToken((token) => (token + 1) % 100000);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  const composeGridImageUrl = async (iconType: string): Promise<string | null> => {
    if (!selectedRequest) {
      return null;
    }

    const selectedIconGroup = groupedIcons[selectedRequest];
    if (!selectedIconGroup) {
      alert("Could not find icons for the selected request.");
      return null;
    }

    const icons = iconType === "original" ? selectedIconGroup.original : selectedIconGroup.variation;
    if (!icons || icons.length === 0) {
      alert("No icons available to create a grid. Please try another request.");
      return null;
    }

    const availableUrls = icons
      .map((icon) => icon.imageUrl)
      .filter((url): url is string => Boolean(url));

    if (availableUrls.length === 0) {
      alert("Icon images are missing for this request.");
      return null;
    }

    const gridIconUrls = availableUrls.slice(0, TOTAL_GRID_CELLS);
    while (gridIconUrls.length < TOTAL_GRID_CELLS) {
      gridIconUrls.push(availableUrls[gridIconUrls.length % availableUrls.length]);
    }

    try {
      const gridBlob = await composeGridFromIconUrls(gridIconUrls);
      return URL.createObjectURL(gridBlob);
    } catch (error) {
      console.error("Error creating grid composition:", error);
      alert("Failed to create grid composition. Please try again.");
      return null;
    }
  };

  const handleGridNavigation = async (iconType: string, targetMode: GridGenerationMode) => {
    const gridImageUrl = await composeGridImageUrl(iconType);
    if (!gridImageUrl) {
      return;
    }

    sessionStorage.setItem("generatedGridImage", gridImageUrl);
    sessionStorage.setItem("generateMoreMode", "true");
    sessionStorage.setItem("generationMode", targetMode);

    router.push("/dashboard");
  };

  const fetchIcons = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/api/user/icons", {
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error("Failed to fetch icons");
      }
      const data: Icon[] = await response.json();

      const grouped = data.reduce((acc, icon) => {
        if (!acc[icon.requestId]) {
          acc[icon.requestId] = { original: [], variation: [], gifs: [] };
        }
        const isGif = icon.imageUrl?.toLowerCase().endsWith(".gif");
        if (isGif) {
          acc[icon.requestId].gifs.push(icon);
        } else if (icon.iconType === "original") {
          acc[icon.requestId].original.push(icon);
        } else if (icon.iconType === "variation") {
          acc[icon.requestId].variation.push(icon);
        }
        return acc;
      }, {} as GroupedIcons);

      setGroupedIcons(grouped);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {

    const fetchIllustrations = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch("/api/gallery/illustrations", {
          credentials: "include",
        });
        if (!response.ok) {
          throw new Error("Failed to fetch illustrations");
        }
        const rawData: Array<Illustration & { isWatermarked?: boolean }> = await response.json();
        const data: Illustration[] = rawData.map((illustration) => ({
          ...illustration,
          watermarked: illustration.watermarked ?? illustration.isWatermarked ?? false,
        }));

        const grouped = data.reduce((acc, illustration) => {
          if (!acc[illustration.requestId]) {
            acc[illustration.requestId] = { original: [], variation: [] };
          }
          if (illustration.illustrationType === "original") {
            acc[illustration.requestId].original.push(illustration);
          } else if (illustration.illustrationType === "variation") {
            acc[illustration.requestId].variation.push(illustration);
          }
          return acc;
        }, {} as GroupedIllustrations);

        setGroupedIllustrations(grouped);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    const fetchMockups = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch("/api/gallery/mockups", {
          credentials: "include",
        });
        if (!response.ok) {
          throw new Error("Failed to fetch mockups");
        }
        const data: Mockup[] = await response.json();

        const grouped = data.reduce((acc, mockup) => {
          if (!acc[mockup.requestId]) {
            acc[mockup.requestId] = { original: [], variation: [] };
          }
          if (mockup.mockupType === "original") {
            acc[mockup.requestId].original.push(mockup);
          } else if (mockup.mockupType === "variation") {
            acc[mockup.requestId].variation.push(mockup);
          }
          return acc;
        }, {} as GroupedMockups);

        setGroupedMockups(grouped);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    const fetchLabels = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch("/api/gallery/labels", {
          credentials: "include",
        });
        if (!response.ok) {
          throw new Error("Failed to fetch labels");
        }
        const data: LabelItem[] = await response.json();

        const grouped = data.reduce((acc, label) => {
          const normalizedLabel: LabelItem = {
            ...label,
            imageUrl: label.imageUrl || label.filePath || "",
          };

          if (!acc[label.requestId]) {
            acc[label.requestId] = { original: [], variation: [] };
          }
          if (normalizedLabel.labelType === "original") {
            acc[label.requestId].original.push(normalizedLabel);
          } else if (normalizedLabel.labelType === "variation") {
            acc[label.requestId].variation.push(normalizedLabel);
          }
          return acc;
        }, {} as GroupedLabels);

        setGroupedLabels(grouped);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    if (galleryType === "icons") {
      fetchIcons();
    } else if (galleryType === "illustrations") {
      fetchIllustrations();
    } else if (galleryType === "mockups") {
      fetchMockups();
    } else if (galleryType === "labels") {
      fetchLabels();
    } else {
      setLoading(false);
    }
  }, [galleryType]);

  const handleSelectRequest = (requestId: string) => {
    setSelectedRequest(requestId);
  };

  const handleBackToGallery = () => {
    setSelectedRequest(null);
  };

  const openDeleteModal = (
    requestId: string,
    targetType?: string | null,
    generationType?: string | null,
  ) => {
    setDeleteRequestId(requestId);
    setDeleteTargetType(targetType ?? galleryType);
    setDeleteGenerationType(generationType ?? null);
    setDeleteError(null);
    setShowDeleteModal(true);
  };

  const closeDeleteModal = () => {
    setShowDeleteModal(false);
    setDeleteRequestId(null);
    setDeleteTargetType(null);
    setDeleteGenerationType(null);
    setDeleteError(null);
  };

  const handleDeleteRequest = async () => {
    if (!deleteRequestId || !deleteTargetType) {
      return;
    }
    setIsDeletingRequest(true);
    setDeleteError(null);
    try {
      let shouldClearSelected = false;
      const deleteEndpoint = deleteGenerationType
        ? deleteTargetType === "icons"
          ? `/api/gallery/request/${deleteRequestId}/${deleteGenerationType}`
          : `/api/gallery/${deleteTargetType}/request/${deleteRequestId}/${deleteGenerationType}`
        : deleteTargetType === "icons"
          ? `/api/gallery/request/${deleteRequestId}`
          : `/api/gallery/${deleteTargetType}/request/${deleteRequestId}`;
      const response = await fetch(deleteEndpoint, {
        method: "DELETE",
        credentials: "include",
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        throw new Error(payload.message || "Failed to delete request");
      }
      if (deleteTargetType === "icons") {
        setGroupedIcons((prev) => {
          const next = { ...prev };
          const group = next[deleteRequestId];
          if (!group) {
            return prev;
          }
          if (deleteGenerationType === "original" || deleteGenerationType === "variation") {
            const updated = {
              ...group,
              [deleteGenerationType]: [],
            };
            if (
              updated.original.length === 0 &&
              updated.variation.length === 0 &&
              updated.gifs.length === 0
            ) {
              delete next[deleteRequestId];
              shouldClearSelected = true;
            } else {
              next[deleteRequestId] = updated;
            }
          } else {
            delete next[deleteRequestId];
            shouldClearSelected = true;
          }
          return next;
        });
      } else if (deleteTargetType === "illustrations") {
        setGroupedIllustrations((prev) => {
          const next = { ...prev };
          const group = next[deleteRequestId];
          if (!group) {
            return prev;
          }
          if (deleteGenerationType === "original" || deleteGenerationType === "variation") {
            const updated = {
              ...group,
              [deleteGenerationType]: [],
            };
            if (updated.original.length === 0 && updated.variation.length === 0) {
              delete next[deleteRequestId];
              shouldClearSelected = true;
            } else {
              next[deleteRequestId] = updated;
            }
          } else {
            delete next[deleteRequestId];
            shouldClearSelected = true;
          }
          return next;
        });
      } else if (deleteTargetType === "mockups") {
        setGroupedMockups((prev) => {
          const next = { ...prev };
          const group = next[deleteRequestId];
          if (!group) {
            return prev;
          }
          if (deleteGenerationType === "original" || deleteGenerationType === "variation") {
            const updated = {
              ...group,
              [deleteGenerationType]: [],
            };
            if (updated.original.length === 0 && updated.variation.length === 0) {
              delete next[deleteRequestId];
              shouldClearSelected = true;
            } else {
              next[deleteRequestId] = updated;
            }
          } else {
            delete next[deleteRequestId];
            shouldClearSelected = true;
          }
          return next;
        });
      } else if (deleteTargetType === "labels") {
        setGroupedLabels((prev) => {
          const next = { ...prev };
          const group = next[deleteRequestId];
          if (!group) {
            return prev;
          }
          if (deleteGenerationType === "original" || deleteGenerationType === "variation") {
            const updated = {
              ...group,
              [deleteGenerationType]: [],
            };
            if (updated.original.length === 0 && updated.variation.length === 0) {
              delete next[deleteRequestId];
              shouldClearSelected = true;
            } else {
              next[deleteRequestId] = updated;
            }
          } else {
            delete next[deleteRequestId];
            shouldClearSelected = true;
          }
          return next;
        });
      }
      if (selectedRequest === deleteRequestId && shouldClearSelected) {
        setSelectedRequest(null);
      }
      closeDeleteModal();
    } catch (error) {
      console.error("Failed to delete request", error);
      setDeleteError(error instanceof Error ? error.message : "Failed to delete request");
    } finally {
      setIsDeletingRequest(false);
    }
  };

  const handleRemoveWatermark = async () => {
    if (!selectedRequest) {
      return;
    }
    if (availableCoins < 1) {
      alert("You need regular coins to remove the watermark.");
      return;
    }

    setIsRemovingWatermark(true);
    try {
      const response = await fetch("/api/user/icons/remove-watermark", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ requestId: selectedRequest }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload.message || "Failed to remove watermark");
      }

      await fetchIcons();
      await checkAuthenticationStatus();
    } catch (error) {
      console.error("Failed to remove watermark", error);
      alert(error instanceof Error ? error.message : "Failed to remove watermark");
    } finally {
      setIsRemovingWatermark(false);
    }
  };

  const openExportModal = (icons: Icon[]) => {
    setIconsToExport(icons);
    setIllustrationsToExport([]);
    setMockupsToExport([]);
    setLabelsToExport([]);
    setShowExportModal(true);
  };

  const openIllustrationExportModal = (illustrations: Illustration[]) => {
    setIllustrationsToExport(illustrations);
    setIconsToExport([]);
    setMockupsToExport([]);
    setLabelsToExport([]);
    setShowExportModal(true);
  };

  const openMockupExportModal = (mockups: Mockup[]) => {
    setMockupsToExport(mockups);
    setIconsToExport([]);
    setIllustrationsToExport([]);
    setLabelsToExport([]);
    setShowExportModal(true);
  };

  const openLabelExportModal = (labels: LabelItem[]) => {
    setLabelsToExport(labels);
    setIconsToExport([]);
    setIllustrationsToExport([]);
    setMockupsToExport([]);
    setShowExportModal(true);
  };

  const handleGenerateMore = async (iconType: string) => {
    if (!selectedRequest) return;
    await handleGridNavigation(iconType, "icons");
  };

  const handleGenerateMockupFromIcons = async (iconType: string) => {
    if (!selectedRequest) return;
    await handleGridNavigation(iconType, "mockups");
  };

  const handleGenerateLabelsFromIcons = async (iconType: string) => {
    if (!selectedRequest) return;
    await handleGridNavigation(iconType, "labels");
  };

  const handleGenerateMoreIllustrations = async (illustrationType: string) => {
    if (!selectedRequest) return;

    const illustrationGroup = groupedIllustrations[selectedRequest];
    if (!illustrationGroup) {
      alert("Could not find illustration group.");
      return;
    }

    const illustrations =
      illustrationType === "original"
        ? illustrationGroup.original
        : illustrationGroup.variation;

    if (!illustrations || illustrations.length === 0) {
      alert("No illustrations found to generate more from.");
      return;
    }

    const firstIllustration = illustrations[0];

    try {
      const response = await fetch(firstIllustration.imageUrl);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const imageBlob = await response.blob();
      const imageUrl = URL.createObjectURL(imageBlob);

      sessionStorage.setItem("generatedGridImage", imageUrl);
      sessionStorage.setItem("generateMoreMode", "true");
      sessionStorage.setItem("generationMode", "illustrations");

      router.push("/dashboard");
    } catch (error) {
      console.error("Error using illustration as reference:", error);
      alert("Failed to use illustration as reference. Please try again.");
    }
  };

  const handleGenerateIconsFromMockup = async (mockup: Mockup) => {
    if (!mockup) return;

    try {
        const response = await fetch(mockup.imageUrl);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const imageBlob = await response.blob();
        const imageUrl = URL.createObjectURL(imageBlob);

        sessionStorage.setItem("generatedGridImage", imageUrl);
        sessionStorage.setItem("generateMoreMode", "true");
        sessionStorage.setItem("generationMode", "icons");

        router.push("/dashboard");
    } catch (error) {
        console.error("Error using mockup as reference:", error);
        alert("Failed to use mockup as reference. Please try again.");
    }
  };

  const confirmGalleryExport = (
    formats: string[],
    sizes?: number[],
    vectorizeSvg?: boolean,
    hqUpscale?: boolean,
  ) => {
    if (iconsToExport.length > 0) {
      const iconFilePaths = iconsToExport.map((icon) => icon.imageUrl);
      const fileName = `icon-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        iconFilePaths,
        formats,
        vectorizeSvg: vectorizeSvg ?? false,
        hqUpscale: hqUpscale ?? false,
      };
      setShowExportModal(false);
      downloadZip(exportData, fileName, "/api/export-gallery");
    } else if (illustrationsToExport.length > 0) {
      const illustrationFilePaths = illustrationsToExport.map((illustration) => illustration.imageUrl);
      const fileName = `illustration-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        illustrationFilePaths,
        formats,
        sizes,
      };
      setShowExportModal(false);
      downloadZip(exportData, fileName, "/api/illustrations/export-gallery");
    } else if (mockupsToExport.length > 0) {
      const mockupFilePaths = mockupsToExport.map((mockup) => mockup.imageUrl);
      const fileName = `mockup-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        mockupFilePaths,
        formats,
        sizes,
      };
      setShowExportModal(false);
      downloadZip(exportData, fileName, "/api/mockups/export-gallery");
    } else if (labelsToExport.length > 0) {
      const labelFilePaths = labelsToExport.map((label) => label.imageUrl);
      const fileName = `label-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        labelFilePaths,
        formats,
        vectorizeSvg: vectorizeSvg ?? false,
      };
      setShowExportModal(false);
      downloadZip(exportData, fileName, "/api/labels/export-gallery");
    }
  };

  const downloadZip = async (exportData: any, fileName: string, endpoint: string) => {
    setShowProgressModal(true);
    const itemType = endpoint.includes("illustration")
      ? "illustrations"
      : endpoint.includes("mockup")
      ? "mockups"
      : endpoint.includes("label")
      ? "labels"
      : "icons";
    setExportProgress({
      step: 1,
      message: "Preparing export request...",
      percent: 25,
    });
    try {
      setTimeout(() => {
        setExportProgress({
          step: 2,
          message: `Converting ${itemType} to multiple formats${itemType === "icons" || itemType === "illustrations" || itemType === "mockups" ? " and sizes" : ""}...`,
          percent: 50,
        });
      }, 500);

      const response = await fetch(endpoint, {
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
      if (!response.ok) {
        let errorMessage = `Failed to export ${itemType}. Please try again.`;
        try {
          const errorBody = await response.text();
          if (errorBody) {
            errorMessage = errorBody;
          }
        } catch (readError) {
          console.error("Failed to read error body:", readError);
        }

        setShowProgressModal(false);
        if (response.status === 402) {
          alert(`${errorMessage}`);
        } else {
          alert(errorMessage);
        }
        return;
      }

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
      }, 1000);
    } catch (error) {
      console.error(`Error exporting ${itemType}:`, error);
      setShowProgressModal(false);
      alert(`Failed to export ${itemType}. Please try again.`);
    }
  };

  const handleExportGifs = async (gifIcons: Icon[]) => {
    if (!gifIcons || gifIcons.length === 0) {
      return;
    }
    try {
      const response = await fetch("/api/gallery/export-gifs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          gifFilePaths: gifIcons.map((icon) => icon.imageUrl),
        }),
      });

      if (!response.ok) {
        throw new Error(`Failed to export GIFs (${response.status})`);
      }

      const blob = await response.blob();
      const downloadUrl = URL.createObjectURL(blob);
      const contentDisposition = response.headers.get("content-disposition");
      let filename = gifIcons.length === 1
        ? `gallery-gif-${Date.now()}.gif`
        : `gallery-gifs-${Date.now()}.zip`;

      if (contentDisposition) {
        const match = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
        if (match?.[1]) {
          filename = match[1];
        }
      }

      const link = document.createElement("a");
      link.href = downloadUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(downloadUrl);
    } catch (err) {
      console.error("Failed to export GIFs:", err);
      setError("Failed to export GIFs. Please try again.");
    }
  };

  const selectedIconGroup = selectedRequest
    ? groupedIcons[selectedRequest]
    : null;
  const selectedIllustrationGroup = selectedRequest
    ? groupedIllustrations[selectedRequest]
    : null;
  const selectedLabelGroup = selectedRequest
    ? groupedLabels[selectedRequest]
    : null;
  const hasWatermarkedIcons = Boolean(
    selectedIconGroup &&
      (selectedIconGroup.original.some((icon) => icon.watermarked) ||
        selectedIconGroup.variation.some((icon) => icon.watermarked)),
  );
  const hasWatermarkedIllustrations = Boolean(
    selectedIllustrationGroup &&
      (selectedIllustrationGroup.original.some((illustration) => illustration.watermarked) ||
        selectedIllustrationGroup.variation.some((illustration) => illustration.watermarked)),
  );
  const canRemoveWatermark = hasWatermarkedIcons && availableCoins > 0;

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation useLoginPage={true} />
      <div className="container mx-auto px-4 py-8">
        {loading && <p>Loading...</p>}
        {!loading && (
          <>
            {!galleryType ? (
              <div>
                <h1 className="text-3xl font-bold mb-8 text-slate-800 text-center">
                  Gallery
                </h1>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-3xl mx-auto">
                  <div
                    onClick={() => setGalleryType("icons")}
                    className="group cursor-pointer rounded-xl border border-slate-200 bg-white p-6 transition-all duration-300 hover:border-purple-300 hover:shadow-lg hover:shadow-purple-100"
                  >
                    <h2 className="text-2xl font-bold text-slate-800 text-center">
                      Icons
                    </h2>
                    <p className="text-slate-500 mt-2 text-center">
                      Browse your generated icon packs.
                    </p>
                  </div>
                  <div
                    onClick={() => setGalleryType("illustrations")}
                    className="group cursor-pointer rounded-xl border border-slate-200 bg-white p-6 transition-all duration-300 hover:border-blue-300 hover:shadow-lg hover:shadow-blue-100"
                  >
                    <h2 className="text-2xl font-bold text-slate-800 text-center">
                      Illustrations
                    </h2>
                    <p className="text-slate-500 mt-2 text-center">
                      Browse your generated illustrations.
                    </p>
                  </div>
                <div
                  onClick={() => setGalleryType("mockups")}
                  className="group cursor-pointer rounded-xl border border-slate-200 bg-white p-6 transition-all duration-300 hover:border-pink-300 hover:shadow-lg hover:shadow-pink-100"
                >
                  <h2 className="text-2xl font-bold text-slate-800 text-center">
                    UI Mockups
                  </h2>
                  <p className="text-slate-500 mt-2 text-center">
                    Browse your generated UI mockups.
                  </p>
                </div>
                  <div
                    onClick={() => setGalleryType("labels")}
                    className="group cursor-pointer rounded-xl border border-slate-200 bg-white p-6 transition-all duration-300 hover:border-emerald-300 hover:shadow-lg hover:shadow-emerald-100"
                  >
                    <h2 className="text-2xl font-bold text-slate-800 text-center">
                      Labels
                    </h2>
                    <p className="text-slate-500 mt-2 text-center">
                      Browse your generated labels.
                    </p>
                  </div>
              </div>
              </div>
            ) : (
              <>
                {selectedRequest ? (
                  <button
                    onClick={handleBackToGallery}
                    className="mb-8 px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M19 12H5m7 7l-7-7 7-7" />
                    </svg>
                    Back to Gallery
                  </button>
                ) : (
                  <button
                    onClick={() => {
                      setGalleryType(null);
                      setSelectedRequest(null);
                      setGroupedIcons({});
                      setGroupedIllustrations({});
                      setGroupedMockups({});
                      setGroupedLabels({});
                      setError(null);
                    }}
                    className="mb-8 px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M19 12H5m7 7l-7-7 7-7" />
                    </svg>
                    Back to Gallery Selection
                  </button>
                )}

                {error && <p className="text-red-500">{error}</p>}

                {!error && galleryType === "icons" && (
                  <>
                    {selectedIconGroup && selectedRequest ? (
                      <div>

                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
                          <h1 className="text-3xl font-bold text-slate-800 mb-4 sm:mb-0">
                            {truncateHeading(
                              selectedIconGroup.original[0]?.theme ||
                                selectedIconGroup.variation[0]?.theme ||
                                `Request: ${selectedRequest}`
                            )}
                          </h1>
                          <button
                            onClick={() =>
                              openExportModal([
                                ...selectedIconGroup.original,
                                ...selectedIconGroup.variation,
                              ])
                            }
                            className="px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"
                          >
                            <Download className="w-4 h-4" />
                            <span className="hidden sm:inline">
                              Export All (
                              {selectedIconGroup.original.length +
                                selectedIconGroup.variation.length}{" "}
                              icons)
                            </span>
                          </button>
                          {hasWatermarkedIcons && (
                            <button
                              onClick={handleRemoveWatermark}
                              disabled={!canRemoveWatermark || isRemovingWatermark}
                              className={`px-3 sm:px-5 py-2.5 font-medium rounded-2xl shadow-sm transition-all flex items-center justify-center gap-2 border ${
                                canRemoveWatermark
                                  ? "bg-[#3C4BFF] text-white border-[#3C4BFF] hover:bg-[#2F3BD4] hover:shadow-md"
                                  : "bg-slate-200 text-slate-500 border-slate-200 cursor-not-allowed"
                              }`}
                              title={
                                canRemoveWatermark
                                  ? "Remove watermark (1 coin)"
                                  : "You need regular coins to remove the watermark"
                              }
                            >
                              {isRemovingWatermark ? (
                                "Removing..."
                              ) : (
                                <>
                                  <span>Remove Watermark</span>
                                  <span className="flex items-center gap-1 text-xs font-semibold">
                                    <img
                                      src="/images/coin.webp"
                                      alt="Coin"
                                      className="h-4 w-4"
                                    />
                                    <span>1</span>
                                  </span>
                                </>
                              )}
                            </button>
                          )}
                        </div>

                        {hasWatermarkedIcons && (
                          <div className="mb-6 flex items-start gap-2 rounded-xl border border-amber-200/80 bg-amber-50/80 px-3 py-3 text-xs text-amber-900">
                            <svg
                              className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-500"
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
                              <p className="text-xs font-semibold text-amber-900">
                                Trial assets notice
                              </p>
                              <p className="text-[11px] text-amber-800">
                                Trial assets include a watermark and are stored for 30 days.
                                Remove the watermark to keep them permanently.
                              </p>
                            </div>
                          </div>
                        )}

                        {selectedIconGroup.original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Icons
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateMore("original")}
                                  className={`${actionButtonBaseClass} gap-2`}
                                  title="Generate more like this"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() => handleGenerateMockupFromIcons("original")}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate UI Mockup from these icons"
                                >
                                  <span className="text-xs font-bold">UI</span>
                                </button>
                                <button
                                  onClick={() => handleGenerateLabelsFromIcons("original")}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate Labels from these icons"
                                >
                                  <span className="text-xs font-bold">T</span>
                                </button>
                                <button
                                  onClick={() => openGifModal("original")}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate GIFs from these icons"
                                >
                                  <span className="text-xs font-bold">GIF</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "icons", "original")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openExportModal(
                                      selectedIconGroup.original
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                  title="Export"
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                              {selectedIconGroup.original.map(
                                (icon, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm"
                                  >
                                    <img
                                      src={icon.imageUrl}
                                      alt={icon.description || "Generated Icon"}
                                      className="w-full h-auto object-cover rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}

                        {selectedIconGroup.variation.length > 0 && (
                          <div className="p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Variations
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() =>
                                    handleGenerateMore("variation")
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                  title="Generate more like this"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() => handleGenerateMockupFromIcons("variation")}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate UI Mockup from these icons"
                                >
                                  <span className="text-xs font-bold">UI</span>
                                </button>
                              <button
                                onClick={() => handleGenerateLabelsFromIcons("variation")}
                                className={`${actionButtonBaseClass} gap-1`}
                                title="Generate Labels from these icons"
                              >
                                <span className="text-xs font-bold">T</span>
                              </button>
                              <button
                                onClick={() => openGifModal("variation")}
                                className={`${actionButtonBaseClass} gap-1`}
                                title="Generate GIFs from these icons"
                              >
                                <span className="text-xs font-bold">GIF</span>
                              </button>
                              <button
                                onClick={() =>
                                  openDeleteModal(selectedRequest, "icons", "variation")
                                }
                                className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                title="Delete this generation"
                                aria-label="Delete this generation"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                              <button
                                onClick={() =>
                                  openExportModal(
                                    selectedIconGroup.variation
                                  )
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                              {selectedIconGroup.variation.map(
                                (icon, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm"
                                  >
                                    <img
                                      src={icon.imageUrl}
                                      alt={
                                        icon.description || "Generated Icon"
                                      }
                                      className="w-full h-auto object-cover rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}

                        {selectedIconGroup.gifs.length > 0 && (
                          <div className="p-4 rounded-lg border border-amber-200 bg-white/60 shadow-lg shadow-amber-100/60">
                            <div className="flex items-center justify-between mb-4">
                              <div>
                                <h3 className="text-xl font-semibold text-amber-700">
                                  Animated GIFs
                                </h3>
                                <span className="text-sm font-semibold text-amber-700">
                                  {selectedIconGroup.gifs.length} file
                                  {selectedIconGroup.gifs.length === 1 ? "" : "s"}
                                </span>
                              </div>
                              <button
                                onClick={() => handleExportGifs(selectedIconGroup.gifs)}
                                className={`${actionButtonBaseClass} gap-2`}
                                title="Export GIFs"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
                              {selectedIconGroup.gifs.map((icon, index) => (
                                <div
                                  key={`gif-${icon.id}-${index}`}
                                  className="border rounded-lg p-2 bg-white shadow-sm space-y-2 max-w-[231px] mx-auto"
                                >
                                  <img
                                    src={`${icon.imageUrl}?loop=${gifRefreshToken}`}
                                    alt={icon.description || "Animated GIF"}
                                    className="w-full h-auto rounded-md max-h-[231px] object-contain"
                                  />
                                  <p className="text-xs text-slate-500 truncate">
                                    {icon.description || "Looping animation"}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div>
                        <h1 className="text-3xl font-bold mb-8 text-slate-800">
                          Icon Pack Gallery
                        </h1>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                          {Object.entries(groupedIcons).map(
                            ([requestId, iconTypes]) => {
                              const getRequestPreview = () => {
                                if (iconTypes.original.length > 0)
                                  return iconTypes.original[0].imageUrl;
                                if (iconTypes.variation.length > 0)
                                  return iconTypes.variation[0].imageUrl;
                                return "";
                              };
                              const theme =
                                iconTypes.original[0]?.theme ||
                                iconTypes.variation[0]?.theme;

                              return (
                                <div
                                  key={requestId}
                                  onClick={() => handleSelectRequest(requestId)}
                                  className="group cursor-pointer rounded-lg border border-purple-200 bg-white/50 shadow-lg shadow-slate-200/50 p-3 transition-all duration-300 hover:border-purple-400 hover:shadow-purple-200/50 flex items-center"
                                >
                                  <div className="w-1/3 aspect-square overflow-hidden rounded-md bg-slate-100 flex-shrink-0">
                                    <img
                                      src={getRequestPreview()}
                                      alt="Request Preview"
                                      className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                                    />
                                  </div>
                                  <div className="w-2/3 pl-4">
                                    <div>
                                      <h2 className="text-base font-bold text-slate-800 truncate">
                                        {theme || `Request: ${requestId}`}
                                      </h2>
                                      <p className="text-sm text-slate-500 mt-1">
                                        {iconTypes.original.length +
                                          iconTypes.variation.length}{" "}
                                        icons
                                      </p>
                                    </div>
                                  </div>
                                </div>
                              );
                            }
                          )}
                        </div>
                      </div>
                    )}
                  </>
                )}

                {!error && galleryType === "labels" && (
                  <>
                    {selectedLabelGroup && selectedRequest ? (
                      <div>
                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
                          <h1 className="text-3xl font-bold text-slate-800 mb-4 sm:mb-0">
                            {truncateHeading(
                              selectedLabelGroup.original[0]?.theme ||
                                selectedLabelGroup.variation[0]?.theme ||
                                `Request: ${selectedRequest}`
                            )}
                          </h1>
                          <button
                            onClick={() =>
                              openLabelExportModal([
                                ...selectedLabelGroup.original,
                                ...selectedLabelGroup.variation,
                              ])
                            }

                            className="px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"
                          >
                            <Download className="w-4 h-4" />
                            <span className="hidden sm:inline">
                              Export All (
                              {selectedLabelGroup.original.length +
                                selectedLabelGroup.variation.length}{" "}
                              labels)
                            </span>
                          </button>
                        </div>

                        {selectedLabelGroup.original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Labels
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "labels", "original")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openLabelExportModal(
                                      selectedLabelGroup.original,
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-1`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                              {selectedLabelGroup.original.map((label, index) => (
                                <div
                                  key={index}
                                  className="border rounded-lg p-4 bg-white shadow-sm flex flex-col items-center gap-3"
                                >
                                    <img
                                      src={label.imageUrl}
                                    alt={label.labelText || "Generated Label"}
                                    className="w-full h-auto object-contain rounded-md bg-slate-50 border border-slate-200"
                                  />
                                  <p className="text-sm font-semibold text-slate-700 text-center">
                                    {label.labelText}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {selectedLabelGroup.variation.length > 0 && (
                          <div className="p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Variations
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "labels", "variation")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openLabelExportModal(
                                      selectedLabelGroup.variation,
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-1`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                              {selectedLabelGroup.variation.map((label, index) => (
                                <div
                                  key={index}
                                  className="border rounded-lg p-4 bg-white shadow-sm flex flex-col items-center gap-3"
                                >
                                    <img
                                      src={label.imageUrl}
                                    alt={label.labelText || "Generated Label"}
                                    className="w-full h-auto object-contain rounded-md bg-slate-50 border border-slate-200"
                                  />
                                  <p className="text-sm font-semibold text-slate-700 text-center">
                                    {label.labelText}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div>
                        <h1 className="text-3xl font-bold mb-8 text-slate-800">
                          Label Gallery
                        </h1>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                          {Object.entries(groupedLabels).map(
                            ([requestId, labelTypes]) => {
                              const getRequestPreview = () => {
                                if (labelTypes.original.length > 0)
                                  return labelTypes.original[0].imageUrl;
                                if (labelTypes.variation.length > 0)
                                  return labelTypes.variation[0].imageUrl;
                                return "";
                              };
                              const theme =
                                labelTypes.original[0]?.theme ||
                                labelTypes.variation[0]?.theme;

                              return (
                                <div
                                  key={requestId}
                                  onClick={() => handleSelectRequest(requestId)}
                                  className="group cursor-pointer rounded-lg border border-emerald-200 bg-white/50 shadow-lg shadow-slate-200/50 p-3 transition-all duration-300 hover:border-emerald-400 hover:shadow-emerald-200/50 flex items-center"
                                >
                                  <div className="w-1/3 aspect-square overflow-hidden rounded-md bg-slate-100 flex-shrink-0">
                                    <img
                                      src={getRequestPreview()}
                                      alt="Request Preview"
                                      className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                                    />
                                  </div>
                                  <div className="w-2/3 pl-4">
                                    <h2 className="text-base font-bold text-slate-800 truncate">
                                      {theme || `Request: ${requestId}`}
                                    </h2>
                                    <p className="text-sm text-slate-500 mt-1">
                                      {labelTypes.original.length +
                                        labelTypes.variation.length}{" "}
                                      labels
                                    </p>
                                  </div>
                                </div>
                              );
                            }
                          )}
                        </div>
                      </div>
                    )}
                  </>
                )}

                {!error && galleryType === "illustrations" && (
                  <>
                    {selectedRequest && groupedIllustrations[selectedRequest] ? (
                      <div>

                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
                          <h1 className="text-3xl font-bold text-slate-800 mb-4 sm:mb-0">
                            {truncateHeading(
                              groupedIllustrations[selectedRequest].original[0]?.theme ||
                                groupedIllustrations[selectedRequest].variation[0]?.theme ||
                                `Request: ${selectedRequest}`
                            )}
                          </h1>
                          <button
                            onClick={() =>
                              openIllustrationExportModal([
                                ...groupedIllustrations[selectedRequest].original,
                                ...groupedIllustrations[selectedRequest].variation,
                              ])
                            }
                            className="px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"
                          >
                            <Download className="w-4 h-4" />
                            <span className="hidden sm:inline">
                              Export All (
                              {groupedIllustrations[selectedRequest].original.length +
                                groupedIllustrations[selectedRequest].variation.length}{" "}
                              illustrations)
                            </span>
                          </button>
                        </div>

                        {hasWatermarkedIllustrations && (
                          <div className="mb-6 flex items-start gap-2 rounded-xl border border-amber-200/80 bg-amber-50/80 px-3 py-3 text-xs text-amber-900">
                            <svg
                              className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-500"
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
                              <p className="text-xs font-semibold text-amber-900">
                                Trial assets notice
                              </p>
                              <p className="text-[11px] text-amber-800">
                                Trial illustrations include a watermark and are stored for 30 days.
                              </p>
                            </div>
                          </div>
                        )}

                        {groupedIllustrations[selectedRequest].original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Illustrations
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateMoreIllustrations("original")}
                                  className={`${actionButtonBaseClass} gap-2`}
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "illustrations", "original")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openIllustrationExportModal(
                                      groupedIllustrations[selectedRequest].original
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-6">
                              {groupedIllustrations[selectedRequest].original.map(
                                (illustration, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm aspect-[5/4] max-w-[450px] mx-auto cursor-pointer hover:shadow-lg transition-shadow duration-200"
                                    onClick={() => handleImageClick(illustration.imageUrl)}
                                  >
                                    <img
                                      src={illustration.imageUrl}
                                      alt={illustration.description || "Generated Illustration"}
                                      className="w-full h-full object-contain rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}

                        {groupedIllustrations[selectedRequest].variation.length > 0 && (
                          <div className="p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Variations
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() =>
                                    handleGenerateMoreIllustrations("variation")
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "illustrations", "variation")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openIllustrationExportModal(
                                      groupedIllustrations[selectedRequest].variation
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-2`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-6">
                              {groupedIllustrations[selectedRequest].variation.map(
                                (illustration, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm aspect-[5/4] max-w-[450px] mx-auto cursor-pointer hover:shadow-lg transition-shadow duration-200"
                                    onClick={() => handleImageClick(illustration.imageUrl)}
                                  >
                                    <img
                                      src={illustration.imageUrl}
                                      alt={illustration.description || "Generated Illustration"}
                                      className="w-full h-full object-contain rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div>
                        <h1 className="text-3xl font-bold mb-8 text-slate-800">
                          Illustration Gallery
                        </h1>
                        {Object.keys(groupedIllustrations).length > 0 ? (
                          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {Object.entries(groupedIllustrations).map(
                              ([requestId, illustrationTypes]) => {
                                const getRequestPreview = () => {
                                  if (illustrationTypes.original.length > 0)
                                    return illustrationTypes.original[0].imageUrl;
                                  if (illustrationTypes.variation.length > 0)
                                    return illustrationTypes.variation[0].imageUrl;
                                  return "";
                                };
                                const theme =
                                  illustrationTypes.original[0]?.theme ||
                                  illustrationTypes.variation[0]?.theme;

                                return (
                                  <div
                                    key={requestId}
                                    onClick={() => handleSelectRequest(requestId)}
                                    className="group cursor-pointer rounded-lg border border-purple-200 bg-white/50 shadow-lg shadow-slate-200/50 p-3 transition-all duration-300 hover:border-purple-400 hover:shadow-purple-200/50 flex items-center"
                                  >
                                    <div className="w-1/3 aspect-[5/4] overflow-hidden rounded-md bg-slate-100 flex-shrink-0">
                                      <img
                                        src={getRequestPreview()}
                                        alt="Request Preview"
                                        className="w-full h-full object-contain transition-transform duration-300 group-hover:scale-105"
                                      />
                                    </div>
                                    <div className="w-2/3 pl-4">
                                      <h2 className="text-base font-bold text-slate-800 truncate">
                                        {theme || `Request: ${requestId}`}
                                      </h2>
                                      <p className="text-sm text-slate-500 mt-1">
                                        {illustrationTypes.original.length +
                                          illustrationTypes.variation.length}{" "}
                                        illustrations
                                      </p>
                                    </div>
                                  </div>
                                );
                              }
                            )}
                          </div>
                        ) : (
                          <div className="text-center py-16 border-2 border-dashed border-slate-300 rounded-lg">
                            <p className="text-slate-500">
                              You don't have any illustrations yet.
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}

                {!error && galleryType === "mockups" && (
                  <>
                    {selectedRequest && groupedMockups[selectedRequest] ? (
                      <div>
                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
                          <h1 className="text-3xl font-bold text-slate-800 mb-4 sm:mb-0">
                            {truncateHeading(
                              groupedMockups[selectedRequest].original[0]?.theme ||
                                groupedMockups[selectedRequest].variation[0]?.theme ||
                                `Request: ${selectedRequest}`
                            )}
                          </h1>
                          <button
                            onClick={() =>
                              openMockupExportModal([
                                ...groupedMockups[selectedRequest].original,
                                ...groupedMockups[selectedRequest].variation,
                              ])
                            }
                            className="px-3 sm:px-5 py-2.5 bg-[#ffffff] text-[#3C4BFF] font-medium
             rounded-2xl shadow-sm hover:shadow-md transition-all
             flex items-center justify-center gap-2
             border border-[#E6E8FF]
             hover:bg-[#F5F6FF] active:shadow-sm
             focus:outline-none focus:ring-2 focus:ring-[#3C4BFF]/40"
                          >
                            <Download className="w-4 h-4" />
                            <span className="hidden sm:inline">
                              Export All (
                              {groupedMockups[selectedRequest].original.length +
                                groupedMockups[selectedRequest].variation.length}{" "}
                              mockups)
                            </span>
                          </button>
                        </div>

                        {groupedMockups[selectedRequest].original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Mockup
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateIconsFromMockup(groupedMockups[selectedRequest].original[0])}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate Icons from this Mockup"
                                >
                                  <span className="text-xs font-bold">Icon</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "mockups", "original")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openMockupExportModal(
                                      groupedMockups[selectedRequest].original
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-1`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="flex justify-center">
                              {groupedMockups[selectedRequest].original.map(
                                (mockup, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm aspect-video max-w-[800px] w-full cursor-pointer hover:shadow-lg transition-shadow duration-200"
                                    onClick={() => handleImageClick(mockup.imageUrl)}
                                  >
                                    <img
                                      src={mockup.imageUrl}
                                      alt={mockup.description || "Generated UI Mockup"}
                                      className="w-full h-full object-contain rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}

                        {groupedMockups[selectedRequest].variation.length > 0 && (
                          <div className="p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Variation
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateIconsFromMockup(groupedMockups[selectedRequest].variation[0])}
                                  className={`${actionButtonBaseClass} gap-1`}
                                  title="Generate Icons from this Mockup"
                                >
                                  <span className="text-xs font-bold">Icon</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openDeleteModal(selectedRequest, "mockups", "variation")
                                  }
                                  className="inline-flex items-center justify-center rounded-2xl border border-red-200 bg-red-50 px-2 sm:px-4 py-2 text-red-600 transition hover:bg-red-100 hover:text-red-700 focus:outline-none focus:ring-2 focus:ring-red-300"
                                  title="Delete this generation"
                                  aria-label="Delete this generation"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openMockupExportModal(
                                      groupedMockups[selectedRequest].variation
                                    )
                                  }
                                  className={`${actionButtonBaseClass} gap-1`}
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </div>
                            </div>
                            <div className="flex justify-center">
                              {groupedMockups[selectedRequest].variation.map(
                                (mockup, index) => (
                                  <div
                                    key={index}
                                    className="border rounded-lg p-2 bg-white shadow-sm aspect-video max-w-[800px] w-full cursor-pointer hover:shadow-lg transition-shadow duration-200"
                                    onClick={() => handleImageClick(mockup.imageUrl)}
                                  >
                                    <img
                                      src={mockup.imageUrl}
                                      alt={mockup.description || "Generated UI Mockup"}
                                      className="w-full h-full object-contain rounded-md"
                                    />
                                  </div>
                                )
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div>
                        <h1 className="text-3xl font-bold mb-8 text-slate-800">
                          UI Mockup Gallery
                        </h1>
                        {Object.keys(groupedMockups).length > 0 ? (
                          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {Object.entries(groupedMockups).map(
                              ([requestId, mockupTypes]) => {
                                const getRequestPreview = () => {
                                  if (mockupTypes.original.length > 0)
                                    return mockupTypes.original[0].imageUrl;
                                  if (mockupTypes.variation.length > 0)
                                    return mockupTypes.variation[0].imageUrl;
                                  return "";
                                };
                                const theme =
                                  mockupTypes.original[0]?.theme ||
                                  mockupTypes.variation[0]?.theme;

                                return (
                                  <div
                                    key={requestId}
                                    onClick={() => handleSelectRequest(requestId)}
                                    className="group cursor-pointer rounded-lg border border-pink-200 bg-white/50 shadow-lg shadow-slate-200/50 p-3 transition-all duration-300 hover:border-pink-400 hover:shadow-pink-200/50"
                                  >
                                    <div className="aspect-video overflow-hidden rounded-md bg-slate-100">
                                      <img
                                        src={getRequestPreview()}
                                        alt="Request Preview"
                                        className="w-full h-full object-contain transition-transform duration-300 group-hover:scale-105"
                                      />
                                    </div>
                                    <div className="mt-3">
                                      <h2 className="text-base font-bold text-slate-800 truncate">
                                        {theme || `Request: ${requestId}`}
                                      </h2>
                                      <p className="text-sm text-slate-500 mt-1">
                                        {mockupTypes.original.length +
                                          mockupTypes.variation.length}{" "}
                                        mockups
                                      </p>
                                    </div>
                                  </div>
                                );
                              }
                            )}
                          </div>
                        ) : (
                          <div className="text-center py-16 border-2 border-dashed border-slate-300 rounded-lg">
                            <p className="text-slate-500">
                              You don't have any UI mockups yet.
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}
              </>
            )}
          </>
        )}
      </div>
      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmGalleryExport}
        iconCount={
          iconsToExport.length > 0
            ? iconsToExport.length
            : illustrationsToExport.length > 0
            ? illustrationsToExport.length
            : mockupsToExport.length > 0
            ? mockupsToExport.length
            : labelsToExport.length
        }
        mode={
          iconsToExport.length > 0
            ? "icons"
            : illustrationsToExport.length > 0
            ? "illustrations"
            : mockupsToExport.length > 0
            ? "mockups"
            : "labels"
        }
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />

      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
          <div
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
            role="dialog"
            aria-modal="true"
          >
            <div className="flex items-start gap-3">
              <div className="rounded-full bg-red-100 p-2 text-red-600">
                <Trash2 className="h-5 w-5" />
              </div>
              <div>
                <h3 className="text-lg font-semibold text-slate-800">Confirm deletion</h3>
                <p className="mt-1 text-sm text-slate-600">
                  This action is irreversible. Are you sure you want to continue?
                </p>
              </div>
            </div>
            {deleteError && (
              <p className="mt-4 text-sm text-red-600">{deleteError}</p>
            )}
            <div className="mt-6 flex justify-end gap-3">
              <button
                onClick={closeDeleteModal}
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50"
                disabled={isDeletingRequest}
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteRequest}
                className="rounded-xl border border-red-600 bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-400"
                disabled={isDeletingRequest}
              >
                {isDeletingRequest ? "Deleting..." : "Delete"}
              </button>
            </div>
          </div>
        </div>
      )}

      {gifModalState && (
        <GifModal
          title={`Request ${gifModalState.requestId} · ${
            gifModalState.iconType === "original" ? "Original" : "Variations"
          }`}
          icons={gifModalState.icons.map((icon, index) => {
            const iconId = icon.iconId || `icon-${icon.id ?? index}`;
            const selectableId = icon.iconId;
            const isSelectable = Boolean(selectableId);
            return {
              id: iconId,
              imageSrc: icon.imageUrl,
              description: icon.description || "Icon",
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
              src={previewImage}
              alt="Full size preview"
              className="max-w-full max-h-[90vh] object-contain rounded-lg"
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        </div>
      )}
    </div>
  );
}
