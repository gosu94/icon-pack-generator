"use client";

import { useState, useRef, useEffect } from "react";
import Navigation from "../../components/Navigation";

type ProcessedImageResponse = {
  success: boolean;
  originalImage: string;
  processedImage: string;
  originalSize: number;
  processedSize: number;
  filename: string;
  backgroundRemoved: boolean;
  sizeReduction: string;
  message?: string;
  error?: string;
};

export default function BackgroundRemoverPage() {
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string>("");
  const [processedImage, setProcessedImage] = useState<string>("");
  const [originalImage, setOriginalImage] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [stats, setStats] = useState<Partial<ProcessedImageResponse>>({});
  const [error, setError] = useState<string>("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  // Coin state
  const [coins, setCoins] = useState<number>(0);
  const [coinsLoading, setCoinsLoading] = useState(true);

  // Initialize coins as 0 since Navigation handles coin display via auth check
  useEffect(() => {
    setCoinsLoading(false);
  }, []);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (file.size > 10 * 1024 * 1024) {
        setError("File size must be less than 10MB.");
        return;
      }
      setImageFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result as string);
      };
      reader.readAsDataURL(file);
      setProcessedImage("");
      setOriginalImage("");
      setStats({});
      setError("");
    }
  };

  const handleRemoveBackground = async () => {
    if (!imageFile) {
      setError("Please select an image first.");
      return;
    }

    setIsLoading(true);
    setError("");
    setProcessedImage("");
    setOriginalImage("");
    setStats({});

    const formData = new FormData();
    formData.append("image", imageFile);

    try {
      const response = await fetch("/background-removal/process", {
        method: "POST",
        credentials: "include",
        body: formData,
      });

      const result: ProcessedImageResponse = await response.json();

      if (result.success) {
        setOriginalImage(result.originalImage);
        setProcessedImage(result.processedImage);
        setStats({
          originalSize: result.originalSize,
          processedSize: result.processedSize,
          sizeReduction: result.sizeReduction,
          backgroundRemoved: result.backgroundRemoved,
          message: result.message,
        });
      } else {
        setError(result.error || "An unknown error occurred.");
      }
    } catch (err) {
      setError("Failed to process image. Please try again.");
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDownload = () => {
    if (!processedImage || !stats.filename) return;

    const link = document.createElement("a");
    link.href = `/download-processed-image?imageData=${encodeURIComponent(
      processedImage,
    )}&filename=${encodeURIComponent(stats.filename)}`;
    link.download = stats.filename.replace(/(\.[^/.]+)$/, "_no_bg$1");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const triggerFileSelect = () => fileInputRef.current?.click();

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation coins={coins} coinsLoading={coinsLoading} />
      <div className="container mx-auto px-4 py-12">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12">
            <h1 className="text-5xl font-bold text-slate-900 tracking-tight">
              Effortless Background Removal
            </h1>
            <p className="mt-4 text-lg text-slate-600 max-w-2xl mx-auto">
              Upload an image and let our AI instantly remove the background.
              Perfect for creating clean, professional product photos,
              portraits, and more.
            </p>
          </div>

          <div className="bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
              <div
                className="border-2 border-dashed border-slate-300 rounded-2xl p-8 text-center cursor-pointer hover:border-purple-400 hover:bg-slate-50/50 transition-all duration-300"
                onClick={triggerFileSelect}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  onChange={handleFileChange}
                  className="hidden"
                />
                {imagePreview ? (
                  <img
                    src={imagePreview}
                    alt="Selected preview"
                    className="max-h-64 mx-auto rounded-lg shadow-md"
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center h-full">
                    <svg
                      className="w-16 h-16 text-slate-400 mb-4"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1}
                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                      />
                    </svg>
                    <p className="text-slate-500 font-semibold">
                      Click to upload an image
                    </p>
                    <p className="text-xs text-slate-400 mt-1">
                      PNG, JPG, WEBP, etc. Max 10MB.
                    </p>
                  </div>
                )}
              </div>
              <div className="space-y-6">
                <button
                  onClick={handleRemoveBackground}
                  disabled={!imageFile || isLoading}
                  className={`w-full py-4 px-6 rounded-2xl text-white font-semibold ${
                    !imageFile || isLoading
                      ? "bg-slate-400 cursor-not-allowed"
                      : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl"
                  } transition-all duration-200`}
                >
                  {isLoading ? (
                    <div className="flex items-center justify-center space-x-2">
                      <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                      <span>Processing...</span>
                    </div>
                  ) : (
                    "Remove Background"
                  )}
                </button>
                {error && (
                  <p className="text-red-500 text-sm text-center">{error}</p>
                )}
                {stats.backgroundRemoved !== undefined && (
                  <div className="bg-slate-100/70 rounded-xl p-4 text-sm text-slate-700 space-y-2">
                    <div className="flex justify-between">
                      <span className="font-semibold">Background Removed:</span>
                      <span>{stats.backgroundRemoved ? "Yes" : "No"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="font-semibold">Size Reduction:</span>
                      <span>{stats.sizeReduction}</span>
                    </div>
                    {stats.message && (
                      <p className="text-xs text-center pt-2 text-slate-500">
                        {stats.message}
                      </p>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>

          {(originalImage || processedImage) && (
            <div className="mt-12 grid grid-cols-1 md:grid-cols-2 gap-8">
              <div className="text-center">
                <h3 className="text-xl font-bold text-slate-800 mb-4">
                  Original
                </h3>
                <div className="bg-white/80 backdrop-blur-md rounded-2xl p-4 shadow-lg border border-purple-200/30">
                  <img
                    src={originalImage}
                    alt="Original"
                    className="w-full h-auto rounded-lg"
                  />
                </div>
              </div>
              <div className="text-center">
                <h3 className="text-xl font-bold text-slate-800 mb-4">
                  Background Removed
                </h3>
                <div className="bg-white/80 backdrop-blur-md rounded-2xl p-4 shadow-lg border border-purple-200/30">
                  <img
                    src={processedImage}
                    alt="Processed"
                    className="w-full h-auto rounded-lg"
                  />
                </div>
                {processedImage && (
                  <button
                    onClick={handleDownload}
                    className="mt-6 w-full py-3 px-5 rounded-xl text-white font-semibold bg-gradient-to-r from-green-500 to-teal-500 hover:from-green-600 hover:to-teal-600 transform hover:scale-105 shadow-lg hover:shadow-xl transition-all duration-200"
                  >
                    Download Processed Image
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
