"use client";

import { useEffect, useState } from "react";
import { GenerationMode } from "@/lib/types";
import Navigation from "../../components/Navigation";
import GeneratorForm from "../../components/GeneratorForm";
import ResultsDisplay from "../../components/ResultsDisplay";
import ExportModal from "../../components/ExportModal";
import ProgressModal from "../../components/ProgressModal";
import FeedbackWidget from "../../components/FeedbackWidget";
import { useAuth } from "@/context/AuthContext";
import { useIconAnimations } from "./hooks/useIconAnimations";
import { useDashboardFormState } from "./hooks/useDashboardFormState";
import { useGenerationFlow } from "./hooks/useGenerationFlow";
import { useExportFlow } from "./hooks/useExportFlow";

export default function Page() {
  const { authState, checkAuthenticationStatus } = useAuth();
  const [mode, setMode] = useState<GenerationMode>("icons");
  const [gifRefreshToken, setGifRefreshToken] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setGifRefreshToken((token) => (token + 1) % 100000);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  const iconAnimations = useIconAnimations();
  const formState = useDashboardFormState({ mode });
  const generation = useGenerationFlow({
    mode,
    setMode,
    authState,
    checkAuthenticationStatus,
    formState,
    iconAnimations,
  });
  const exportFlow = useExportFlow({
    mode,
    setUiState: generation.setUiState,
    setErrorMessage: generation.setErrorMessage,
  });

  const {
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
    formatFileSize,
    enhancePrompt,
    setEnhancePrompt,
  } = formState;

  const {
    uiState,
    errorMessage,
    isGenerating,
    setIsGenerating,
    currentResponse,
    isTrialResult,
    streamingResults,
    showResultsPanes,
    overallProgress,
    calculateTimeRemaining,
    generateIcons,
    generateMoreIcons,
    generateMoreIllustrations,
    moreIconsVisible,
    showMoreIconsForm,
    hideMoreIconsForm,
    moreIconsDescriptions,
    setMoreIconsDescriptions,
    getServiceDisplayName,
    animatingIcons,
    getIconAnimationClass,
  } = generation;

  const {
    showExportModal,
    setShowExportModal,
    exportContext,
    exportGeneration,
    confirmExport,
    showProgressModal,
    exportProgress,
  } = exportFlow;

  const modalIconCount =
    exportContext && streamingResults
      ? streamingResults[
          `${exportContext.serviceName}-gen${exportContext.generationIndex}`
        ]?.icons?.length || 0
      : 0;

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation useLoginPage={true} />
      <div className="flex flex-col lg:flex-row lg:h-screen">
        <GeneratorForm
          mode={mode}
          setMode={setMode}
          inputType={inputType}
          setInputType={setInputType}
          labelText={labelText}
          setLabelText={setLabelText}
          generateVariations={generateVariations}
          setGenerateVariations={setGenerateVariations}
          generalDescription={generalDescription}
          setGeneralDescription={setGeneralDescription}
          individualDescriptions={individualDescriptions}
          setIndividualDescriptions={setIndividualDescriptions}
          referenceImage={referenceImage}
          imagePreview={imagePreview}
          isGenerating={isGenerating}
          generateIcons={generateIcons}
          handleImageSelect={handleImageSelect}
          removeImage={removeImage}
          fileInputRef={fileInputRef}
          formatFileSize={formatFileSize}
          enhancePrompt={enhancePrompt}
          setEnhancePrompt={setEnhancePrompt}
        />

        <ResultsDisplay
          mode={mode}
          uiState={uiState}
          generateVariations={generateVariations}
          isGenerating={isGenerating}
          overallProgress={overallProgress}
          calculateTimeRemaining={calculateTimeRemaining}
          errorMessage={errorMessage}
          streamingResults={streamingResults}
          showResultsPanes={showResultsPanes}
          getIconAnimationClass={getIconAnimationClass}
          animatingIcons={animatingIcons}
          exportGeneration={exportGeneration}
          currentResponse={currentResponse}
          isTrialResult={isTrialResult}
          moreIconsVisible={moreIconsVisible}
          showMoreIconsForm={showMoreIconsForm}
          hideMoreIconsForm={hideMoreIconsForm}
          generateMoreIcons={generateMoreIcons}
          generateMoreIllustrations={generateMoreIllustrations}
          moreIconsDescriptions={moreIconsDescriptions}
          setMoreIconsDescriptions={setMoreIconsDescriptions}
          getServiceDisplayName={getServiceDisplayName}
          setIsGenerating={setIsGenerating}
          setMode={setMode}
          setInputType={setInputType}
          setReferenceImage={setReferenceImage}
          setImagePreview={setImagePreview}
          availableCoins={authState?.user?.coins ?? 0}
          trialCoins={authState?.user?.trialCoins ?? 0}
          gifRefreshToken={gifRefreshToken}
        />
      </div>
      <ExportModal
        show={showExportModal}
        onClose={() => setShowExportModal(false)}
        onConfirm={confirmExport}
        iconCount={modalIconCount}
        mode={mode}
      />

      <ProgressModal show={showProgressModal} progress={exportProgress} />
      <FeedbackWidget />
    </div>
  );
}
