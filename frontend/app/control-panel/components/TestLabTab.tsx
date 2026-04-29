"use client";

import { ChangeEvent, useMemo, useRef, useState } from "react";
import { Download, ImageIcon, Loader2, Sparkles, Upload, X } from "lucide-react";
import ExportModal from "../../../components/ExportModal";
import ProgressModal from "../../../components/ProgressModal";
import {
  AdminTestLabModelResult,
  AdminTestLabResponse,
} from "../types";

const EMPTY_PROMPTS = new Array(9).fill("");

function formatTime(ms: number) {
  if (!ms) return "0.0s";
  return `${(ms / 1000).toFixed(1)}s`;
}

export default function TestLabTab() {
  const [inputType, setInputType] = useState<"text" | "image">("text");
  const [generalDescription, setGeneralDescription] = useState("");
  const [individualDescriptions, setIndividualDescriptions] =
    useState<string[]>(EMPTY_PROMPTS);
  const [enhancePrompt, setEnhancePrompt] = useState(false);
  const [referenceImageBase64, setReferenceImageBase64] = useState("");
  const [referenceImagePreview, setReferenceImagePreview] = useState("");
  const [referenceImageName, setReferenceImageName] = useState("");
  const [referenceImageSize, setReferenceImageSize] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<AdminTestLabResponse | null>(null);
  const [downloadingModelId, setDownloadingModelId] = useState<string | null>(null);
  const [exportTarget, setExportTarget] = useState<AdminTestLabModelResult | null>(null);
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  const fileInputRef = useRef<HTMLInputElement>(null);

  const hasReferenceImage = inputType === "image" && !!referenceImageBase64;
  const effectivePromptLabel = hasReferenceImage
    ? "Reference image prompt"
    : "Generated prompt";

  const canSubmit = useMemo(() => {
    if (loading) {
      return false;
    }
    if (inputType === "image") {
      return referenceImageBase64.length > 0;
    }
    return generalDescription.trim().length > 0;
  }, [generalDescription, inputType, loading, referenceImageBase64]);

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const units = ["Bytes", "KB", "MB", "GB"];
    const exponent = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, exponent)).toFixed(2)} ${units[exponent]}`;
  };

  const toBase64 = (file: File) =>
    new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        const result = reader.result as string;
        setReferenceImagePreview(result);
        resolve(result.split(",")[1] || "");
      };
      reader.onerror = (readerError) => reject(readerError);
    });

  const handleImageSelect = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (!file.type.startsWith("image/")) {
      setError("Please select a valid image file.");
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setError("File size must be less than 10MB.");
      return;
    }

    try {
      setError(null);
      setReferenceImageName(file.name);
      setReferenceImageSize(file.size);
      setReferenceImageBase64(await toBase64(file));
    } catch {
      setError("Failed to read the selected image.");
    }
  };

  const removeReferenceImage = () => {
    setReferenceImageBase64("");
    setReferenceImagePreview("");
    setReferenceImageName("");
    setReferenceImageSize(0);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handlePromptChange = (index: number, value: string) => {
    setIndividualDescriptions((current) => {
      const next = [...current];
      next[index] = value;
      return next;
    });
  };

  const submit = async () => {
    if (!canSubmit) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const payload = {
        generalDescription: generalDescription.trim(),
        referenceImageBase64: inputType === "image" ? referenceImageBase64 : null,
        iconCount: 9,
        individualDescriptions,
        enhancePrompt: inputType === "text" ? enhancePrompt : false,
      };

      const apiResponse = await fetch("/api/admin/test-lab/icons", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(payload),
      });

      const data = await apiResponse.json();
      if (!apiResponse.ok) {
        throw new Error(data.error || "Failed to run test lab generation");
      }

      setResponse(data as AdminTestLabResponse);
    } catch (submitError) {
      const message =
        submitError instanceof Error
          ? submitError.message
          : "Failed to run test lab generation";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const renderModelCard = (result: AdminTestLabModelResult) => {
    const isSuccess = result.status === "success";
    const isDownloading = downloadingModelId === result.modelId;

    return (
      <div
        key={result.modelId}
        className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-slate-900">
              {result.modelLabel}
            </h3>
            <p className="text-sm text-slate-500">
              {isSuccess ? "Completed" : "Failed"} in{" "}
              {formatTime(result.generationTimeMs)}
            </p>
          </div>
          <span
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              isSuccess
                ? "bg-emerald-100 text-emerald-700"
                : "bg-rose-100 text-rose-700"
            }`}
          >
            {result.status}
          </span>
        </div>

        <p
          className={`mb-4 text-sm ${
            isSuccess ? "text-slate-600" : "text-rose-700"
          }`}
        >
          {result.message}
        </p>

        {isSuccess && result.icons.length > 0 && (
          <div className="mb-4 flex justify-end">
            <button
              type="button"
              onClick={() => openExportModal(result)}
              disabled={isDownloading}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isDownloading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Download className="h-4 w-4" />
              )}
              Download
            </button>
          </div>
        )}

        {isSuccess && result.originalGridImageBase64 && (
          <div className="mb-4 overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
            <img
              src={`data:image/png;base64,${result.originalGridImageBase64}`}
              alt={`${result.modelLabel} grid output`}
              className="h-auto w-full object-cover"
            />
          </div>
        )}

        {isSuccess && result.icons.length > 0 ? (
          <div className="grid grid-cols-3 gap-2">
            {result.icons.map((icon) => (
              <div
                key={icon.id}
                className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50"
              >
                <img
                  src={`data:image/png;base64,${icon.base64Data}`}
                  alt={`${result.modelLabel} icon ${icon.gridPosition + 1}`}
                  className="h-auto w-full object-cover"
                />
              </div>
            ))}
          </div>
        ) : (
          !isSuccess && (
            <div className="rounded-xl border border-dashed border-rose-200 bg-rose-50 px-4 py-6 text-sm text-rose-700">
              No output was produced for this model.
            </div>
          )
        )}
      </div>
    );
  };

  const openExportModal = (result: AdminTestLabModelResult) => {
    setExportTarget(result);
    setShowExportModal(true);
  };

  const downloadModelResults = async (
    result: AdminTestLabModelResult,
    formats: string[],
    vectorizeSvg?: boolean,
    hqUpscale?: boolean,
  ) => {
    if (!response || !result.icons.length) {
      return;
    }

    setDownloadingModelId(result.modelId);
    setError(null);
    setShowProgressModal(true);
    setExportProgress({
      step: 1,
      message: "Preparing export request...",
      percent: 25,
    });

    try {
      const exportRequest = {
        requestId: `test-lab-${response.seed}`,
        serviceName: result.modelId,
        generationIndex: 1,
        icons: result.icons,
        formats,
        vectorizeSvg: vectorizeSvg ?? false,
        hqUpscale: hqUpscale ?? false,
      };

      setTimeout(() => {
        setExportProgress({
          step: 2,
          message: `Converting icons to multiple formats and sizes...`,
          percent: 50,
        });
      }, 500);

      const exportResponse = await fetch("/api/admin/test-lab/icons/export", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(exportRequest),
      });

      if (!exportResponse.ok) {
        const message = await exportResponse.text();
        throw new Error(message || "Failed to export icons");
      }

      setExportProgress({
        step: 3,
        message: "Creating ZIP file...",
        percent: 75,
      });

      const blob = await exportResponse.blob();
      setExportProgress({
        step: 4,
        message: "Finalizing download...",
        percent: 100,
      });

      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `icon-pack-test-lab-${result.modelId}-${response.seed}.zip`;
      setTimeout(() => {
        setShowProgressModal(false);
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
      }, 1000);
    } catch (downloadError) {
      setShowProgressModal(false);
      const message =
        downloadError instanceof Error
          ? downloadError.message
          : "Failed to export icons";
      setError(message);
    } finally {
      setDownloadingModelId(null);
    }
  };

  const confirmExport = (
    formats: string[],
    _sizes?: number[],
    vectorizeSvg?: boolean,
    hqUpscale?: boolean,
  ) => {
    if (!exportTarget) {
      return;
    }
    setShowExportModal(false);
    void downloadModelResults(exportTarget, formats, vectorizeSvg, hqUpscale);
  };

  return (
    <div className="space-y-6 p-6">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">Test lab</h2>
            <p className="text-sm text-slate-600">
              Compare icon outputs across GPT-1, GPT-1.5, and GPT-2 using the
              same admin-only prompt pipeline.
            </p>
          </div>

          <div className="inline-flex rounded-xl border border-slate-200 bg-slate-100 p-1">
            <button
              type="button"
              onClick={() => {
                setInputType("text");
                removeReferenceImage();
              }}
              className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
                inputType === "text"
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Theme prompt
            </button>
            <button
              type="button"
              onClick={() => {
                setInputType("image");
                setEnhancePrompt(false);
              }}
              className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
                inputType === "image"
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Reference image
            </button>
          </div>
        </div>

        <div className="space-y-6">
          {inputType === "text" ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <label className="text-sm font-semibold text-slate-700">
                  General theme prompt
                </label>
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={enhancePrompt}
                    onChange={(event) => setEnhancePrompt(event.target.checked)}
                    className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                  <Sparkles className="h-4 w-4 text-blue-600" />
                  Enhance prompt
                </label>
              </div>
              <textarea
                value={generalDescription}
                onChange={(event) => setGeneralDescription(event.target.value)}
                placeholder="Describe the icon pack theme, style, colors, and visual direction."
                rows={5}
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          ) : (
            <div className="space-y-3">
              <label className="text-sm font-semibold text-slate-700">
                Reference image
              </label>
              {!referenceImagePreview ? (
                <label className="flex cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center transition hover:border-blue-400 hover:bg-blue-50">
                  <Upload className="mb-3 h-8 w-8 text-slate-500" />
                  <span className="text-sm font-semibold text-slate-800">
                    Upload reference image
                  </span>
                  <span className="mt-1 text-xs text-slate-500">
                    PNG, JPG, or WebP up to 10MB
                  </span>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleImageSelect}
                    className="hidden"
                  />
                </label>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900">
                        {referenceImageName}
                      </p>
                      <p className="text-xs text-slate-500">
                        {formatFileSize(referenceImageSize)}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={removeReferenceImage}
                      className="rounded-lg p-2 text-slate-500 transition hover:bg-white hover:text-slate-900"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>
                  <img
                    src={referenceImagePreview}
                    alt="Reference preview"
                    className="max-h-80 w-full rounded-xl object-contain"
                  />
                </div>
              )}
            </div>
          )}

          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <ImageIcon className="h-4 w-4 text-slate-500" />
              <label className="text-sm font-semibold text-slate-700">
                Individual icon prompts
              </label>
            </div>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {individualDescriptions.map((value, index) => (
                <input
                  key={index}
                  type="text"
                  value={value}
                  onChange={(event) =>
                    handlePromptChange(index, event.target.value)
                  }
                  placeholder={`Icon ${index + 1} description (optional)`}
                  className="rounded-xl border border-slate-200 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
                />
              ))}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={submit}
              disabled={!canSubmit}
              className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Run test lab
            </button>

            {response?.seed ? (
              <span className="text-xs text-slate-500">Seed: {response.seed}</span>
            ) : null}
          </div>

          {error ? (
            <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          ) : null}

          {response ? (
            <div className="space-y-4 rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div className="grid gap-4 lg:grid-cols-2">
                <div>
                  <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Effective general theme
                  </p>
                  <p className="text-sm text-slate-700">
                    {response.effectiveGeneralDescription?.trim() ||
                      "Reference-image mode without additional theme text."}
                  </p>
                </div>
                <div>
                  <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    {effectivePromptLabel}
                  </p>
                  <p className="text-sm text-slate-700">{response.promptUsed}</p>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        {(response?.results ?? [
          {
            modelId: "gpt",
            modelLabel: "GPT-1",
            status: "idle",
            message: "Run the test lab to see results.",
            generationTimeMs: 0,
            originalGridImageBase64: "",
            icons: [],
          },
          {
            modelId: "gpt15",
            modelLabel: "GPT-1.5",
            status: "idle",
            message: "Run the test lab to see results.",
            generationTimeMs: 0,
            originalGridImageBase64: "",
            icons: [],
          },
          {
            modelId: "gpt2",
            modelLabel: "GPT-2",
            status: "idle",
            message: "Run the test lab to see results.",
            generationTimeMs: 0,
            originalGridImageBase64: "",
            icons: [],
          },
        ]).map((result) =>
          result.status === "idle" ? (
            <div
              key={result.modelId}
              className="rounded-2xl border border-dashed border-slate-300 bg-white/70 px-6 py-10 text-center shadow-sm"
            >
              <h3 className="text-lg font-semibold text-slate-900">
                {result.modelLabel}
              </h3>
              <p className="mt-2 text-sm text-slate-500">{result.message}</p>
            </div>
          ) : (
            renderModelCard(result)
          ),
        )}
      </div>

      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmExport}
        iconCount={exportTarget?.icons.length ?? 0}
        mode="icons"
        ignoreCoinBalance={true}
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />
    </div>
  );
}
