export interface Icon {
  base64Data: string;
  description?: string;
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
  bananaResults?: ServiceResult[];
  letterGroups?: LetterGroup[];
}

export type UIState = "initial" | "streaming" | "error" | "results";

export interface LetterIcon {
  letter: string;
  base64Data: string;
  sequence: number;
}

export interface LetterGroup {
  name: string;
  icons: LetterIcon[];
  originalGridImageBase64?: string;
}

export interface LetterPackResponse {
  status: string;
  message: string;
  requestId: string;
  groups: LetterGroup[];
}

export type GenerationMode = "icons" | "illustrations" | "mockups" | "letters";
