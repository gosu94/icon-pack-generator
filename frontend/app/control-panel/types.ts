export interface UserAdminData {
  id: number;
  email: string;
  lastLogin: string | null;
  coins: number;
  trialCoins: number;
  generatedIconsCount: number;
  generatedIllustrationsCount: number;
  generatedMockupsCount: number;
  registeredAt: string;
  authProvider: string;
  isActive: boolean;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface UserIcon {
  id: number;
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
  iconType: string;
  theme: string;
}

export interface UserIllustration {
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
}

export interface UserMockup {
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
}
