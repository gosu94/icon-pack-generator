'use client';

import { useState, useEffect } from 'react';
import Navigation from '../../components/Navigation';
import ExportModal from '../../components/ExportModal';
import ProgressModal from '../../components/ProgressModal';

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

type GroupedIcons = Record<string, { original: Icon[], variation: Icon[] }>;

export default function GalleryPage() {
  const [groupedIcons, setGroupedIcons] = useState<GroupedIcons>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string[]>([]);

  // Export state
  const [showExportModal, setShowExportModal] = useState(false);
  const [showProgressModal, setShowProgressModal] = useState(false);
  const [iconsToExport, setIconsToExport] = useState<Icon[]>([]);
  const [removeBackground, setRemoveBackground] = useState(true);
  const [outputFormat, setOutputFormat] = useState('png');
  const [exportProgress, setExportProgress] = useState({ step: 1, message: '', percent: 25 });

  useEffect(() => {
    const fetchIcons = async () => {
      try {
        const response = await fetch('/api/user/icons');
        if (!response.ok) {
          throw new Error('Failed to fetch icons');
        }
        const data: Icon[] = await response.json();
        
        const grouped = data.reduce((acc, icon) => {
          if (!acc[icon.requestId]) {
            acc[icon.requestId] = { original: [], variation: [] };
          }
          if (icon.iconType === 'original') {
            acc[icon.requestId].original.push(icon);
          } else if (icon.iconType === 'variation') {
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

  const toggleExpand = (id: string) => {
    setExpanded(current =>
      current.includes(id) ? current.filter(item => item !== id) : [...current, id]
    );
  };

  const openExportModal = (icons: Icon[]) => {
    setIconsToExport(icons);
    setShowExportModal(true);
  };

  const confirmGalleryExport = () => {
    if (iconsToExport.length > 0) {
        const iconFilePaths = iconsToExport.map(icon => icon.imageUrl);
        const fileName = `icon-pack-gallery-${new Date().getTime()}.zip`;
        const exportData = {
            iconFilePaths,
            removeBackground: removeBackground,
            outputFormat: outputFormat
        };
        setShowExportModal(false);
        downloadZip(exportData, fileName);
    }
  };

  const downloadZip = async (exportData: any, fileName: string) => {
    setShowProgressModal(true);
    setExportProgress({ step: 1, message: 'Preparing export request...', percent: 25 });
    try {
        setTimeout(() => {
            setExportProgress({ 
                step: 2, 
                message: exportData.removeBackground ? 'Processing icons and removing backgrounds...' : 'Processing icons...', 
                percent: 50 
            });
        }, 500);

        const response = await fetch('/api/export-gallery', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(exportData)
        });

        setExportProgress({ step: 3, message: 'Creating ZIP file...', percent: 75 });
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        
        const blob = await response.blob();
        setExportProgress({ step: 4, message: 'Finalizing download...', percent: 100 });
        
        setTimeout(() => {
            setShowProgressModal(false);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        }, 1000);
    } catch (error) {
        console.error('Error exporting icons:', error);
        setShowProgressModal(false);
        alert('Failed to export icons. Please try again.');
    }
  };

  return (
    <div className="min-h-screen bg-white">
      <Navigation />
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold mb-8">Icon Gallery</h1>
        {loading && <p>Loading...</p>}
        {error && <p className="text-red-500">{error}</p>}
        {!loading && !error && (
          <div>
            {Object.entries(groupedIcons).map(([requestId, iconTypes]) => {
              const isRequestExpanded = expanded.includes(requestId);

              const getRequestPreview = () => {
                if (iconTypes.original.length > 0) return iconTypes.original[0].imageUrl;
                if (iconTypes.variation.length > 0) return iconTypes.variation[0].imageUrl;
                return '';
              }
              
              const theme = iconTypes.original[0]?.theme || iconTypes.variation[0]?.theme;

              return (
                <div key={requestId} className="mb-8 border rounded-lg p-4">
                  <div onClick={() => toggleExpand(requestId)} className="cursor-pointer flex items-center gap-4">
                    <img src={getRequestPreview()} alt="Request Preview" className="w-20 h-20 object-cover rounded-md" />
                    <h2 className="text-2xl font-bold">{theme || `Request: ${requestId}`}</h2>
                  </div>

                  {isRequestExpanded && (
                    <div className="mt-4 pl-8">
                      {iconTypes.original.length > 0 && (
                        <div className="mb-4">
                          <div onClick={(e) => { e.stopPropagation(); toggleExpand(`${requestId}-original`); }} className="cursor-pointer flex items-center justify-between">
                            <div className="flex items-center gap-4">
                              <img src={iconTypes.original[0].imageUrl} alt="Original Icon Preview" className="w-16 h-16 object-cover rounded-md" />
                              <h3 className="text-xl font-semibold">Original</h3>
                            </div>
                            <button onClick={(e) => { e.stopPropagation(); openExportModal(iconTypes.original); }} className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">Export</button>
                          </div>
                          {expanded.includes(`${requestId}-original`) && (
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4 mt-4 pl-8">
                              {iconTypes.original.map((icon, index) => (
                                <div key={index} className="border rounded-lg p-2">
                                  <img src={icon.imageUrl} alt={icon.description || 'Generated Icon'} className="w-full h-auto object-cover rounded-md" />
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                      {iconTypes.variation.length > 0 && (
                        <div>
                          <div onClick={(e) => { e.stopPropagation(); toggleExpand(`${requestId}-variation`); }} className="cursor-pointer flex items-center justify-between">
                            <div className="flex items-center gap-4">
                              <img src={iconTypes.variation[0].imageUrl} alt="Variation Icon Preview" className="w-16 h-16 object-cover rounded-md" />
                              <h3 className="text-xl font-semibold">Variations</h3>
                            </div>
                            <button onClick={(e) => { e.stopPropagation(); openExportModal(iconTypes.variation); }} className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">Export</button>
                          </div>
                          {expanded.includes(`${requestId}-variation`) && (
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4 mt-4 pl-8">
                              {iconTypes.variation.map((icon, index) => (
                                <div key={index} className="border rounded-lg p-2">
                                  <img src={icon.imageUrl} alt={icon.description || 'Generated Icon'} className="w-full h-auto object-cover rounded-md" />
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmGalleryExport}
        removeBackground={removeBackground}
        setRemoveBackground={setRemoveBackground}
        outputFormat={outputFormat}
        setOutputFormat={setOutputFormat}
        iconCount={iconsToExport.length}
      />
      <ProgressModal show={showProgressModal} progress={exportProgress} />
    </div>
  );
}