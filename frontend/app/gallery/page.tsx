"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Navigation from "../../components/Navigation";
import ExportModal from "../../components/ExportModal";
import ProgressModal from "../../components/ProgressModal";
import { Download, Sparkles } from "lucide-react";

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

interface Illustration {
  id: number;
  imageUrl: string;
  description: string;
  requestId: string;
  illustrationType: string;
  theme: string;
}

interface Mockup {
  id: number;
  imageUrl: string;
  description: string;
  requestId: string;
  mockupType: string;
  theme: string;
}

type GroupedIcons = Record<string, { original: Icon[]; variation: Icon[] }>;
type GroupedIllustrations = Record<string, { original: Illustration[]; variation: Illustration[] }>;
type GroupedMockups = Record<string, { original: Mockup[]; variation: Mockup[] }>;

export default function GalleryPage() {
  const router = useRouter();
  const [groupedIcons, setGroupedIcons] = useState<GroupedIcons>({});
  const [groupedIllustrations, setGroupedIllustrations] = useState<GroupedIllustrations>({});
  const [groupedMockups, setGroupedMockups] = useState<GroupedMockups>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRequest, setSelectedRequest] = useState<string | null>(null);
  const [galleryType, setGalleryType] = useState<string | null>(null);

  // Export state
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [iconsToExport, setIconsToExport] = useState<Icon[]>([]);
  const [illustrationsToExport, setIllustrationsToExport] = useState<Illustration[]>([]);
  const [mockupsToExport, setMockupsToExport] = useState<Mockup[]>([]);

  const [exportProgress, setExportProgress] = useState({
    step: 1,
    message: "",
    percent: 25,
  });

  // Preview state
  const [previewImage, setPreviewImage] = useState<string | null>(null);

  const handleImageClick = (imageUrl: string) => {
    // Only open preview for illustrations and mockups
    if (galleryType === "illustrations" || galleryType === "mockups") {
      setPreviewImage(imageUrl);
    }
  };

  const closePreview = () => {
    setPreviewImage(null);
  };

  useEffect(() => {
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
        const data: Illustration[] = await response.json();

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

    if (galleryType === "icons") {
      fetchIcons();
    } else if (galleryType === "illustrations") {
      fetchIllustrations();
    } else if (galleryType === "mockups") {
      fetchMockups();
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

  const openExportModal = (icons: Icon[]) => {
    setIconsToExport(icons);
    setIllustrationsToExport([]);
    setShowExportModal(true);
  };

  const openIllustrationExportModal = (illustrations: Illustration[]) => {
    setIllustrationsToExport(illustrations);
    setIconsToExport([]);
    setMockupsToExport([]);
    setShowExportModal(true);
  };

  const openMockupExportModal = (mockups: Mockup[]) => {
    setMockupsToExport(mockups);
    setIconsToExport([]);
    setIllustrationsToExport([]);
    setShowExportModal(true);
  };

  const handleGenerateMore = async (iconType: string) => {
    if (!selectedRequest) return;

    try {
      // Call the backend to create the grid composition
      const response = await fetch("/api/gallery/compose-grid", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          requestId: selectedRequest,
          iconType: iconType,
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();

      if (data.status === "success" && data.gridImageBase64) {
        // Convert base64 to blob and create URL
        const binaryString = atob(data.gridImageBase64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        const blob = new Blob([bytes], { type: "image/png" });

        // Store the grid image data in sessionStorage for the dashboard to use
        const gridImageUrl = URL.createObjectURL(blob);
        sessionStorage.setItem("generatedGridImage", gridImageUrl);
        sessionStorage.setItem("generateMoreMode", "true");
        sessionStorage.setItem("generationMode", "icons");

        // Navigate to dashboard
        router.push("/dashboard");
      } else {
        console.error("Failed to create grid:", data.error);
        alert("Failed to create grid composition. Please try again.");
      }
    } catch (error) {
      console.error("Error creating grid composition:", error);
      alert("Failed to create grid composition. Please try again.");
    }
  };

  const handleGenerateMockupFromIcons = async (iconType: string) => {
    if (!selectedRequest) return;

    try {
      // Call the backend to create the grid composition (same endpoint as generate more)
      const response = await fetch("/api/gallery/compose-grid", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          requestId: selectedRequest,
          iconType: iconType,
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();

      if (data.status === "success" && data.gridImageBase64) {
        // Convert base64 to blob and create URL
        const binaryString = atob(data.gridImageBase64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        const blob = new Blob([bytes], { type: "image/png" });

        // Store the grid image data in sessionStorage for the dashboard to use
        const gridImageUrl = URL.createObjectURL(blob);
        sessionStorage.setItem("generatedGridImage", gridImageUrl);
        sessionStorage.setItem("generateMoreMode", "true");
        sessionStorage.setItem("generationMode", "mockups"); // Set to mockups mode

        // Navigate to dashboard
        router.push("/dashboard");
      } else {
        console.error("Failed to create grid:", data.error);
        alert("Failed to create grid composition. Please try again.");
      }
    } catch (error) {
      console.error("Error creating grid composition:", error);
      alert("Failed to create grid composition. Please try again.");
    }
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

  const confirmGalleryExport = (formats: string[], sizes?: number[], vectorizeSvg?: boolean) => {
    if (iconsToExport.length > 0) {
      const iconFilePaths = iconsToExport.map((icon) => icon.imageUrl);
      const fileName = `icon-pack-gallery-${new Date().getTime()}.zip`;
      const exportData = {
        iconFilePaths,
        formats,
        vectorizeSvg: vectorizeSvg ?? false,
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
    }
  };

  const downloadZip = async (exportData: any, fileName: string, endpoint: string) => {
    setShowProgressModal(true);
    const itemType = endpoint.includes("illustration") ? "illustrations" : endpoint.includes("mockup") ? "mockups" : "icons";
    setExportProgress({
      step: 1,
      message: "Preparing export request...",
      percent: 25,
    });
    try {
      setTimeout(() => {
        setExportProgress({
          step: 2,
          message: `Converting ${itemType} to multiple formats and sizes...`,
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
      console.error(`Error exporting ${itemType}:`, error);
      setShowProgressModal(false);
      alert(`Failed to export ${itemType}. Please try again.`);
    }
  };

  const selectedIconGroup = selectedRequest
    ? groupedIcons[selectedRequest]
    : null;

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
                </div>
              </div>
            ) : (
              <>
                {selectedRequest ? (
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
                ) : (
                  <button
                    onClick={() => {
                      setGalleryType(null);
                      setSelectedRequest(null);
                      setGroupedIcons({});
                      setGroupedIllustrations({});
                      setError(null);
                    }}
                    className="mb-8 inline-flex items-center gap-2 rounded-md bg-slate-100 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-200 transition-colors"
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
                            className="px-2 sm:px-4 py-2 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
                          >
                            <Download className="w-4 h-4" />
                            <span className="hidden sm:inline">
                              Export All (
                              {selectedIconGroup.original.length +
                                selectedIconGroup.variation.length}{" "}
                              icons)
                            </span>
                          </button>
                        </div>

                        {selectedIconGroup.original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Icons
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateMore("original")}
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
                                  title="Generate more like this"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() => handleGenerateMockupFromIcons("original")}
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-1"
                                  title="Generate UI Mockup from these icons"
                                >
                                  <span className="text-xs font-bold">UI</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openExportModal(
                                      selectedIconGroup.original
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() => handleGenerateMockupFromIcons("variation")}
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-1"
                                  title="Generate UI Mockup from these icons"
                                >
                                  <span className="text-xs font-bold">UI</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openExportModal(
                                      selectedIconGroup.variation
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                            {groupedIllustrations[selectedRequest].original[0]?.theme ||
                              groupedIllustrations[selectedRequest].variation[0]?.theme ||
                              `Request: ${selectedRequest}`}
                          </h1>
                          <button
                            onClick={() =>
                              openIllustrationExportModal([
                                ...groupedIllustrations[selectedRequest].original,
                                ...groupedIllustrations[selectedRequest].variation,
                              ])
                            }
                            className="px-2 sm:px-4 py-2 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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

                        {groupedIllustrations[selectedRequest].original.length > 0 && (
                          <div className="mb-8 p-4 rounded-lg border border-slate-200/80 bg-white/50 shadow-lg shadow-slate-200/50">
                            <div className="flex items-center justify-between mb-4">
                              <h3 className="text-xl font-semibold text-slate-700">
                                Original Illustrations
                              </h3>
                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleGenerateMoreIllustrations("original")}
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openIllustrationExportModal(
                                      groupedIllustrations[selectedRequest].original
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
                                >
                                  <Sparkles className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() =>
                                    openIllustrationExportModal(
                                      groupedIllustrations[selectedRequest].variation
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                            {groupedMockups[selectedRequest].original[0]?.theme ||
                              groupedMockups[selectedRequest].variation[0]?.theme ||
                              `Request: ${selectedRequest}`}
                          </h1>
                          <button
                            onClick={() =>
                              openMockupExportModal([
                                ...groupedMockups[selectedRequest].original,
                                ...groupedMockups[selectedRequest].variation,
                              ])
                            }
                            className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-pink-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-1"
                                  title="Generate Icons from this Mockup"
                                >
                                  <span className="text-xs font-bold">Icon</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openMockupExportModal(
                                      groupedMockups[selectedRequest].original
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-pink-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-1"
                                  title="Generate Icons from this Mockup"
                                >
                                  <span className="text-xs font-bold">Icon</span>
                                </button>
                                <button
                                  onClick={() =>
                                    openMockupExportModal(
                                      groupedMockups[selectedRequest].variation
                                    )
                                  }
                                  className="px-2 sm:px-4 py-2 bg-gradient-to-r from-blue-600 to-pink-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transform hover:scale-105 transition-all duration-200 flex items-center justify-center gap-2"
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
        iconCount={iconsToExport.length > 0 ? iconsToExport.length : illustrationsToExport.length > 0 ? illustrationsToExport.length : mockupsToExport.length}
        mode={iconsToExport.length > 0 ? "icons" : illustrationsToExport.length > 0 ? "illustrations" : "mockups"}
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />

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
