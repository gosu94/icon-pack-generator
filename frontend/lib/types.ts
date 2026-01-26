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
  gpt15Results?: ServiceResult[];
  bananaResults?: ServiceResult[];
  trialMode?: boolean;
}

export interface GifAsset {
  id?: number;
  iconId: string;
  fileName: string;
  filePath: string;
  iconType?: string;
  serviceSource?: string;
  gridPosition?: number;
  generationIndex?: number;
}

export interface GifProgressUpdate {
  gifRequestId: string;
  requestId: string;
  totalIcons: number;
  completedIcons: number;
  status: string;
  message: string;
  eventType: string;
  currentIconId?: string;
  gifs?: GifAsset[];
}

export type UIState = "initial" | "streaming" | "error" | "results";

export type GenerationMode =
  | "icons"
  | "illustrations"
  | "mockups"
  | "ui-elements"
  | "labels";
