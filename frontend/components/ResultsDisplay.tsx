import React from "react";
import Image from "next/image";
import { UIState, ServiceResult, GenerationResponse, GenerationMode } from "../lib/types";

interface ResultsDisplayProps {
  mode: GenerationMode;
  uiState: UIState;
  generateVariations: boolean;
  isGenerating: boolean;
  overallProgress: number;
  calculateTimeRemaining: () => string;
  errorMessage: string;
  streamingResults: { [key: string]: ServiceResult };
  showResultsPanes: boolean;
  getIconAnimationClass: (serviceId: string, iconIndex: number) => string;
  animatingIcons: { [key: string]: number };
  exportGeneration: (
    requestId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  currentResponse: GenerationResponse | null;
  moreIconsVisible: { [key: string]: boolean };
  showMoreIconsForm: (uniqueId: string) => void;
  hideMoreIconsForm: (uniqueId: string) => void;
  generateMoreIcons: (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  generateMoreIllustrations: (
    serviceId: string,
    serviceName: string,
    generationIndex: number,
  ) => void;
  moreIconsDescriptions: { [key: string]: string[] };
  setMoreIconsDescriptions: React.Dispatch<
    React.SetStateAction<{ [key: string]: string[] }>
  >;
  getServiceDisplayName: (serviceId: string) => string;
  setIsGenerating: (isGenerating: boolean) => void;
}

const ResultsDisplay: React.FC<ResultsDisplayProps> = ({
  mode,
  uiState,
  generateVariations,
  isGenerating,
  overallProgress,
  calculateTimeRemaining,
  errorMessage,
  streamingResults,
  showResultsPanes,
  getIconAnimationClass,
  animatingIcons,
  exportGeneration,
  currentResponse,
  moreIconsVisible,
  showMoreIconsForm,
  hideMoreIconsForm,
  generateMoreIcons,
  generateMoreIllustrations,
  moreIconsDescriptions,
  setMoreIconsDescriptions,
  getServiceDisplayName,
  setIsGenerating,
}) => {
  const getGenerationResults = (generationNumber: number) => {
    return Object.entries(streamingResults)
      .filter(([, result]) => result.generationIndex === generationNumber)
      .map(([serviceId, result]) => ({ serviceId, ...result }));
  };

  const renderGenerationResults = (generationNumber: number) => {
    const results = getGenerationResults(generationNumber);
    return results.map((result, index) => {
      const baseServiceId = result.serviceId.replace(/-gen\d+$/, "");
      const serviceName = getServiceDisplayName(baseServiceId);

      return (
        <div key={result.serviceId} data-oid="o-woppu">
          {index > 0 && (
            <div className="border-t border-gray-200 my-6" data-oid=".rbhqb3" />
          )}
          <div className="mb-4" data-oid="d3ydm7r">
            <div
              className="flex items-center justify-between"
              data-oid="eo088-:"
            >
              <h3
                className="text-lg font-medium text-gray-900 flex items-center"
                data-oid="wndlt.f"
              >
                {result.status === "success" ? (
                  <span className="mr-2" data-oid="qbnu-fi">
                    ✅
                  </span>
                ) : result.status === "error" ? (
                  <span className="mr-2" data-oid="qbnu-fi">
                    ❌
                  </span>
                ) : (
                  <div className="mr-2 h-4 w-4 animate-spin rounded-full border-b-2 border-gray-900" />
                )}
                {result.status}
                {result.generationTimeMs > 0 && (
                  <span
                    className="text-sm text-gray-500 ml-2"
                    data-oid="q4emod9"
                  >
                    ({(result.generationTimeMs / 1000).toFixed(1)}s)
                  </span>
                )}
              </h3>
              {result.status === "success" && result.icons.length > 0 && (
                <button
                  onClick={() =>
                    exportGeneration(
                      currentResponse?.requestId || "",
                      baseServiceId,
                      result.generationIndex,
                    )
                  }
                  className="px-3 py-1 bg-blue-50 text-blue-600 rounded text-sm hover:bg-blue-100"
                  data-oid="xt-nyai"
                >
                  Export
                </button>
              )}
            </div>
            <p className="text-sm text-gray-600 mt-1" data-oid="6-u8r69">
              {result.message}
            </p>
          </div>

          {showResultsPanes && result.icons && result.icons.length > 0 && (
            <div 
              className={
                mode === "icons"
                  ? "grid gap-4 grid-cols-[repeat(auto-fit,minmax(140px,1fr))]"
                  : "grid grid-cols-1 sm:grid-cols-2 gap-6"
              } 
              data-oid=".ge-1o5"
            >
              {result.icons.map((icon, iconIndex) => (
                <div
                  key={iconIndex}
                  className={`relative group transform ${getIconAnimationClass(result.serviceId, iconIndex)} ${mode === "icons" ? "hover:scale-105 hover:z-20 flex justify-center" : "hover:scale-105 transition-transform duration-200"}`}
                  data-oid="m76b0.p"
                >
                  <div className={mode === "illustrations" ? "aspect-[5/4] w-full" : ""}>
                    <img
                      src={`data:image/png;base64,${icon.base64Data}`}
                      alt={mode === "icons" ? `Generated Icon ${iconIndex + 1}` : `Generated Illustration ${iconIndex + 1}`}
                      className={
                        mode === "icons"
                          ? "w-full h-auto max-w-[128px] rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200"
                          : "w-full h-full object-contain rounded-lg border border-gray-200 shadow-sm hover:shadow-md transition-shadow duration-200"
                      }
                      data-oid="3jhfiim"
                    />
                  </div>
                  <div
                    className={`absolute inset-0 bg-gradient-to-r from-blue-400 to-purple-500 rounded-lg transition-opacity duration-700 ${animatingIcons[result.serviceId] > iconIndex ? "opacity-0" : "opacity-20"}`}
                    data-oid="bit9s0x"
                  />
                </div>
              ))}
            </div>
          )}

          {result.status === "success" && uiState === "results" && (
            <div
              className="mt-6 p-6 bg-white/60 backdrop-blur-lg rounded-2xl shadow-lg border border-purple-200/30"
              data-oid="ovhlhfz"
            >
              <div
                className="flex items-center justify-between mb-4"
                data-oid="uso00lt"
              >
                <h4
                  className="text-base font-semibold text-slate-800"
                  data-oid="d1sbto:"
                >
                  Generate More With Same Style
                </h4>
                {!moreIconsVisible[result.serviceId] && (
                  <button
                    onClick={() => showMoreIconsForm(result.serviceId)}
                    className="px-4 py-2 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-105 shadow-md hover:shadow-lg transition-all duration-200"
                    data-oid="or9y-ww"
                  >
                    <div className="flex items-center justify-center space-x-2">
                      <span>Generate More</span>
                      <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                        <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                        <span>1</span>
                      </span>
                    </div>
                  </button>
                )}
              </div>
              {moreIconsVisible[result.serviceId] && (
                <div className="space-y-4" data-oid="vyc4_1h">
                  <p className="text-xs text-slate-500" data-oid="83178c6">
                    {mode === "icons" 
                      ? "Describe up to 9 new icons (leave empty for creative variations):"
                      : "Describe up to 4 new illustrations (leave empty for creative variations):"}
                  </p>
                  <div className={mode === "icons" ? "grid grid-cols-3 gap-3" : "grid grid-cols-2 gap-3"} data-oid="05:gpsz">
                    {Array.from({ length: mode === "icons" ? 9 : 4 }, (_, i) => (
                      <input
                        key={i}
                        type="text"
                        placeholder={mode === "icons" ? `Icon ${i + 1}` : `Illustration ${i + 1}`}
                        value={
                          moreIconsDescriptions[result.serviceId]?.[i] || ""
                        }
                        onChange={(e) => {
                          const count = mode === "icons" ? 9 : 4;
                          const newDescriptions = [
                            ...(moreIconsDescriptions[result.serviceId] ||
                              new Array(count).fill("")),
                          ];
                          newDescriptions[i] = e.target.value;
                          setMoreIconsDescriptions((prev) => ({
                            ...prev,
                            [result.serviceId]: newDescriptions,
                          }));
                        }}
                        className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition-all duration-200"
                        data-oid="dkp-80."
                      />
                    ))}
                  </div>
                  <div className="flex space-x-3 pt-2" data-oid="5:ovvgt">
                    <button
                      onClick={() => {
                        if (mode === "icons") {
                          generateMoreIcons(
                            baseServiceId,
                            serviceName,
                            result.generationIndex,
                          );
                        } else {
                          generateMoreIllustrations(
                            baseServiceId,
                            serviceName,
                            result.generationIndex,
                          );
                        }
                      }}
                      disabled={isGenerating}
                      className={`w-full py-3 px-5 rounded-xl text-white font-semibold ${isGenerating ? "bg-slate-400 cursor-not-allowed" : "bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-105 shadow-lg hover:shadow-xl"} transition-all duration-200`}
                      data-oid="xku30oy"
                    >
                      {isGenerating ? (
                        <div
                          className="flex items-center justify-center space-x-2"
                          data-oid="wr:qqx5"
                        >
                          <div
                            className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"
                            data-oid="klkpq:y"
                          ></div>
                          <span data-oid="-o8k1u8">Generating...</span>
                        </div>
                      ) : (
                        <div className="flex items-center justify-center space-x-2">
                          <span>{mode === "icons" ? "Generate 9 More Icons" : "Generate 4 More Illustrations"}</span>
                          <span className="flex items-center space-x-1 rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                            <Image src="/images/coin.webp" alt="Coins" width={16} height={16} />
                            <span>1</span>
                          </span>
                        </div>
                      )}
                    </button>
                    <button
                      onClick={() => hideMoreIconsForm(result.serviceId)}
                      className="px-4 py-2 bg-slate-200 text-slate-700 rounded-xl text-sm font-semibold hover:bg-slate-300 transition-colors duration-200"
                      data-oid="rw.qmye"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      );
    });
  };

  return (
    <div className="w-full flex-1 p-4 lg:p-8 flex flex-col" data-oid="zbnho:w">
      {isGenerating && (
        <div className="mb-6" data-oid="dy:7hpm">
          <div
            className="w-full bg-gray-200 rounded-full h-0.5"
            data-oid="q9qnu-0"
          >
            <div
              className="bg-purple-600 h-0.5 rounded-full transition-all duration-300"
              style={{ width: `${overallProgress}%` }}
              data-oid="d-c5u0v"
            />
          </div>
          {/*<p className="text-center text-sm text-gray-600 mt-2" data-oid="2x02tua">*/}
          {/*    {overallProgress < 100*/}
          {/*        ? `Generating icons... Estimated time remaining: ${calculateTimeRemaining()}`*/}
          {/*        : 'Finalizing results...'}*/}
          {/*</p>*/}
        </div>
      )}
      <div className="flex-1 flex flex-col lg:flex-row lg:space-x-8 space-y-8 lg:space-y-0" data-oid=".0me_fy">
        <div
          className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-purple-200/50 flex-1 relative"
          data-oid="pzu54n5"
        >
          <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
          <div
            className="p-8 h-full flex flex-col relative z-10"
            data-oid="pqst9it"
          >
            <div
              className="flex items-center justify-between mb-6"
              data-oid="dv9qzvx"
            >
              <h2
                className="text-2xl font-bold text-slate-900"
                data-oid="rn9b4_h"
              >
                {mode === "icons" ? "Your Icons" : "Your Illustrations"}
              </h2>
            </div>
            <div className="flex-1 overflow-y-auto" data-oid="fr-8:os">
              {uiState === "initial" && (
                <div
                  className="h-full flex items-center justify-center"
                  data-oid="wrg3w6c"
                >
                  <div className="text-center" data-oid="k7r4nph">
                    <svg
                      className="mx-auto h-16 w-16 text-gray-400 mb-4"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      data-oid="zwyv.vw"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1}
                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                        data-oid="jboyff2"
                      />
                    </svg>
                    <p className="text-gray-500" data-oid="gi9rui3">
                      Generated icons will appear here
                    </p>
                  </div>
                </div>
              )}
              {uiState === "error" && (
                <div
                  className="h-full flex items-center justify-center"
                  data-oid="33s1ijk"
                >
                  <div
                    className="bg-red-50 border border-red-200 rounded-lg p-4 max-w-sm"
                    data-oid="2z75dtq"
                  >
                    <div className="flex items-center" data-oid="d6_n28j">
                      <svg
                        className="h-5 w-5 text-red-400 mr-2"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        data-oid="ep8gn1t"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                          data-oid="12on8e0"
                        />
                      </svg>
                      <div data-oid=":de2emg">
                        <h3
                          className="text-sm font-medium text-red-800"
                          data-oid="_vh8b7."
                        >
                          Generation Failed
                        </h3>
                        <p
                          className="text-sm text-red-700 mt-1"
                          data-oid="sk6-y9n"
                        >
                          {errorMessage}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {(uiState === "streaming" || uiState === "results") && (
                <div className="space-y-6" data-oid="g25wa45">
                  {renderGenerationResults(1)}
                </div>
              )}
            </div>
          </div>
        </div>
        {generateVariations && (
          <div
            className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-purple-200/50 flex-1 relative"
            data-oid="mj9c878"
          >
            <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
            <div
              className="p-8 h-full flex flex-col relative z-10"
              data-oid="o17l-tp"
            >
              <div
                className="flex items-center justify-between mb-6"
                data-oid="qd67dxc"
              >
                <h2
                  className="text-2xl font-bold text-slate-900"
                  data-oid="pz2eo.j"
                >
                  Variations
                </h2>
              </div>
              <div className="flex-1 overflow-y-auto" data-oid="ocx--ar">
                {uiState === "initial" && (
                  <div
                    className="h-full flex items-center justify-center"
                    data-oid="22__9nu"
                  >
                    <div className="text-center" data-oid="d0c1s_8">
                      <svg
                        className="mx-auto h-16 w-16 text-gray-400 mb-4"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        data-oid="wo5.-8x"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={1}
                          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                          data-oid="2eg74fx"
                        />
                      </svg>
                      <p className="text-gray-500" data-oid="2c96zzz">
                        Icon variations will appear here
                      </p>
                    </div>
                  </div>
                )}
                {(uiState === "streaming" || uiState === "results") && (
                  <div className="space-y-6" data-oid=":qzs.na">
                    {renderGenerationResults(2)}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ResultsDisplay;
