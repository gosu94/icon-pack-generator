import { useCallback, useState } from "react";
import { GenerationMode, UIState } from "@/lib/types";

interface ExportContext {
  requestId: string;
  serviceName: string;
  generationIndex: number;
}

interface UseExportFlowParams {
  mode: GenerationMode;
  setUiState: (state: UIState) => void;
  setErrorMessage: (message: string) => void;
}

export function useExportFlow({
  mode,
  setUiState,
  setErrorMessage,
}: UseExportFlowParams) {
  const [showExportModal, setShowExportModal] = useState(false);
  const [exportContext, setExportContext] = useState<ExportContext | null>(
    null,
  );
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  const exportGeneration = useCallback(
    (requestId: string, serviceName: string, generationIndex: number) => {
      setExportContext({ requestId, serviceName, generationIndex });
      setShowExportModal(true);
    },
    [],
  );

  const downloadZip = useCallback(
    async (
      exportData: any,
      fileName: string,
    ): Promise<void> => {
      const itemType =
        mode === "icons"
          ? "icons"
          : mode === "illustrations"
          ? "illustrations"
          : mode === "labels"
          ? "labels"
          : "ui elements";

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
            message: `Converting ${itemType} to multiple formats${
              mode === "illustrations" ||
              mode === "mockups" ||
              mode === "icons"
                ? " and sizes"
                : ""
            }...`,
            percent: 50,
          });
        }, 500);

        const endpoint =
          mode === "icons"
            ? "/export"
            : mode === "illustrations"
            ? "/api/illustrations/export"
            : mode === "labels"
            ? "/api/labels/export"
            : "/api/mockups/export-elements";

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
          throw new Error(`HTTP error! status: ${response.status}`);
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
          const anchor = document.createElement("a");
          anchor.style.display = "none";
          anchor.href = url;
          anchor.download = fileName;
          document.body.appendChild(anchor);
          anchor.click();
          window.URL.revokeObjectURL(url);
          document.body.removeChild(anchor);
        }, 1000);
      } catch (error) {
        console.error(`Error exporting ${itemType}:`, error);
        setShowProgressModal(false);
        setErrorMessage(`Failed to export ${itemType}. Please try again.`);
        setUiState("error");
      }
    },
    [mode, setErrorMessage, setUiState],
  );

  const downloadMockupPng = useCallback(
    async (requestId: string): Promise<void> => {
      try {
        const response = await fetch("/api/mockups/export", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({
            requestId,
            serviceName: "banana",
            generationIndex: 0,
            formats: ["png"],
          }),
        });
        if (!response.ok) {
          console.error("Mockup PNG export failed:", response.status);
          return;
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.style.display = "none";
        anchor.href = url;
        anchor.download = `ui-mockups-${requestId}.zip`;
        document.body.appendChild(anchor);
        anchor.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(anchor);
      } catch (error) {
        console.error("Error exporting mockup PNG:", error);
      }
    },
    [],
  );

  const confirmExport = useCallback(
    (
      formats: string[],
      sizes?: number[],
      vectorizeSvg?: boolean,
      hqUpscale?: boolean,
    ) => {
      if (!exportContext) {
        return;
      }
      const { requestId, serviceName, generationIndex } = exportContext;
      const packType =
        mode === "icons"
          ? "icon"
          : mode === "illustrations"
          ? "illustration"
          : mode === "labels"
          ? "label"
          : "ui-mockup";
      const fileName = `${packType}-pack-${requestId}-${serviceName}-gen${generationIndex}.zip`;
      const exportData = {
        requestId,
        serviceName,
        generationIndex,
        formats,
        sizes: mode === "labels" ? undefined : sizes,
        vectorizeSvg: vectorizeSvg ?? false,
        hqUpscale: hqUpscale ?? false,
        minSvgSize: mode === "mockups" ? 256 : 0,
      };
      setShowExportModal(false);
      void downloadZip(exportData, fileName);
      if (mode === "mockups") {
        void downloadMockupPng(requestId);
      }
    },
    [downloadZip, downloadMockupPng, exportContext, mode],
  );

  return {
    showExportModal,
    setShowExportModal,
    exportContext,
    exportGeneration,
    confirmExport,
    showProgressModal,
    exportProgress,
  };
}
