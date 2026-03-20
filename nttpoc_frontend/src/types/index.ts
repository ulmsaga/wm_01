import type { ComponentType } from 'react';

export interface User {
  userSeq: number;
  userId: string;
  userName: string;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  code?: string;
  message?: string;
}

export interface NavItem {
  label: string;
  icon?: ComponentType<{ className?: string }>;
  path?: string;
  children?: NavItem[];
}
