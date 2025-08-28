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
    imagenResults?: ServiceResult[];
    bananaResults?: ServiceResult[];
}

export type UIState = 'initial' | 'streaming' | 'error' | 'results';
