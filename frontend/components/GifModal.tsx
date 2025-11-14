import React, { useEffect, useState } from "react";
import { Download } from "lucide-react";
import { GifAsset } from "@/lib/types";

export interface GifModalProgress {
  status: string;
  message: string;
  total: number;
  completed: number;
  percent: number;
}

export interface GifModalIcon {
  id: string;
  imageSrc: string;
  description?: string;
  selectable: boolean;
  isSelected: boolean;
  onToggle?: () => void;
}

interface GifModalProps {
  title: string;
  subtitle?: string;
  icons: GifModalIcon[];
  selectedCount: number;
  estimatedCost: number;
  availableCoins: number;
  trialCoins: number;
  insufficientBalance: boolean;
  progress: GifModalProgress;
  gifResults: GifAsset[];
  gifRefreshToken: number;
  gifError: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onGenerate: () => void;
}

const PROGRESS_TARGET_PERCENT = 95;
const PROGRESS_DURATION_MS = 70_000;
const PROGRESS_INTERVAL_MS = 500;
const PROGRESS_INCREMENT =
  PROGRESS_TARGET_PERCENT / (PROGRESS_DURATION_MS / PROGRESS_INTERVAL_MS);

const GifModal: React.FC<GifModalProps> = ({
  title,
  subtitle = "Select icons to animate. Each GIF costs 2 coins.",
  icons,
  selectedCount,
  estimatedCost,
  availableCoins,
  trialCoins,
  insufficientBalance,
  progress,
  gifResults,
  gifRefreshToken,
  gifError,
  isSubmitting,
  onClose,
  onGenerate,
}) => {
  const [simulatedPercent, setSimulatedPercent] = useState(progress.percent);

  useEffect(() => {
    if (progress.status === "idle") {
      setSimulatedPercent(progress.percent);
      return;
    }
    setSimulatedPercent((prev) => Math.max(prev, progress.percent));
  }, [progress.percent, progress.status]);

  useEffect(() => {
    if (!isSubmitting) {
      if (progress.status === "completed" || progress.percent >= 100) {
        setSimulatedPercent(100);
      }
      return;
    }

    const interval = window.setInterval(() => {
      setSimulatedPercent((prev) => {
        if (prev >= PROGRESS_TARGET_PERCENT) {
          return prev;
        }
        const next = prev + PROGRESS_INCREMENT;
        return next > PROGRESS_TARGET_PERCENT ? PROGRESS_TARGET_PERCENT : next;
      });
    }, PROGRESS_INTERVAL_MS);

    return () => window.clearInterval(interval);
  }, [isSubmitting, progress.status, progress.percent]);

  const showComplete =
    !isSubmitting && (progress.status === "completed" || progress.percent >= 100);
  const visualPercent = showComplete
    ? 100
    : Math.min(PROGRESS_TARGET_PERCENT, simulatedPercent);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-4xl bg-white rounded-3xl shadow-2xl border border-slate-100 p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <p className="text-sm uppercase tracking-wide text-slate-500 font-semibold">
              Animated GIFs
            </p>
            <h2 className="text-2xl font-bold text-slate-900">{title}</h2>
            <p className="text-sm text-slate-600 mt-1">{subtitle}</p>
          </div>
          <button
            onClick={onClose}
            className="self-end text-slate-600 hover:text-slate-900 transition-colors"
            aria-label="Close GIF modal"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 mt-6">
          {icons.map((icon) => (
            <button
              key={icon.id}
              type="button"
              disabled={!icon.selectable}
              onClick={icon.selectable ? icon.onToggle : undefined}
              className={`relative border rounded-2xl p-2 transition-all hover:shadow-md ${
                icon.isSelected ? "border-blue-500 shadow-lg" : "border-slate-200"
              } ${!icon.selectable ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
            >
              <img src={icon.imageSrc} alt={icon.description || "Icon"} className="w-full h-auto rounded-xl" />
              {icon.isSelected && (
                <span className="absolute top-3 right-3 text-xs font-semibold bg-blue-600 text-white px-2 py-0.5 rounded-full">
                  Selected
                </span>
              )}
              {!icon.selectable && (
                <span className="absolute top-3 right-3 text-xs font-semibold bg-slate-400 text-white px-2 py-0.5 rounded-full">
                  N/A
                </span>
              )}
            </button>
          ))}
        </div>

        <div className="mt-6 text-sm text-slate-600 space-y-1">
          <p>
            Selected icons: <span className="font-semibold text-slate-900">{selectedCount}</span>
          </p>
          <p>
            Cost: <span className="font-semibold text-slate-900">{estimatedCost}</span> coins (2 per icon)
          </p>
          <p>
            Balance: <span className="font-semibold text-slate-900">{availableCoins}</span> coins · Trial coins:{" "}
            <span className="font-semibold text-slate-900">{trialCoins}</span>
          </p>
          {insufficientBalance && (
            <p className="text-red-500 font-medium">Not enough coins. Please purchase more coins to continue.</p>
          )}
        </div>

        {progress.status !== "idle" && (
          <div className="mt-6">
            <div className="w-full h-3 bg-slate-100 rounded-full overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-blue-500 to-purple-500 transition-all duration-300"
                style={{ width: `${visualPercent}%` }}
              />
            </div>
            <div className="mt-2 flex flex-col sm:flex-row sm:items-center sm:justify-between text-sm text-slate-600 gap-2">
              <span>{progress.message}</span>
              {progress.total > 0 && (
                <span>
                  {progress.completed} / {progress.total} completed
                </span>
              )}
            </div>
          </div>
        )}

        {gifResults.length > 0 && (
          <div className="mt-6">
            <h3 className="text-lg font-semibold text-slate-900 mb-3">Generated GIFs</h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
              {gifResults.map((asset) => (
                <div key={asset.iconId || asset.fileName} className="border border-slate-200 rounded-2xl p-3 shadow-sm bg-white space-y-2">
                  <img
                    src={`${asset.filePath}?loop=${gifRefreshToken}`}
                    alt={asset.fileName}
                    className="w-full h-auto rounded-xl border border-slate-100"
                  />
                  <a
                    href={asset.filePath}
                    download
                    className="inline-flex items-center justify-center gap-2 px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#ffffff] text-[#3C4BFF] border border-[#E6E8FF] hover:bg-[#F5F6FF] transition-colors"
                  >
                    <Download className="w-4 h-4" />
                    Download
                  </a>
                </div>
              ))}
            </div>
          </div>
        )}

        {gifError && <p className="text-sm text-red-500 mt-4">{gifError}</p>}

        <div className="mt-6 flex flex-col sm:flex-row sm:justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50 font-semibold"
          >
            Close
          </button>
          <button
            onClick={onGenerate}
            disabled={isSubmitting || selectedCount === 0 || insufficientBalance}
            className={`px-4 py-2 rounded-xl font-semibold text-white transition-all ${
              isSubmitting || selectedCount === 0 || insufficientBalance
                ? "bg-slate-300 cursor-not-allowed"
                : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 shadow-lg"
            }`}
          >
            {isSubmitting ? "Generating..." : "Generate GIFs"}
          </button>
        </div>
      </div>
    </div>
  );
};

export default GifModal;
