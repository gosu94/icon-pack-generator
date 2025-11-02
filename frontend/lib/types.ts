export interface Icon {
  id?: string;
  base64Data: string;
  description?: string;
  gridPosition?: number;
  serviceSource?: string;
}

export interface Label {
  id: number;
  imageUrl: string;
  labelText: string;
  requestId: string;
  labelType: string;
  serviceSource: string;
  theme: string;
}

export interface ServiceResult {
  icons: Icon[];
  components?: Icon[];
  originalGridImageBase64?: string;
  generationTimeMs: number;
  status: string;
  message: string;
  generationIndex: number;
  seed?: string;
}

export interface StreamingResults {
  [key: string]: ServiceResult;
}

export interface GenerationResponse {
  icons: Icon[];
  requestId: string;
  falAiResults?: ServiceResult[];
  recraftResults?: ServiceResult[];
  photonResults?: ServiceResult[];
  gptResults?: ServiceResult[];
  bananaResults?: ServiceResult[];
}

export type UIState = "initial" | "streaming" | "error" | "results";

export type GenerationMode = "icons" | "illustrations" | "mockups" | "labels";
