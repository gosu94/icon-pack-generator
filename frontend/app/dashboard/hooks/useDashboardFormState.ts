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

  const fileInputRef = useRef<HTMLInputElement>(null);

  const resetFormForMode = useCallback(
    (nextMode: GenerationMode) => {
      const count =
        nextMode === "illustrations"
          ? 4
          : nextMode === "mockups"
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
  }, [mode, resetFormForMode]);

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

  const removeImage = useCallback(() => {
    setReferenceImage(null);
    setImagePreview("");
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
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

      if (inputType === "text") {
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

      if (inputType === "image" && !referenceImage) {
        setErrorMessage("Please select a reference image.");
        return false;
      }

      const cost =
        generationMode === "mockups" ? 1 : generateVariations ? 2 : 1;
      const user = authState?.user;
      const regularCoins = user?.coins ?? 0;
      const trialCoins = user?.trialCoins ?? 0;
      const hasEnoughRegularCoins = regularCoins >= cost;
      const hasTrialCoins = trialCoins > 0;

      if (!hasEnoughRegularCoins && !hasTrialCoins) {
        const itemType =
          generationMode === "mockups"
            ? "mockups"
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
    fileInputRef,
    handleImageSelect,
    removeImage,
    fileToBase64,
    formatFileSize,
    validateForm,
  };
}

export type DashboardFormState = ReturnType<typeof useDashboardFormState>;
