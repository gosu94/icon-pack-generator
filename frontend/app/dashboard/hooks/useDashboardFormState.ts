import {
  ChangeEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { GenerationMode } from "@/lib/types";

interface AuthStateLike {
  user?: {
    coins?: number;
    trialCoins?: number;
  };
}

interface UseDashboardFormStateParams {
  mode: GenerationMode;
}

export type CropRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export function useDashboardFormState({
  mode,
}: UseDashboardFormStateParams) {
  const [inputType, setInputType] = useState("text");
  const [generateVariations, setGenerateVariations] = useState(false);
  const [generalDescription, setGeneralDescription] = useState("");
  const [labelText, setLabelText] = useState("");
  const [individualDescriptions, setIndividualDescriptions] = useState<
    string[]
  >([]);
  const [referenceImage, setReferenceImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string>("");
  const [uiReferenceImage, setUiReferenceImage] = useState<File | null>(null);
  const [uiImagePreview, setUiImagePreview] = useState<string>("");
  const [uiCrop, setUiCrop] = useState<CropRect | null>(null);
  const [enhancePrompt, setEnhancePrompt] = useState(false);
  const [baseModel, setBaseModel] = useState("standard");
  const [variationModel, setVariationModel] = useState("pro");

  const fileInputRef = useRef<HTMLInputElement>(null);
  const uiFileInputRef = useRef<HTMLInputElement>(null);

  const resetFormForMode = useCallback(
    (nextMode: GenerationMode) => {
      const count =
        nextMode === "illustrations"
          ? 4
          : nextMode === "mockups"
          ? 1
          : nextMode === "ui-elements"
          ? 1
          : nextMode === "labels"
          ? 1
          : 9;
      setIndividualDescriptions(new Array(count).fill(""));
      if (nextMode !== "labels") {
        setLabelText("");
      }
    },
    [setIndividualDescriptions, setLabelText],
  );

  useEffect(() => {
    resetFormForMode(mode);
    if (mode === "mockups") {
      setGenerateVariations(true);
    }
    if (mode === "ui-elements") {
      setGenerateVariations(false);
    }
    if (mode !== "icons" && enhancePrompt) {
      setEnhancePrompt(false);
    }
  }, [mode, resetFormForMode, enhancePrompt]);

  useEffect(() => {
    if (inputType === "image" && enhancePrompt) {
      setEnhancePrompt(false);
    }
  }, [inputType, enhancePrompt]);

  const handleImageSelect = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
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
    },
    [],
  );

  const handleUiImageSelect = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file) {
        setUiReferenceImage(null);
        setUiImagePreview("");
        setUiCrop(null);
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
      setUiReferenceImage(file);
      setUiCrop(null);
      const reader = new FileReader();
      reader.onload = (e) => {
        setUiImagePreview(e.target?.result as string);
      };
      reader.readAsDataURL(file);
    },
    [],
  );

  const handleUiImageFromGallery = useCallback(
    async (imageUrl: string, fileName?: string) => {
      try {
        const response = await fetch(imageUrl, { credentials: "include" });
        if (!response.ok) {
          throw new Error("Failed to fetch gallery image.");
        }
        const blob = await response.blob();
        if (!blob.type.startsWith("image/")) {
          alert("Selected file is not a valid image.");
          return;
        }
        if (blob.size > 10 * 1024 * 1024) {
          alert("File size must be less than 10MB.");
          return;
        }
        const fallbackName =
          fileName || imageUrl.split("/").pop() || "gallery-reference.png";
        const file = new File([blob], fallbackName, {
          type: blob.type || "image/png",
          lastModified: Date.now(),
        });

        setUiReferenceImage(file);
        setUiCrop(null);
        if (uiFileInputRef.current) {
          uiFileInputRef.current.value = "";
        }

        const reader = new FileReader();
        reader.onload = (e) => {
          setUiImagePreview(e.target?.result as string);
        };
        reader.readAsDataURL(file);
      } catch (error) {
        console.error("Failed to load gallery image", error);
        alert("Failed to load gallery image. Please try again.");
      }
    },
    [],
  );

  const removeImage = useCallback(() => {
    setReferenceImage(null);
    setImagePreview("");
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }, []);

  const removeUiImage = useCallback(() => {
    setUiReferenceImage(null);
    setUiImagePreview("");
    setUiCrop(null);
    if (uiFileInputRef.current) {
      uiFileInputRef.current.value = "";
    }
  }, []);

  const fileToBase64 = useCallback((file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        const base64 = (reader.result as string).split(",")[1];
        resolve(base64);
      };
      reader.onerror = (error) => reject(error);
    });
  }, []);

  const cropImageToBase64 = useCallback(
    (file: File, crop: CropRect | null, previewUrl?: string): Promise<string> => {
      if (!crop) {
        return fileToBase64(file);
      }
      return new Promise((resolve, reject) => {
        const image = new Image();
        const objectUrl = previewUrl || URL.createObjectURL(file);
        image.onload = () => {
          try {
            const naturalWidth = image.naturalWidth || image.width;
            const naturalHeight = image.naturalHeight || image.height;
            const cropX = Math.max(0, Math.min(crop.x, 1)) * naturalWidth;
            const cropY = Math.max(0, Math.min(crop.y, 1)) * naturalHeight;
            const cropWidth = Math.max(0.01, Math.min(crop.width, 1)) * naturalWidth;
            const cropHeight = Math.max(0.01, Math.min(crop.height, 1)) * naturalHeight;

            const canvas = document.createElement("canvas");
            canvas.width = Math.max(1, Math.round(cropWidth));
            canvas.height = Math.max(1, Math.round(cropHeight));
            const ctx = canvas.getContext("2d");
            if (!ctx) {
              reject(new Error("Failed to create canvas context"));
              return;
            }
            ctx.drawImage(
              image,
              cropX,
              cropY,
              cropWidth,
              cropHeight,
              0,
              0,
              canvas.width,
              canvas.height,
            );
            const dataUrl = canvas.toDataURL("image/png");
            resolve(dataUrl.split(",")[1] ?? "");
          } catch (error) {
            reject(error);
          } finally {
            if (!previewUrl) {
              URL.revokeObjectURL(objectUrl);
            }
          }
        };
        image.onerror = () => {
          if (!previewUrl) {
            URL.revokeObjectURL(objectUrl);
          }
          reject(new Error("Failed to load image for cropping"));
        };
        image.src = objectUrl;
      });
    },
    [fileToBase64],
  );

  const formatFileSize = useCallback((bytes: number): string => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
  }, []);

  const validateForm = useCallback(
    (
      generationMode: GenerationMode,
      authState: AuthStateLike | null | undefined,
      setErrorMessage: (message: string) => void,
    ): boolean => {
      if (generationMode === "labels" && !labelText.trim()) {
        setErrorMessage("Please provide the label text.");
        return false;
      }

      if (generationMode === "ui-elements") {
        if (!uiReferenceImage) {
          setErrorMessage("Please select a UI reference image.");
          return false;
        }
      }

      if (generationMode !== "ui-elements" && inputType === "text") {
        if (generationMode === "labels" && !generalDescription.trim()) {
          setErrorMessage("Please provide a general theme for your label.");
          return false;
        }
        if (
          generationMode !== "labels" &&
          !generalDescription.trim()
        ) {
          setErrorMessage("Please provide a general description.");
          return false;
        }
      }

      if (generationMode !== "ui-elements" && inputType === "image" && !referenceImage) {
        setErrorMessage("Please select a reference image.");
        return false;
      }

      const cost =
        generationMode === "mockups" || generationMode === "ui-elements"
          ? 1
          : generateVariations
          ? 2
          : 1;
      const user = authState?.user;
      const regularCoins = user?.coins ?? 0;
      const trialCoins = user?.trialCoins ?? 0;
      const hasEnoughRegularCoins = regularCoins >= cost;
      const hasTrialCoins = trialCoins > 0;

      if (!hasEnoughRegularCoins && !hasTrialCoins) {
        const itemType =
          generationMode === "mockups"
            ? "mockups"
            : generationMode === "ui-elements"
            ? "ui elements"
            : generationMode === "illustrations"
            ? "illustrations"
            : generationMode === "labels"
            ? "labels"
            : "icons";
        setErrorMessage(
          `Insufficient coins. You need ${cost} coin${
            cost > 1 ? "s" : ""
          } to generate ${itemType}, or you can use your trial coin for a limited experience.`,
        );
        return false;
      }

      return true;
    },
    [
      generateVariations,
      generalDescription,
      inputType,
      labelText,
      referenceImage,
      uiReferenceImage,
    ],
  );

  return {
    inputType,
    setInputType,
    generateVariations,
    setGenerateVariations,
    generalDescription,
    setGeneralDescription,
    labelText,
    setLabelText,
    individualDescriptions,
    setIndividualDescriptions,
    referenceImage,
    setReferenceImage,
    imagePreview,
    setImagePreview,
    uiReferenceImage,
    setUiReferenceImage,
    uiImagePreview,
    setUiImagePreview,
    uiCrop,
    setUiCrop,
    fileInputRef,
    uiFileInputRef,
    handleImageSelect,
    handleUiImageSelect,
    handleUiImageFromGallery,
    removeImage,
    removeUiImage,
    fileToBase64,
    cropImageToBase64,
    formatFileSize,
    enhancePrompt,
    setEnhancePrompt,
    baseModel,
    setBaseModel,
    variationModel,
    setVariationModel,
    validateForm,
  };
}

export type DashboardFormState = ReturnType<typeof useDashboardFormState>;
