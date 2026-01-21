export interface UserAdminData {
  id: number;
  email: string;
  lastLogin: string | null;
  coins: number;
  trialCoins: number;
  generatedIconsCount: number;
  generatedIllustrationsCount: number;
  generatedMockupsCount: number;
  generatedLabelsCount: number;
  registeredAt: string;
  authProvider: string;
  isActive: boolean;
  isCustomer: boolean;
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

export interface UserLabel {
  imageUrl: string;
  labelText: string;
  serviceSource: string;
  requestId: string;
  labelType: string;
  theme: string;
}

export interface GenerationInProgress {
  requestId: string;
  type: string;
  startedAt: string;
}

export interface GenerationStatus {
  inProgress: boolean;
  activeCount: number;
  activeGenerations: GenerationInProgress[];
}

export type StatsRange = "week" | "month" | "quarter" | "all";

export interface DailyStat {
  date: string;
  count: number;
}

export interface ActivityStats {
  range: StatsRange;
  registrations: DailyStat[];
  icons: DailyStat[];
  totalRegistrations: number;
  totalIcons: number;
}
