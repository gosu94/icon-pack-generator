"use client";

import { useState, useEffect } from "react";
import Navigation from "../../components/Navigation";
import ExportModal from "../../components/ExportModal";
import ProgressModal from "../../components/ProgressModal";

// Local Icon type for the gallery page, matching the backend DTO
interface Icon {
  id: number;
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
  iconType: string;
  theme: string;
}

type GroupedIcons = Record<string, { original: Icon[]; variation: Icon[] }>;

export default function GalleryPage() {
  const [groupedIcons, setGroupedIcons] = useState<GroupedIcons>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRequest, setSelectedRequest] = useState<string | null>(null);

  // Export state
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [iconsToExport, setIconsToExport] = useState<Icon[]>([]);
  
  // Coin state
  const [coins, setCoins] = useState<number>(0);
  const [coinsLoading, setCoinsLoading] = useState(true);

  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  useEffect(() => {
    const fetchIcons = async () => {
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
            acc[icon.requestId] = { original: [], variation: [] };
          }
          if (icon.iconType === "original") {
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

    fetchIcons();
  }, []);

  // Initialize coins as 0 since Navigation handles coin display via auth check
  useEffect(() => {
    setCoinsLoading(false);
  }, []);

  const handleSelectRequest = (requestId: string) => {
    setSelectedRequest(requestId);
  };

  const handleBackToGallery = () => {
    setSelectedRequest(null);
  };

  const openExportModal = (icons: Icon[]) => {
    setIconsToExport(icons);
    setShowExportModal(true);
  };

  const confirmGalleryExport = () => {
    if (iconsToExport.length > 0) {
      const iconFilePaths = iconsToExport.map((icon) => icon.imageUrl);
      const fileName = `icon-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        iconFilePaths,
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

      const response = await fetch("/api/export-gallery", {
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
      }, 1000);
    } catch (error) {
      console.error("Error exporting icons:", error);
      setShowProgressModal(false);
      alert("Failed to export icons. Please try again.");
    }
  };

  const selectedIconGroup = selectedRequest
    ? groupedIcons[selectedRequest]
    : null;

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation coins={coins} coinsLoading={coinsLoading} />
      <div className="container mx-auto px-4 py-8">
        {loading && <p>Loading...</p>}
        {error && <p className="text-red-500">{error}</p>}
        {!loading && !error && (
          <>
            {selectedIconGroup && selectedRequest ? (
              <div>
                <button
                  onClick={handleBackToGallery}
                  className="mb-8 inline-flex items-center gap-2 rounded-md bg-purple-50 px-4 py-2 text-sm font-medium text-purple-600 hover:bg-purple-100 transition-colors"
                >
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

                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
                  <h1 className="text-3xl font-bold text-slate-800 mb-4 sm:mb-0">
                    {selectedIconGroup.original[0]?.theme ||
                      selectedIconGroup.variation[0]?.theme ||
                      `Request: ${selectedRequest}`}
                  </h1>
                  <button
                    onClick={() =>
                      openExportModal([
                        ...selectedIconGroup.original,
                        ...selectedIconGroup.variation,
                      ])
                    }
                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                  >
                    Export All (
                    {selectedIconGroup.original.length +
                      selectedIconGroup.variation.length}{" "}
                    icons)
                  </button>
                </div>

                {selectedIconGroup.original.length > 0 && (
                  <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                    <div className="flex items-center justify-between mb-4">
                      <h3 className="text-xl font-semibold text-slate-700">
                        Original Icons
                      </h3>
                      <button
                        onClick={() =>
                          openExportModal(selectedIconGroup.original)
                        }
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                      >
                        Export Originals ({selectedIconGroup.original.length})
                      </button>
                    </div>
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                      {selectedIconGroup.original.map((icon, index) => (
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
                      ))}
                    </div>
                  </div>
                )}

                {selectedIconGroup.variation.length > 0 && (
                  <div className="p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                    <div className="flex items-center justify-between mb-4">
                      <h3 className="text-xl font-semibold text-slate-700">
                        Variations
                      </h3>
                      <button
                        onClick={() =>
                          openExportModal(selectedIconGroup.variation)
                        }
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                      >
                        Export Variations ({selectedIconGroup.variation.length})
                      </button>
                    </div>
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                      {selectedIconGroup.variation.map((icon, index) => (
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
                      );
                    },
                  )}
                </div>
              </div>
            )}
          </>
        )}
      </div>
      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmGalleryExport}
        iconCount={iconsToExport.length}
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />
    </div>
  );
}
